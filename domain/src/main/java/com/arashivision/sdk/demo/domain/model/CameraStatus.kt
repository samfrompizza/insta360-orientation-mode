package com.arashivision.sdk.demo.domain.model

data class CameraStatus(
    val isConnected: Boolean,
    val isRecording: Boolean,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val sdCardInserted: Boolean,
    val freeSpaceMB: Long,
    val totalSpaceMB: Long,
)
