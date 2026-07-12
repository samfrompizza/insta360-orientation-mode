package com.arashivision.sdk.demo.data.camera.usecase

import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.callback.ICameraOperateCallback
import com.arashivision.sdkcamera.camera.callback.ICaptureSupportConfigCallback
import com.arashivision.sdkcamera.camera.model.SensorMode
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class InitializeCameraUseCase
    @Inject
    constructor(
        private val cameraManager: InstaCameraManager,
    ) {
        suspend fun checkSensorMode(): Boolean {
            if (cameraManager.currentSensorMode == SensorMode.PANORAMA) return true
            return suspendCancellableCoroutine { cont ->
                cameraManager.switchPanoramaSensorMode(
                    object : ICameraOperateCallback {
                        override fun onSuccessful() = cont.resume(true)

                        override fun onFailed() = cont.resume(false)

                        override fun onCameraConnectError() = cont.resume(false)
                    },
                )
            }
        }

        suspend fun fetchOptions(): Boolean =
            suspendCancellableCoroutine { cont ->
                cameraManager.fetchCameraOptions(
                    object : ICameraOperateCallback {
                        override fun onSuccessful() = cont.resume(true)

                        override fun onFailed() = cont.resume(false)

                        override fun onCameraConnectError() = cont.resume(false)
                    },
                )
            }

        suspend fun initSupportConfig(): Boolean =
            suspendCancellableCoroutine { cont ->
                cameraManager.initCameraSupportConfig(
                    object : ICaptureSupportConfigCallback {
                        override fun onComplete() = cont.resume(true)

                        override fun onFailed(s: String) = cont.resume(false)
                    },
                )
            }
    }
