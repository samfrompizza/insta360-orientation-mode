package com.panorama.app.live

import com.panorama.android.camera.CaptureState
import com.panorama.android.camera.ConnectionState

/** Slow UI snapshot for the live screen. */
data class LiveUiState(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val capture: CaptureState = CaptureState.IDLE,
    val photoMode: Boolean = true,
    val sdMissing: Boolean = false,
)
