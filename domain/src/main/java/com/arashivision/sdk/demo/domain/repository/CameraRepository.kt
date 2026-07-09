package com.arashivision.sdk.demo.domain.repository

import com.arashivision.sdk.demo.domain.model.CameraStatus

interface CameraRepository {
    fun isConnected(): Boolean

    fun connectViaUSB(): Result<Unit>

    fun connectViaWiFi(): Result<Unit>

    fun disconnect()

    fun getStatus(): CameraStatus

    fun startCapture()

    fun stopCapture()

    fun openPreviewStream()

    fun closePreviewStream()

    fun switchCaptureMode(mode: Int)
}
