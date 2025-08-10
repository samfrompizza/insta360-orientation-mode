package com.arashivision.sdk.demo.ui.main

import com.arashivision.sdk.demo.base.BaseEvent

interface MainEvent : BaseEvent {

    object PermissionGrantedEvent : MainEvent

    object PermissionDeniedEvent : MainEvent
}
