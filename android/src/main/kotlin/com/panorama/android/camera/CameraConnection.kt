package com.panorama.android.camera

import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.callback.ICameraChangedCallback
import com.arashivision.sdkcamera.camera.callback.ICaptureStatusListener
import com.arashivision.sdkcamera.camera.callback.IPreviewStatusListener
import com.arashivision.sdkcamera.camera.model.CaptureMode
import com.arashivision.sdkcamera.camera.model.ISO
import com.arashivision.sdkcamera.camera.model.Shutter
import com.arashivision.sdkcamera.camera.model.EV
import com.arashivision.sdkcamera.camera.model.WB
import com.arashivision.sdkcamera.camera.model.RecordResolution
import com.arashivision.sdkcamera.camera.model.PhotoResolution
import com.arashivision.sdkcamera.camera.resolution.PreviewStreamResolution
import com.arashivision.sdkcamera.camera.callback.ICaptureSupportConfigCallback
import com.arashivision.sdkcamera.camera.callback.ICameraOperateCallback
import com.arashivision.sdkcamera.camera.model.SensorMode
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Connection lifecycle of the Insta360 camera, derived from the SDK callbacks. */
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, STREAMING, ERROR }

/** Whether a capture/record is in progress. */
enum class CaptureState { IDLE, BUSY, RECORDING }

/** The connect transports the SDK supports. */
enum class ConnectTransport(val sdkType: Int) {
    WIFI(InstaCameraManager.CONNECT_TYPE_WIFI),
    USB(InstaCameraManager.CONNECT_TYPE_USB),
    BLE(InstaCameraManager.CONNECT_TYPE_BLE),
}

/** Thin wrapper over [InstaCameraManager] that exposes a single [state] flow and open/close/preview
 *  actions, hiding all SDK callback wiring from the rest of the app. The SDK renders the preview
 *  into an [com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView] separately; this class
 *  only manages connection + preview-stream lifecycle and reports state. */
