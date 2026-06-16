package com.panorama.android.camera

import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.callback.ICameraChangedCallback
import com.arashivision.sdkcamera.camera.callback.ICaptureStatusListener
import com.arashivision.sdkcamera.camera.callback.IPreviewStatusListener
import com.arashivision.sdkcamera.camera.model.CaptureMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private val manager: InstaCameraManager = InstaCameraManager.getInstance(),
) {
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
            _state.value = if (enabled) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
        }
        override fun onCameraConnectError(errorCode: Int) {
            _state.value = ConnectionState.ERROR
        }
    }

    private val previewListener = object : IPreviewStatusListener {
        override fun onOpening() { _state.value = ConnectionState.CONNECTING }
        override fun onOpened() { _state.value = ConnectionState.STREAMING }
        override fun onIdle() { _state.value = ConnectionState.CONNECTED }
        override fun onError() { _state.value = ConnectionState.ERROR }
    }

    /** Register callbacks. Call once when the live screen starts. */
    fun register() {
        manager.registerCameraChangedCallback(cameraCallback)
        manager.setPreviewStatusChangedListener(previewListener)
        manager.setCaptureStatusListener(captureListener)
    }

    /** Open a connection over the given transport. State advances via the camera callback. */
    fun connect(transport: ConnectTransport) {
        _state.value = ConnectionState.CONNECTING
        manager.openCamera(transport.sdkType)
    }

    /** Begin the live preview stream (after CONNECTED). */
    fun startPreview() {
        manager.startPreviewStream(InstaCameraManager.PREVIEW_TYPE_NORMAL)
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
