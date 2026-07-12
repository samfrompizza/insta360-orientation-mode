package com.arashivision.sdk.demo.ui.capture

import com.arashivision.sdkcamera.camera.model.CaptureMode

data class CaptureUiState(
    val isInitializing: Boolean = false,
    val initStep: CaptureEvent.InitStep? = null,
    val isInitFailed: Boolean = false,
    val currentCaptureMode: CaptureMode? = null,
    val captureModeList: List<CaptureMode> = emptyList(),
    val isRecording: Boolean = false,
    val isLiveStreaming: Boolean = false,
    val isCapturing: Boolean = false,
    val recordTimeMs: Long = 0L,
    val videoTimeMs: Long = 0L,
    val captureCount: Int = 0,
    val showCaptureCount: Boolean = false,
    val showRecordTime: Boolean = false,
    val showVideoDuration: Boolean = false,
    val errorMessage: String? = null,
    val isCaptureButtonVisible: Boolean = true,
    val showRenderingLoading: Boolean = false,
    val needRestartPlayer: Boolean = false,
    val sdcardMissing: Boolean = false,
)
