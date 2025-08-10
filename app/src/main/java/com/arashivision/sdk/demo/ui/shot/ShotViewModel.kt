package com.arashivision.sdk.demo.ui.shot

import androidx.lifecycle.viewModelScope
import com.arashivision.insta360.basemedia.model.gps.GpsData
import com.arashivision.sdk.demo.base.BaseViewModel
import com.arashivision.sdk.demo.base.EventStatus
import com.arashivision.sdk.demo.ext.durationFormat
import com.arashivision.sdk.demo.ext.instaCameraManager
import com.arashivision.sdk.demo.util.LocationManager
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.callback.ICameraOperateCallback
import com.arashivision.sdkcamera.camera.callback.ICaptureStatusListener
import com.arashivision.sdkcamera.camera.callback.ICaptureSupportConfigCallback
import com.arashivision.sdkcamera.camera.model.CaptureMode
import com.arashivision.sdkcamera.camera.model.CaptureSetting
import com.arashivision.sdkcamera.camera.model.SensorMode
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ShotViewModel : BaseViewModel(), ICaptureStatusListener {

    private val logger: Logger = XLog.tag(ShotViewModel::class.java.simpleName).build()

    val supportCaptureSettings: List<CaptureSetting>
        get() = instaCameraManager.getSupportCaptureSettingList(currentCaptureMode)

    val captureModeList: List<CaptureMode>
        get() = instaCameraManager.supportCaptureMode.filter {
            it !in arrayOf(
                CaptureMode.LIVE,
                CaptureMode.LIVE_ANIMATION,
                CaptureMode.VIDEO_NONE,
                CaptureMode.PHOTO_NONE
            )
        }

    var currentCaptureMode: CaptureMode = CaptureMode.CAPTURE_NORMAL


    private val isSingleClickAction: Boolean
        get() = currentCaptureMode.let {
            it.isPhotoMode && it !in listOf(
                CaptureMode.INTERVAL_SHOOTING,
                CaptureMode.STARLAPSE_SHOOTING
            )
        }

    val isCameraWorking: Boolean
        get() = instaCameraManager.isCameraWorking

    init {
        instaCameraManager.setCaptureStatusListener(this)
        switchCaptureMode(currentCaptureMode)
        initCameraSupportConfig()
    }

    private fun initCameraSupportConfig() {
        instaCameraManager.initCameraSupportConfig(object : ICaptureSupportConfigCallback {
            override fun onComplete() {
                logger.d("initCameraSupportConfig success")
            }

            override fun onFailed(s: String) {
                logger.d("initCameraSupportConfig failed : $s")
            }
        })
    }

    fun switchPanoramaSensorMode() {
        emitEvent(ShotEvent.SwitchPanoramaSensorModeEvent(EventStatus.START))

        if (instaCameraManager.currentSensorMode == SensorMode.PANORAMA) {
            logger.d("checkCameraSensorMode already panorama")
            emitEvent(ShotEvent.SwitchPanoramaSensorModeEvent(EventStatus.SUCCESS))
            return
        }

        InstaCameraManager.getInstance().switchPanoramaSensorMode(object : ICameraOperateCallback {
            override fun onSuccessful() {
                logger.d("switch sensor success.")
                emitEvent(ShotEvent.SwitchPanoramaSensorModeEvent(EventStatus.SUCCESS))
            }

            override fun onFailed() {
                logger.d("switch sensor fail.")
                emitEvent(ShotEvent.SwitchPanoramaSensorModeEvent(EventStatus.FAILED))
            }

            override fun onCameraConnectError() {
                logger.d("switch sensor error.")
                emitEvent(ShotEvent.SwitchPanoramaSensorModeEvent(EventStatus.FAILED))
            }
        })
    }

    fun switchCaptureMode(captureMode: CaptureMode) {
        currentCaptureMode = captureMode
        viewModelScope.launch { setCaptureMode(captureMode) }
    }

    private suspend fun setCaptureMode(captureMode: CaptureMode): Boolean {
        return suspendCancellableCoroutine {
            instaCameraManager.setCaptureMode(captureMode) { code ->
                it.resume(code == 0)
            }
        }
    }

    private fun getGpsData(): ByteArray? {
        return LocationManager.currentLocation?.let {
            val gpsData = GpsData()
            gpsData.latitude = it.latitude
            gpsData.longitude = it.longitude
            gpsData.groundSpeed = it.speed.toDouble()
            gpsData.groundCrouse = it.bearing.toDouble()
            gpsData.geoidUndulation = it.altitude
            gpsData.utcTimeMs = it.time
            gpsData.isVaild = true
            val gpsData2ByteArray = GpsData.GpsData2ByteArray(listOf(gpsData))
            logger.d("gpsData2ByteArray = ${String(gpsData2ByteArray, Charsets.UTF_8)}")
            gpsData2ByteArray

        }
    }

    fun startWork() {
        if (!instaCameraManager.isSdCardEnabled) {
            emitEvent(ShotEvent.SDCardDisableEvent)
            return
        }
        when (currentCaptureMode) {
            CaptureMode.CAPTURE_NORMAL -> {
                getGpsData()?.let {
                    instaCameraManager.startNormalCapture(it)
                }?.run {
                    instaCameraManager.startNormalCapture()
                }
            }

            CaptureMode.HDR_CAPTURE -> instaCameraManager.startHDRCapture()
            CaptureMode.NIGHT_SCENE -> instaCameraManager.startNightScene()
            CaptureMode.BURST -> instaCameraManager.startBurstCapture()
            CaptureMode.RECORD_NORMAL -> instaCameraManager.startNormalRecord()
            CaptureMode.BULLETTIME -> instaCameraManager.startBulletTime()
            CaptureMode.TIMELAPSE -> instaCameraManager.startTimeLapse()
            CaptureMode.HDR_RECORD -> instaCameraManager.startHDRRecord()
            CaptureMode.TIME_SHIFT -> instaCameraManager.startTimeShift()
            CaptureMode.LOOPER_RECORDING -> instaCameraManager.startLooperRecord()
            CaptureMode.SUPER_RECORD -> instaCameraManager.startSuperRecord()
            CaptureMode.SLOW_MOTION -> instaCameraManager.startSlowMotionRecord()
            CaptureMode.SELFIE_RECORD -> instaCameraManager.startSelfieRecord()
            CaptureMode.PURE_RECORD -> instaCameraManager.startPureRecord()
            CaptureMode.INTERVAL_SHOOTING -> instaCameraManager.startIntervalShooting()
            CaptureMode.STARLAPSE_SHOOTING -> instaCameraManager.startStarLapseShooting()
            else -> {}
        }
    }

    fun stopWork() {
        if (isSingleClickAction) return
        when (currentCaptureMode) {
            CaptureMode.RECORD_NORMAL -> instaCameraManager.stopNormalRecord()
            CaptureMode.BULLETTIME -> instaCameraManager.stopBulletTime()
            CaptureMode.TIMELAPSE -> instaCameraManager.stopTimeLapse()
            CaptureMode.HDR_RECORD -> instaCameraManager.stopHDRRecord()
            CaptureMode.TIME_SHIFT -> instaCameraManager.stopTimeShift()
            CaptureMode.LOOPER_RECORDING -> instaCameraManager.stopLooperRecord()
            CaptureMode.SUPER_RECORD -> instaCameraManager.stopSuperRecord()
            CaptureMode.SLOW_MOTION -> instaCameraManager.stopSlowMotionRecord()
            CaptureMode.SELFIE_RECORD -> instaCameraManager.stopSelfieRecord()
            CaptureMode.PURE_RECORD -> instaCameraManager.stopPureRecord()
            CaptureMode.INTERVAL_SHOOTING -> instaCameraManager.stopIntervalShooting()
            CaptureMode.STARLAPSE_SHOOTING -> instaCameraManager.stopStarLapseShooting()
            else -> {}
        }
    }

    override fun onCaptureStarting() {
        super.onCaptureStarting()
        logger.d("onCaptureStarting")
        emitEvent(ShotEvent.CaptureStartingEvent)
    }

    override fun onCaptureStopping() {
        super.onCaptureStopping()
        logger.d("onCaptureStopping")
        emitEvent(ShotEvent.CaptureStoppingEvent)
    }

    override fun onCaptureWorking() {
        super.onCaptureWorking()
        logger.d("onCaptureWorking")
        emitEvent(ShotEvent.CaptureWorkingEvent(isSingleClickAction))
    }

    override fun onCaptureFinish(paths: Array<out String>?) {
        logger.d("onCaptureFinish paths=$paths")
        emitEvent(ShotEvent.CaptureFinishEvent(paths?.toList() ?: emptyList()))
    }

    override fun onCaptureTimeChanged(captureTime: Long) {
        logger.d("onCaptureTimeChanged -> ${captureTime.durationFormat()}")
        emitEvent(ShotEvent.CaptureTimeChangedEvent(captureTime))
    }

    override fun onCaptureCountChanged(captureCount: Int) {
        logger.d("onCaptureCountChanged -> $captureCount")
        emitEvent(ShotEvent.CaptureCountChangedEvent(captureCount))

    }

    override fun onCaptureError(p0: Int) {
        logger.d("onCaptureError -> $p0")
        emitEvent(ShotEvent.CaptureErrorEvent(p0))
    }

    override fun onCleared() {
        super.onCleared()
        instaCameraManager.setCaptureStatusListener(null)
    }
}