package com.panorama.android.camera

import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.callback.ICameraChangedCallback
import com.arashivision.sdkcamera.camera.callback.IPreviewStatusListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Connection lifecycle of the Insta360 camera, derived from the SDK callbacks. */
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, STREAMING, ERROR }

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

    /** Stop preview + close the camera + unregister. Call from the screen's teardown. */
    fun disconnect() {
        manager.closePreviewStream()
        manager.setPreviewStatusChangedListener(null)
        manager.unregisterCameraChangedCallback(cameraCallback)
        manager.closeCamera()
        _state.value = ConnectionState.DISCONNECTED
    }
}
