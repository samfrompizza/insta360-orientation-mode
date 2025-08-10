package com.arashivision.sdk.demo.capture

import com.arashivision.sdk.demo.ext.getCaptureSettingValue
import com.arashivision.sdk.demo.ext.instaCameraManager
import com.arashivision.sdk.demo.ext.setCaptureSettingValue
import com.arashivision.sdkcamera.camera.InstaCameraManager.IDependChecker
import com.arashivision.sdkcamera.camera.model.AEB
import com.arashivision.sdkcamera.camera.model.BurstCapture
import com.arashivision.sdkcamera.camera.model.CaptureMode
import com.arashivision.sdkcamera.camera.model.CaptureSetting
import com.arashivision.sdkcamera.camera.model.CaptureSetting.*
import com.arashivision.sdkcamera.camera.model.DarkEisType
import com.arashivision.sdkcamera.camera.model.EV
import com.arashivision.sdkcamera.camera.model.EVInterval
import com.arashivision.sdkcamera.camera.model.Exposure
import com.arashivision.sdkcamera.camera.model.GammaMode
import com.arashivision.sdkcamera.camera.model.HdrStatus
import com.arashivision.sdkcamera.camera.model.ILogStatus
import com.arashivision.sdkcamera.camera.model.ISO
import com.arashivision.sdkcamera.camera.model.ISOTopLimit
import com.arashivision.sdkcamera.camera.model.InternalSplicing
import com.arashivision.sdkcamera.camera.model.Interval
import com.arashivision.sdkcamera.camera.model.LiveBitrate
import com.arashivision.sdkcamera.camera.model.PanoExposureMode
import com.arashivision.sdkcamera.camera.model.PhotoResolution
import com.arashivision.sdkcamera.camera.model.RawType
import com.arashivision.sdkcamera.camera.model.RecordDuration
import com.arashivision.sdkcamera.camera.model.RecordResolution
import com.arashivision.sdkcamera.camera.model.Shutter
import com.arashivision.sdkcamera.camera.model.ShutterMode
import com.arashivision.sdkcamera.camera.model.WB
import com.arashivision.sdkcamera.camera.model.proto.PhotoHdrType
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 相机离线参数，作用有2个
 * 1. 缓存相机参数，不用每次都去相机中去参数，减少与相机之间通讯所花费的时间
 * 2. 适配旧的拍摄流程（即X3以及X3之前的相机）
 */
class CameraOfflineData {

    private val logger: Logger = XLog.tag(CameraOfflineData::class.java.simpleName).build()

    private val dataMap = mutableMapOf<CaptureMode, MutableMap<CaptureSetting, Any>>()

    /**
     * 当前拍摄模式，以此为准.
     * 在旧的拍摄流程中，打开预览流时，获取到的拍摄模式固定为普通录像，因此通过[instaCameraManager]获取到的拍摄模式没有意义
     */
    var currentCaptureMode = CaptureMode.RECORD_NORMAL
        private set

    init {
        // 初始化拍摄模式并矫正
        currentCaptureMode = instaCameraManager.currentCaptureMode
        if (currentCaptureMode !in instaCameraManager.supportCaptureMode) {
            currentCaptureMode = CaptureMode.RECORD_NORMAL
        }

        // 获取并矫正CaptureSetting
        // 矫正：获取当前相机中设置的数值是否符合支持列表中的配置要求，如果不符合则取默认值
        instaCameraManager.supportCaptureMode.filterNotNull().forEach { captureMode ->
            val data: MutableMap<CaptureSetting, Any> = dataMap.getOrDefault(captureMode, mutableMapOf())
            instaCameraManager.getSupportCaptureSettingList(captureMode).filterNotNull().forEach {
                captureSetting -> data[captureSetting] = getCaptureSetting(captureMode, captureSetting)
            }
            dataMap[captureMode] = data
        }
    }


    suspend fun setCaptureMode(captureMode: CaptureMode): Boolean {
        return suspendCancellableCoroutine {
            instaCameraManager.setCaptureMode(captureMode) { code ->
                if (code == 0) {
                    currentCaptureMode = captureMode
                }
                it.resume(code == 0)
            }
        }
    }

    fun setCaptureSetting(
        captureSetting: CaptureSetting,
        value: Any,
        checker: ((checks: List<CaptureSetting>) -> Unit)? = null
    ) {
        setCaptureSetting(currentCaptureMode, captureSetting, value, checker)
    }

    private fun setCaptureSetting(
        captureMode: CaptureMode,
        captureSetting: CaptureSetting,
        value: Any,
        checker: ((checks: List<CaptureSetting>) -> Unit)? = null
    ) {
        setCaptureSettingValue(captureMode, captureSetting, value) { checks ->
            checks.add(captureSetting)
            checks.distinct().forEach {
                val data = dataMap.getOrDefault(captureMode, mutableMapOf())
                data[it] = getCaptureSettingValue(captureMode, it)
                dataMap[captureMode] = data
            }
            checker?.invoke(checks)
        }
    }

    fun getCaptureSetting(captureSetting: CaptureSetting): Any {
        return getCaptureSetting(currentCaptureMode, captureSetting)
    }

    fun getCaptureSetting(captureMode: CaptureMode, captureSetting: CaptureSetting): Any {
        return dataMap[captureMode]?.get(captureSetting) ?: run {
            val value = getCaptureSettingValue(captureMode, captureSetting)
            val data = dataMap.getOrDefault(captureMode, mutableMapOf())
            data[captureSetting] = value
            dataMap[captureMode] = data
            value
        }
    }
    
}