class CameraConnection(
    private val appContext: Context,
    private val manager: InstaCameraManager = InstaCameraManager.getInstance(),
) {
    private companion object {
        const val TAG = "CameraConnection"
        // Explicit panoramic (2:1) preview resolution used when the camera reports no supported
        // list; a direct protocol probe got frames at this size on the ONE RS 1-Inch 360 mod.
        val PREVIEW_FALLBACK_RESOLUTION = PreviewStreamResolution.STREAM_3840_1920_30FPS
    }

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _captureState = MutableStateFlow(CaptureState.IDLE)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _lastCapturedPaths = MutableStateFlow<List<String>>(emptyList())
    val lastCapturedPaths: StateFlow<List<String>> = _lastCapturedPaths.asStateFlow()

    private var recording = false

    private val captureListener = object : ICaptureStatusListener {
        override fun onCaptureStarting() { _captureState.value = CaptureState.BUSY }
        override fun onCaptureWorking() {
            _captureState.value = if (recording) CaptureState.RECORDING else CaptureState.BUSY
        }
        override fun onCaptureStopping() { _captureState.value = CaptureState.BUSY }
        override fun onCaptureFinish(paths: Array<String>?) {
            recording = false
            _lastCapturedPaths.value = paths?.toList() ?: emptyList()
            _captureState.value = CaptureState.IDLE
        }
        override fun onCaptureError(code: Int) {
            recording = false
            _captureState.value = CaptureState.IDLE
        }
    }

    private val cameraCallback = object : ICameraChangedCallback {
        override fun onCameraStatusChanged(enabled: Boolean, connectType: Int) {
            Log.i(TAG, "onCameraStatusChanged enabled=$enabled connectType=$connectType")
            _state.value = if (enabled) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
        }
        override fun onCameraConnectError(errorCode: Int) {
            Log.e(TAG, "onCameraConnectError errorCode=$errorCode")
            _state.value = ConnectionState.ERROR
        }
    }

    private val previewListener = object : IPreviewStatusListener {
        override fun onOpening() { Log.i(TAG, "preview onOpening"); _state.value = ConnectionState.CONNECTING }
        override fun onOpened() {
            Log.i(TAG, "preview onOpened")
            // Resolve the stream encoder now that the preview stream is open. The legacy demo did
            // this in its open-stream callback; calling it before the stream exists crashes the SDK
            // (StartStreamingParam is null). This is what lets the SDK read the preview codec.
            runCatching { manager.setStreamEncode() }
                .onFailure { Log.e(TAG, "setStreamEncode failed: $it") }
            Log.i(TAG, "after setStreamEncode h265=${manager.isH265StreamEncode()} encodeType=${manager.videoEncodeType}")
            _state.value = ConnectionState.STREAMING
        }
        override fun onIdle() { Log.i(TAG, "preview onIdle"); _state.value = ConnectionState.CONNECTED }
        override fun onError() { Log.e(TAG, "preview onError"); _state.value = ConnectionState.ERROR }
    }

    /** Register callbacks. Call once when the live screen starts. */
    fun register() {
        Log.i(TAG, "register: already connected=${manager.cameraConnectedType}")
        manager.registerCameraChangedCallback(cameraCallback)
        manager.setPreviewStatusChangedListener(previewListener)
        manager.setCaptureStatusListener(captureListener)
    }

    /** Open a connection over the given transport. State advances via the camera callback. */
    fun connect(transport: ConnectTransport) {
        Log.i(TAG, "connect transport=$transport sdkType=${transport.sdkType}")
        _state.value = ConnectionState.CONNECTING
        manager.openCamera(transport.sdkType)
    }

    /** Ensure the camera is in panorama sensor mode before preview; the legacy demo did this and a
     *  non-panorama mode can leave the preview formats/codec unresolved. No-op if already panorama. */
    suspend fun ensurePanoramaMode(): Boolean {
        if (manager.currentSensorMode == SensorMode.PANORAMA) {
            Log.i(TAG, "ensurePanoramaMode: already panorama")
            return true
        }
        Log.i(TAG, "ensurePanoramaMode: switching from ${manager.currentSensorMode}")
        return suspendCancellableCoroutine { cont ->
            manager.switchPanoramaSensorMode(object : ICameraOperateCallback {
                override fun onSuccessful() { if (cont.isActive) cont.resume(true) }
                override fun onFailed() { Log.e(TAG, "switchPanorama onFailed"); if (cont.isActive) cont.resume(false) }
                override fun onCameraConnectError() { Log.e(TAG, "switchPanorama connectError"); if (cont.isActive) cont.resume(false) }
            })
        }
    }

    /** Fetch the camera's capability/config over HTTP, which is what tells the SDK the preview
     *  formats and codec. The HTTP request must go over the camera's own Wi-Fi network, so the
     *  process is temporarily bound to it (the camera AP has no internet, so Android otherwise
     *  routes the request to the default network and the config never arrives — leaving the codec
     *  unresolved and the preview black). Mirrors the legacy demo's initSupportConfig(). Call once
     *  after CONNECTED, before startPreview. Returns true on success. */
    suspend fun initSupportConfig(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cameraNetwork(cm)
        if (net == null) {
            Log.e(TAG, "initSupportConfig: camera network not found")
            return false
        }
        cm.bindProcessToNetwork(net)
        return try {
            suspendCancellableCoroutine { cont ->
                manager.initCameraSupportConfig(object : ICaptureSupportConfigCallback {
                    override fun onComplete() {
                        Log.i(TAG, "initSupportConfig onComplete")
                        if (cont.isActive) cont.resume(true)
                    }
                    override fun onFailed(msg: String?) {
                        Log.e(TAG, "initSupportConfig onFailed: $msg")
                        if (cont.isActive) cont.resume(false)
                    }
                })
            }
        } finally {
            cm.bindProcessToNetwork(null)
        }
    }

    /** Find the [Network] that is the camera's Wi-Fi AP: the connected Wi-Fi network whose IP
     *  matches the active Wi-Fi connection. Ported from the legacy demo's NetworkManager. */
    private fun cameraNetwork(cm: ConnectivityManager): Network? {
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val activeIp = wifi.connectionInfo.ipAddress
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            val info = caps.transportInfo
            if (info is WifiInfo && info.ipAddress == activeIp) return network
        }
        return null
    }

    /** Begin the live preview stream (after CONNECTED). The ONE RS 1-Inch 360 mod returns an empty
     *  supported-resolution list and the default normal stream leaves the SDK unable to resolve the
     *  codec (main_video_encode_type error), so no frames decode. A direct protocol probe confirmed
     *  the camera does deliver frames when asked with an explicit resolution, so we start with the
     *  first one the camera reports and, when that list is empty, fall back to an explicit panoramic
     *  resolution (2:1) instead of the default. */
    fun startPreview() {
        val supported = manager.getSupportedPreviewStreamResolution(InstaCameraManager.PREVIEW_TYPE_NORMAL)
        Log.i(TAG, "startPreview supported=$supported h265=${manager.isH265StreamEncode()}")
        val res = supported.firstOrNull() ?: PREVIEW_FALLBACK_RESOLUTION
        Log.i(TAG, "startPreview using=$res")
        manager.startPreviewStream(res, InstaCameraManager.PREVIEW_TYPE_NORMAL)
    }

    /** Bind the player's render pipeline to the camera so the preview stream is fed into it (or
     *  unbind with null). Without this the player decodes nothing and the preview stays black; this
     *  is the step the legacy demo performed in the player's onLoadingFinish callback. */
    fun setPipeline(pipeline: Any?) {
        Log.i(TAG, "setPipeline ${if (pipeline != null) "bind" else "unbind"}")
        manager.setPipeline(pipeline)
    }

    /** Whether the camera has a usable SD card (recording requires it). */
    fun isSdCardEnabled(): Boolean = manager.isSdCardEnabled

    /** Take a single photo (normal mode). */
    fun capturePhoto() {
        recording = false
        manager.startNormalCapture()
    }

    /** Start a normal video recording. Caller must check [isSdCardEnabled] first. */
    fun startRecord() {
        recording = true
        manager.startNormalRecord()
    }

    /** Stop the current recording. */
    fun stopRecord() {
        manager.stopNormalRecord()
    }

    /** Read a setting's current value + options. RECORD_RESOLUTION always reads the video mode and
     *  PHOTO_RESOLUTION the photo mode; the rest read against the active mode chosen by [photo]. */
    fun readSetting(setting: CameraSetting, photo: Boolean): SettingState {
        val mode = if (photo) CaptureMode.CAPTURE_NORMAL else CaptureMode.RECORD_NORMAL
        fun opt(e: Enum<*>?) = e?.let { SettingOption(it.name, it.name) }
        fun opts(list: List<Enum<*>>) = list.map { SettingOption(it.name, it.name) }
        return when (setting) {
            CameraSetting.ISO ->
                SettingState(setting, opt(manager.getISO(mode)), opts(manager.getSupportISOList(mode)))
            CameraSetting.SHUTTER ->
                SettingState(setting, opt(manager.getShutter(mode)), opts(manager.getSupportShutterList(mode)))
            // EV and WB are plain SDK classes (constant fields), not Kotlin/Java enums, so we identify
            // each value by the name of its declaring static field via [constantOpt]/[constantOpts].
            CameraSetting.EV ->
                SettingState(setting, constantOpt(manager.getEv(mode)), constantOpts(manager.getSupportEVList(mode)))
            CameraSetting.WB ->
                SettingState(setting, constantOpt(manager.getWB(mode)), constantOpts(manager.getSupportWBList(mode)))
            CameraSetting.RECORD_RESOLUTION ->
                SettingState(setting, opt(manager.getRecordResolution(CaptureMode.RECORD_NORMAL)),
                    opts(manager.getSupportRecordResolutionList(CaptureMode.RECORD_NORMAL)))
            CameraSetting.PHOTO_RESOLUTION ->
                SettingState(setting, opt(manager.getPhotoResolution(CaptureMode.CAPTURE_NORMAL)),
                    opts(manager.getSupportPhotoResolutionList(CaptureMode.CAPTURE_NORMAL)))
        }
    }

    /** Apply a setting value (token = SDK enum/constant name) for the active mode. STREAMING only. */
    fun applySetting(setting: CameraSetting, token: String, photo: Boolean) {
        val mode = if (photo) CaptureMode.CAPTURE_NORMAL else CaptureMode.RECORD_NORMAL
        when (setting) {
            CameraSetting.ISO -> manager.setISO(mode, enumValueOf<ISO>(token))
            CameraSetting.SHUTTER -> manager.setShutter(mode, enumValueOf<Shutter>(token))
            CameraSetting.EV -> manager.setEv(mode, constantValueOf(EV::class.java, token))
            CameraSetting.WB -> manager.setWB(mode, constantValueOf(WB::class.java, token))
            CameraSetting.RECORD_RESOLUTION ->
                manager.setRecordResolution(CaptureMode.RECORD_NORMAL, enumValueOf<RecordResolution>(token))
            CameraSetting.PHOTO_RESOLUTION ->
                manager.setPhotoResolution(CaptureMode.CAPTURE_NORMAL, enumValueOf<PhotoResolution>(token))
        }
    }

    /** Name of the public static field on [value]'s class that holds [value] (its constant name),
     *  for SDK model classes that are not enums (EV, WB). Returns null if no such field is found. */
    private fun constantName(value: Any): String? =
        value.javaClass.fields.firstOrNull { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.get(null) === value }?.name

    private fun <T : Any> constantOpt(value: T?): SettingOption? =
        value?.let { constantName(it)?.let { name -> SettingOption(name, name) } }

    private fun <T : Any> constantOpts(list: List<T>): List<SettingOption> =
        list.mapNotNull { constantOpt(it) }

    /** Resolve a constant by its static field [name] on the non-enum SDK class [type]. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> constantValueOf(type: Class<T>, name: String): T =
        type.getField(name).get(null) as T

    /** Stop preview + close the camera + unregister. Call from the screen's teardown. */
    fun disconnect() {
        manager.closePreviewStream()
        manager.setPreviewStatusChangedListener(null)
        manager.unregisterCameraChangedCallback(cameraCallback)
        manager.setCaptureStatusListener(null)
        manager.closeCamera()
        _state.value = ConnectionState.DISCONNECTED
    }
}
