package com.arashivision.sdk.demo.data.camera.usecase

import com.arashivision.sdkcamera.camera.InstaCameraManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreviewStreamUseCase
    @Inject
    constructor(
        private val cameraManager: InstaCameraManager,
    ) {
        fun closeStream() {
            cameraManager.closePreviewStream()
        }

        fun reopenStream(): Result<Unit> {
            cameraManager.closePreviewStream()
            cameraManager.startPreviewStream(InstaCameraManager.PREVIEW_TYPE_NORMAL)
            cameraManager.setStreamEncode()
            return Result.success(Unit)
        }
    }
