package com.arashivision.sdk.demo.data.camera.usecase

import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.callback.ILiveStatusListener
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveStreamUseCase
    @Inject
    constructor(
        private val cameraManager: InstaCameraManager,
    ) {
        fun startLive(
            rtmpUrl: String,
            listener: ILiveStatusListener,
        ): Result<Unit> {
            if (rtmpUrl.isEmpty()) return Result.failure(Exception("RTMP URL is empty"))
            cameraManager.startLive(rtmpUrl, -1, listener)
            return Result.success(Unit)
        }

        fun stopLive(): Result<Unit> {
            cameraManager.stopLive()
            return Result.success(Unit)
        }
    }
