package com.arashivision.sdk.demo.data.camera.usecase

import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.model.CaptureMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureControlUseCase
    @Inject
    constructor(
        private val cameraManager: InstaCameraManager,
    ) {
        fun isSingleClickAction(currentMode: CaptureMode): Boolean =
            currentMode.isPhotoMode &&
                currentMode !in listOf(CaptureMode.INTERVAL_SHOOTING, CaptureMode.STARLAPSE_SHOOTING)

        fun startCapture(currentMode: CaptureMode): Result<Unit> {
            if (isSingleClickAction(currentMode)) {
                takePhotos(currentMode)
            } else {
                startRecord(currentMode)
            }
            return Result.success(Unit)
        }

        fun stopCapture(currentMode: CaptureMode): Result<Unit> {
            if (!isSingleClickAction(currentMode)) {
                stopRecord(currentMode)
            }
            return Result.success(Unit)
        }

        private fun takePhotos(captureMode: CaptureMode) {
            when (captureMode) {
                CaptureMode.CAPTURE_NORMAL -> cameraManager.startNormalCapture()
                CaptureMode.HDR_CAPTURE -> cameraManager.startHDRCapture()
                CaptureMode.NIGHT_SCENE -> cameraManager.startNightScene()
                CaptureMode.BURST -> cameraManager.startBurstCapture()
                else -> {}
            }
        }

        private fun startRecord(captureMode: CaptureMode) {
            if (!cameraManager.isSdCardEnabled) return
            when (captureMode) {
                CaptureMode.RECORD_NORMAL -> cameraManager.startNormalRecord()
                CaptureMode.BULLETTIME -> cameraManager.startBulletTime()
                CaptureMode.TIMELAPSE -> cameraManager.startTimeLapse()
                CaptureMode.HDR_RECORD -> cameraManager.startHDRRecord()
                CaptureMode.TIME_SHIFT -> cameraManager.startTimeShift()
                CaptureMode.LOOPER_RECORDING -> cameraManager.startLooperRecord()
                CaptureMode.SUPER_RECORD -> cameraManager.startSuperRecord()
                CaptureMode.SLOW_MOTION -> cameraManager.startSlowMotionRecord()
                CaptureMode.SELFIE_RECORD -> cameraManager.startSelfieRecord()
                CaptureMode.PURE_RECORD -> cameraManager.startPureRecord()
                CaptureMode.INTERVAL_SHOOTING -> cameraManager.startIntervalShooting()
                CaptureMode.STARLAPSE_SHOOTING -> cameraManager.startStarLapseShooting()
                else -> {}
            }
        }

        private fun stopRecord(captureMode: CaptureMode) {
            when (captureMode) {
                CaptureMode.RECORD_NORMAL -> cameraManager.stopNormalRecord()
                CaptureMode.BULLETTIME -> cameraManager.stopBulletTime()
                CaptureMode.TIMELAPSE -> cameraManager.stopTimeLapse()
                CaptureMode.HDR_RECORD -> cameraManager.stopHDRRecord()
                CaptureMode.TIME_SHIFT -> cameraManager.stopTimeShift()
                CaptureMode.LOOPER_RECORDING -> cameraManager.stopLooperRecord()
                CaptureMode.SUPER_RECORD -> cameraManager.stopSuperRecord()
                CaptureMode.SLOW_MOTION -> cameraManager.stopSlowMotionRecord()
                CaptureMode.SELFIE_RECORD -> cameraManager.stopSelfieRecord()
                CaptureMode.PURE_RECORD -> cameraManager.stopPureRecord()
                CaptureMode.INTERVAL_SHOOTING -> cameraManager.stopIntervalShooting()
                CaptureMode.STARLAPSE_SHOOTING -> cameraManager.stopStarLapseShooting()
                else -> {}
            }
        }
    }
