package com.panorama.app.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panorama.android.camera.CameraConnection
import com.panorama.android.camera.ConnectTransport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Drives the live screen: mirrors [CameraConnection.state] into [state] and forwards connect /
 *  disconnect / preview actions. The endless mirror collector runs in [backgroundScope] (default
 *  [viewModelScope]) so a test can hand in runTest's auto-cancelled scope. */
@HiltViewModel
class LiveViewModel(
    private val connection: CameraConnection,
    private val orientationEngine: com.panorama.android.sensor.OrientationEngine,
    backgroundScope: CoroutineScope? = null,
) : ViewModel() {

    @Inject
    constructor(
        connection: CameraConnection,
        orientationEngine: com.panorama.android.sensor.OrientationEngine,
    ) : this(connection, orientationEngine, backgroundScope = null)

    private val _state = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    init {
        val scope = backgroundScope ?: viewModelScope
        scope.launch {
            connection.state.collect { c -> _state.update { it.copy(connection = c) } }
        }
        scope.launch {
            connection.captureState.collect { c -> _state.update { it.copy(capture = c) } }
        }
    }

    fun register() = connection.register()
    fun connect(transport: ConnectTransport) = connection.connect(transport)
    fun setPipeline(pipeline: Any?) = connection.setPipeline(pipeline)
    fun disconnect() = connection.disconnect()

    /** After CONNECTED: fetch the camera's support config (over its Wi-Fi) so the SDK knows the
     *  preview codec, then start the preview stream, then run [onStreamStarted] (the player
     *  prepare/play). Without the config step the codec stays unresolved and the preview is black.
     *  [onStreamStarted] runs on the main thread. Runs in [viewModelScope]. */
    fun preparePreview(onStreamStarted: () -> Unit) {
        viewModelScope.launch {
            connection.ensurePanoramaMode()
            connection.initSupportConfig()
            connection.startPreview()
            onStreamStarted()
        }
    }

    fun startSensor() = orientationEngine.start()
    fun stopSensor() = orientationEngine.stop()
    fun currentGaze() = orientationEngine.currentGaze()

    fun setPhotoMode(photo: Boolean) = _state.update { it.copy(photoMode = photo, sdMissing = false) }

    fun onShutter() {
        if (_state.value.photoMode) {
            connection.capturePhoto()
            return
        }
        when (_state.value.capture) {
            com.panorama.android.camera.CaptureState.RECORDING -> connection.stopRecord()
            else ->
                if (!connection.isSdCardEnabled()) _state.update { it.copy(sdMissing = true) }
                else connection.startRecord()
        }
    }

    fun settingsFor(): List<com.panorama.android.camera.SettingState> =
        com.panorama.android.camera.CameraSetting.entries.map {
            connection.readSetting(it, _state.value.photoMode)
        }

    fun applySetting(setting: com.panorama.android.camera.CameraSetting, token: String) =
        connection.applySetting(setting, token, _state.value.photoMode)
}
