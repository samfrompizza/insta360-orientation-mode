package com.arashivision.sdk.demo.base

interface BaseEvent {

    object CameraBatteryLowEvent : BaseEvent

    class CameraSDCardStateChangedEvent(var enabled: Boolean) : BaseEvent

    class CameraBatteryUpdateEvent(var batteryLevel: Int, var isCharging: Boolean) : BaseEvent

    class CameraStorageChangedEvent(var freeSpace: Long, var totalSpace: Long) : BaseEvent

    class CameraStatusChangedEvent(var enable: Boolean, var connectType: Int) : BaseEvent
}

enum class EventStatus {
    START,
    SUCCESS,
    PROGRESS,
    FAILED
}