package com.arashivision.sdk.demo.ui.ability

import com.arashivision.sdk.demo.base.BaseViewModel
import com.arashivision.sdk.demo.base.EventStatus
import com.arashivision.sdk.demo.ext.instaCameraManager
import com.arashivision.sdk.demo.ui.album.AlbumFragment
import com.arashivision.sdkcamera.api.bean.SecretInfo
import com.arashivision.sdkcamera.camera.InstaCameraManager.IActivateCameraCallback
import com.arashivision.sdkcamera.camera.callback.ICameraOperateCallback
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog

class AbilityViewModel : BaseViewModel() {

    private val logger: Logger = XLog.tag(AbilityViewModel::class.java.simpleName).build()

    private val mAppID = "6w6s760hbg0lm216"
    private val mSecretKey = "pq5zwxftyrxq0a7cbsu1jpifptcz3ve7"

    fun formatSdCard() {
        emitEvent(AbilityEvent.FormatSdCardEvent(EventStatus.START))
        instaCameraManager.formatStorage(object : ICameraOperateCallback {

            override fun onSuccessful() {
                emitEvent(AbilityEvent.FormatSdCardEvent(EventStatus.SUCCESS))
            }

            override fun onFailed() {
                emitEvent(AbilityEvent.FormatSdCardEvent(EventStatus.FAILED))
            }

            override fun onCameraConnectError() {
                emitEvent(AbilityEvent.FormatSdCardEvent(EventStatus.FAILED))
            }
        })
    }

    fun shutdownCamera() {
        instaCameraManager.shutdownCamera()
    }

    fun activeCamera() {
        instaCameraManager.activateCamera(SecretInfo(mAppID, mSecretKey), object : IActivateCameraCallback {
                override fun onStart() {
                    emitEvent(AbilityEvent.ActiveCameraEvent(EventStatus.START))
                }

                override fun onSuccess() {
                    emitEvent(AbilityEvent.ActiveCameraEvent(EventStatus.SUCCESS))
                }

                override fun onFailed(error: String?) {
                    logger.d("activateCamera  onFailed  error = $error")
                    emitEvent(AbilityEvent.ActiveCameraEvent(EventStatus.FAILED, error ?: ""))
                }
            })
    }

}
