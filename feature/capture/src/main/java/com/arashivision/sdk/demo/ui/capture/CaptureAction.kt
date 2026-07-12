package com.arashivision.sdk.demo.ui.capture

import com.arashivision.insta360.basecamera.camera.setting.StreamResolution
import com.arashivision.insta360.basemedia.asset.WindowCropInfo
import com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView

sealed interface CaptureAction {
    data object InitCapture : CaptureAction

    data class SwitchCaptureMode(val position: Int) : CaptureAction

    data object ToggleCapture : CaptureAction

    data object ToggleVrMode : CaptureAction

    data object RecenterGyro : CaptureAction

    data object CameraPreviewStreamParamsChanged : CaptureAction

    data class UpdatePreviewParams(
        val playerView: InstaCapturePlayerView,
    ) : CaptureAction

    data class RestartPlayer(val playerView: InstaCapturePlayerView? = null) : CaptureAction

    data class ShowCaptureSettings(val settingIndex: Int = -1) : CaptureAction

    data object HideCaptureSettings : CaptureAction

    data class SetCaptureSetting(
        val setting: com.arashivision.sdkcamera.camera.model.CaptureSetting,
        val value: Any,
    ) : CaptureAction

    data class UpdatePlayerViewParams(
        val windowCropInfo: WindowCropInfo?,
        val offsetData: String?,
        val stabOffset: String?,
        val resolution: StreamResolution?,
    ) : CaptureAction

    data class ShowLoading(
        val messageResId: Int? = null,
        val message: String? = null,
    ) : CaptureAction

    data object HideLoading : CaptureAction

    data class ShowToast(
        val messageResId: Int? = null,
        val message: String? = null,
        val longTime: Boolean = false,
    ) : CaptureAction

    data object CloseScreen : CaptureAction
}
