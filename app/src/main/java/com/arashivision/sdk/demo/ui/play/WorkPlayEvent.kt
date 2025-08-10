package com.arashivision.sdk.demo.ui.play

import com.arashivision.sdk.demo.base.BaseEvent
import com.arashivision.sdk.demo.base.EventStatus


open class WorkPlayEvent : BaseEvent {

    enum class ExportStatus {
        START,
        PROGRESS,
        SUCCESS,
        FAILED,
        CANCEL
    }
    class ExportMediaEvent(
        var exportStatus: ExportStatus,
        var exportPath: String = "",
        var progress: Float = 0f,
        var error: String = ""
    ) : WorkPlayEvent()

    class HDRStitchEvent(var status: EventStatus) : WorkPlayEvent()
    class PureShotStitchEvent(var status: EventStatus) : WorkPlayEvent()

    class TryLoadExtraDataEvent(var status: EventStatus) : WorkPlayEvent()
}
