package com.panorama.app.live

import com.panorama.android.camera.ConnectionState

/** Slow UI snapshot for the live screen. */
data class LiveUiState(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
)
