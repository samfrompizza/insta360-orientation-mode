package com.arashivision.sdk.demo.ui.ability

import com.arashivision.sdk.demo.base.BaseEvent
import com.arashivision.sdk.demo.base.EventStatus

interface AbilityEvent : BaseEvent {

    class FormatSdCardEvent(var status: EventStatus) : AbilityEvent
    class ActiveCameraEvent(var status: EventStatus, var error: String = "") : AbilityEvent

}
