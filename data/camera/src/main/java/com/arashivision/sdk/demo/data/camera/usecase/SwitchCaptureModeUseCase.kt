package com.arashivision.sdk.demo.data.camera.usecase

import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.model.CaptureMode
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class SwitchCaptureModeUseCase
    @Inject
    constructor(
        private val cameraManager: InstaCameraManager,
    ) {
        suspend operator fun invoke(position: Int): Result<CaptureMode> {
            if (position > cameraManager.supportCaptureMode.size - 1) {
                return Result.failure(IndexOutOfBoundsException("Invalid capture mode position: $position"))
            }
            val captureMode =
                cameraManager.supportCaptureMode[position]
                    ?: return Result.failure(NullPointerException("Capture mode at position $position is null"))

            val success =
                suspendCancellableCoroutine { cont ->
                    cameraManager.setCaptureMode(captureMode) { code ->
                        cont.resume(code == 0)
                    }
                }
            return if (success) Result.success(captureMode) else Result.failure(Exception("Failed to switch capture mode"))
        }
    }
