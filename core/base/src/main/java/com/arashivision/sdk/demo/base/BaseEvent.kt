package com.arashivision.sdk.demo.base

interface BaseEvent {
    data object CameraBatteryLowEvent : BaseEvent

    data class CameraSDCardStateChangedEvent(
        val enabled: Boolean,
    ) : BaseEvent

    data class CameraBatteryUpdateEvent(
        val batteryLevel: Int,
        val isCharging: Boolean,
    ) : BaseEvent

    data class CameraStorageChangedEvent(
        val freeSpace: Long,
        val totalSpace: Long,
    ) : BaseEvent

    data class CameraStatusChangedEvent(
        val enabled: Boolean,
        val connectType: Int,
    ) : BaseEvent
}
