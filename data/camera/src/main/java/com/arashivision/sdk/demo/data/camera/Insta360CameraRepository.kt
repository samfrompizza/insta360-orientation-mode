package com.arashivision.sdk.demo.data.camera

import com.arashivision.sdk.demo.domain.model.CameraStatus
import com.arashivision.sdk.demo.domain.repository.CameraRepository
import com.arashivision.sdkcamera.camera.InstaCameraManager

class Insta360CameraRepository : CameraRepository {
    private val cameraManager: InstaCameraManager
        get() = InstaCameraManager.getInstance()

    override fun isConnected(): Boolean =
        cameraManager.cameraConnectedType in
            arrayOf(
                InstaCameraManager.CONNECT_TYPE_USB,
                InstaCameraManager.CONNECT_TYPE_WIFI,
            )

    override fun connectViaUSB(): Result<Unit> =
        runCatching {
            cameraManager.openCamera(InstaCameraManager.CONNECT_TYPE_USB)
        }

    override fun connectViaWiFi(): Result<Unit> =
        runCatching {
            cameraManager.openCamera(InstaCameraManager.CONNECT_TYPE_WIFI)
        }

    override fun disconnect() {
        cameraManager.closeCamera()
    }

    override fun getStatus(): CameraStatus =
        CameraStatus(
            isConnected = isConnected(),
            isRecording = cameraManager.isCameraWorking,
            batteryLevel = cameraManager.cameraCurrentBatteryLevel,
            isCharging = cameraManager.isCameraCharging,
            sdCardInserted = cameraManager.isSdCardEnabled,
            freeSpaceMB = cameraManager.cameraStorageFreeSpace,
            totalSpaceMB = cameraManager.cameraStorageTotalSpace,
        )

    override fun startCapture() {
        cameraManager.startNormalCapture()
    }

    override fun stopCapture() {
        cameraManager.stopNormalRecord()
    }

    override fun openPreviewStream() {
        cameraManager.startPreviewStream(InstaCameraManager.PREVIEW_TYPE_NORMAL)
    }

    override fun closePreviewStream() {
        cameraManager.closePreviewStream()
    }

    override fun switchCaptureMode(mode: Int) {
        val modes = cameraManager.supportCaptureMode
        if (mode in modes.indices) {
            modes[mode]?.let { captureMode ->
                cameraManager.setCaptureMode(captureMode, null)
            }
        }
    }
}
