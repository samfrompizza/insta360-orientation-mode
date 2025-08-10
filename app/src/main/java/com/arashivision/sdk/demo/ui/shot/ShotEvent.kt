package com.arashivision.sdk.demo.ui.shot

import com.arashivision.sdk.demo.base.BaseEvent
import com.arashivision.sdk.demo.base.EventStatus

open class ShotEvent : BaseEvent {

    class SwitchPanoramaSensorModeEvent(var status: EventStatus) : ShotEvent()
    object SDCardDisableEvent : ShotEvent()
    class CaptureTimeChangedEvent(var time: Long) : ShotEvent()
    object CaptureStartingEvent : ShotEvent()
    object CaptureStoppingEvent : ShotEvent()
    class CaptureErrorEvent(var code: Int) : ShotEvent()
    class CaptureWorkingEvent(var isSingleClickAction: Boolean) : ShotEvent()
    class CaptureCountChangedEvent(var count: Int) : ShotEvent()
    class CaptureFinishEvent(var paths: List<String>) : ShotEvent()

}