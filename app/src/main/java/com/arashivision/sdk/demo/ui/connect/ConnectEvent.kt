package com.arashivision.sdk.demo.ui.connect

import com.arashivision.sdk.demo.base.BaseEvent
import com.arashivision.sdk.demo.base.EventStatus
import com.clj.fastble.data.BleDevice

interface ConnectEvent : BaseEvent {

    data class ScanDeviceEvent(
        var status: EventStatus,
        var bleDeviceList: List<BleDevice> = emptyList(),
        var bleDevice: BleDevice? = null
    ) : ConnectEvent

    class ConnectDeviceEvent(
        var status: EventStatus,
        var connectType: Int,
        var errorCode: Int? = 0
    ) : ConnectEvent


    object CameraDisconnectedEvent : ConnectEvent

    data class RefreshMediaTimeEvent(var status: EventStatus, var mediaTime: Long = 0) : ConnectEvent
}
