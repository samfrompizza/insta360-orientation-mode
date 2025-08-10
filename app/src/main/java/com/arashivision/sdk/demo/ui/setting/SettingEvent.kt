package com.arashivision.sdk.demo.ui.setting

import com.arashivision.sdk.demo.base.BaseEvent
import com.arashivision.sdk.demo.base.EventStatus
import java.io.File

interface SettingEvent : BaseEvent {

    class ExportLogEvent(var status: EventStatus, var zipFile: File? = null) : SettingEvent

}
