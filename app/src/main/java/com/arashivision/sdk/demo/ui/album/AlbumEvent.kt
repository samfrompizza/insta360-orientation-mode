package com.arashivision.sdk.demo.ui.album

import com.arashivision.sdk.demo.base.BaseEvent
import com.arashivision.sdk.demo.base.EventStatus
import com.arashivision.sdkmedia.work.WorkWrapper

interface AlbumEvent : BaseEvent {

    class AlbumGetWorkWEvent(
        var status: EventStatus,
        var cameraWorks: List<WorkWrapper> = emptyList(),
        var localWorks: List<WorkWrapper> = emptyList()
    ) : AlbumEvent

    class AlbumDeleteCameraFileEvent(
        var status: EventStatus
    ) : AlbumEvent

    class AlbumDownloadCameraFileEvent(
        var status: EventStatus,
        var index: Int = 0,
        var progress: Int = 0,
        var speed: String = "0 KB/s",
        var paths: List<String>? = null
    ) : AlbumEvent
}
