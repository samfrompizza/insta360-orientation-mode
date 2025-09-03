package com.arashivision.sdk.demo.ui.capture

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Surface
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.arashivision.sdk.demo.R
import com.arashivision.sdk.demo.base.BaseActivity
import com.arashivision.sdk.demo.base.BaseEvent
import com.arashivision.sdk.demo.base.EventStatus.FAILED
import com.arashivision.sdk.demo.base.EventStatus.PROGRESS
import com.arashivision.sdk.demo.base.EventStatus.START
import com.arashivision.sdk.demo.base.EventStatus.SUCCESS
import com.arashivision.sdk.demo.databinding.ActivityCaptureBinding
import com.arashivision.sdk.demo.ext.durationFormat
import com.arashivision.sdk.demo.ext.instaCameraManager
import com.arashivision.sdk.demo.ext.vibrate
import com.arashivision.sdk.demo.ui.capture.adapter.CaptureModeAdapter
import com.arashivision.sdk.demo.view.CaptureShutterButton
import com.arashivision.sdk.demo.view.discretescrollview.DSVOrientation
import com.arashivision.sdk.demo.view.discretescrollview.FadingEdgeDecoration
import com.arashivision.sdk.demo.view.discretescrollview.transform.ScaleTransformer
import com.arashivision.sdk.demo.view.picker.PickData
import com.arashivision.sdkcamera.camera.model.CaptureMode
import com.arashivision.sdkcamera.camera.model.CaptureSetting
import com.arashivision.sdkmedia.player.listener.PlayerViewListener
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import kotlin.math.abs

class CaptureActivity : BaseActivity<ActivityCaptureBinding, CaptureViewModel>() {

    private val logger: Logger = XLog.tag(CaptureActivity::class.java.simpleName).build()

    private var captureModeAdapter: CaptureModeAdapter? = null

    // --- Gyro control fields ---
    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null

    // rate limiting & smoothing
    private val rateLimitMs = 33L // ~30Hz
    private var lastSensorUpdate = 0L

    // сглаживание (меньше = плавнее/медленнее отклик)
    private var smoothingAlpha = 0.1f // можно уменьшить до 0.06..0.08 для более плавного отклика

    // чувствительность / масштабирование (1.0 = линейно)
    private var yawSensitivity = 0.025f   // 0.6 уменьшает отклик по yaw, попробуйте 0.4..1.0
    private var pitchSensitivity = 0.0125f // 0.6 уменьшает отклик по pitch

    // инверсия осей (если управление наоборот)
    private var invertYaw = true   // true — инвертировать поворот влево/вправо
    private var invertPitch = true // true — инвертировать наклон вверх/вниз

    // last raw orientation in degrees (updated from sensor)
    private var lastRawYawDeg = 0f
    private var lastRawPitchDeg = 0f
    private var lastRawRollDeg = 0f

    // smoothed values applied to player
    private var smoothedYaw = 0f
    private var smoothedPitch = 0f

    // calibration offset (set on first frame render)
    private var yawOffset = 0f
    private var calibrated = false

    // whether to apply gyro control
    private var gyroControlEnabled = true
    // --- end gyro fields ---

    override fun onStop() {
        super.onStop()
        if (isFinishing) viewModel.closePreviewStream()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun initView() {
        super.initView()
        binding.capturePlayerView.setLifecycle(this.lifecycle)

        // initialize sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        binding.svCaptureMode.setSlideOnFling(true)
        captureModeAdapter = CaptureModeAdapter()
        binding.svCaptureMode.setAdapter(captureModeAdapter)
        binding.svCaptureMode.setOrientation(DSVOrientation.HORIZONTAL)
        binding.svCaptureMode.setOverScrollEnabled(true)
        binding.svCaptureMode.setSlideOnFling(true)
        binding.svCaptureMode.setSlideOnFlingThreshold(1300)
        binding.svCaptureMode.setItemTransitionTimeMillis(180)

        binding.svCaptureMode.setItemTransformer(
            ScaleTransformer.Builder().setMinScale(0.8f).build()
        )

        binding.svCaptureMode.addItemDecoration(FadingEdgeDecoration())

        binding.pickCaptureSetting.setTitleText(getString(R.string.capture_settings))
    }

    override fun initListener() {
        super.initListener()
        binding.btnCapture.setOnClickListener { viewModel.startCapture() }

        binding.ivCaptureSetting.setOnClickListener { showCaptureSettingView() }

        binding.svCaptureMode.addOnItemChangedListener { _: RecyclerView.ViewHolder?, position: Int ->
            vibrate(50, 10)
            viewModel.switchCaptureMode(position)
        }

        captureModeAdapter?.setItemClickListener { _: String, position: Int ->
            binding.svCaptureMode.smoothScrollToPosition(position)
        }

        binding.pickCaptureSetting.setOnItemClickListener { position, data ->
            val supportCaptureSettingList: List<CaptureSetting> = viewModel.cameraOfflineData.let {
                instaCameraManager.getSupportCaptureSettingList(it.currentCaptureMode)
            }
            val captureSetting: CaptureSetting = supportCaptureSettingList[position]
            viewModel.cameraOfflineData.setCaptureSetting(captureSetting, data) {
                binding.pickCaptureSetting.setData(captureSettingDataList)
            }
            binding.pickCaptureSetting.setData(captureSettingDataList)
        }
    }

    private val captureSettingDataList: List<PickData>
        get() {
            val supportCaptureSettingList: List<CaptureSetting> = viewModel.cameraOfflineData.let {
                instaCameraManager.getSupportCaptureSettingList(it.currentCaptureMode)
            }

            return supportCaptureSettingList.map { getCaptureSettingData(it) }
        }

    private fun getCaptureSettingData(captureSetting: CaptureSetting): PickData {
        val title = getString(getCaptureSettingNameResId(captureSetting))
        val captureSettingValue = viewModel.cameraOfflineData.getCaptureSetting(captureSetting)
        val captureSettingSupportList = viewModel.getCaptureSettingSupportValueList(captureSetting)
        val index: Int = captureSettingSupportList.indexOfFirst { captureSettingValue == it }.coerceAtLeast(0)
        val options = captureSettingSupportList.map { value -> getCaptureSettingValueName(this, captureSetting, value) to value }
        return PickData(true, title, index, options)
    }

    private fun showCaptureSettingView() {
        binding.pickCaptureSetting.setData(captureSettingDataList)
        binding.pickCaptureSetting.show()
    }

    override fun onEvent(event: BaseEvent) {
        super.onEvent(event)
        when (event) {
            // Wi-Fi断连事件
            is CaptureEvent.CameraWiFiDisconnectEvent -> finish()

            // 拍摄页面初始化
            is CaptureEvent.InitCaptureEvent -> {
                logger.d("event.status=${event.status}   event.step=${event.step}")
                when (event.status) {
                    START -> showLoading()

                    PROGRESS -> stepToLoadingTextMap[event.step]?.let { showLoading(it) }

                    SUCCESS -> {
                        showLoading(R.string.capture_rendering_player)
                        displayPreviewStream()
                        updateCaptureModeUi(event.captureModeList, event.currentCaptureMode)
                        updateCaptureButton()
                    }

                    FAILED -> {
                        hideLoading()
                        stepToErrorTextMap[event.step]?.let { lastToast(it) }
                        finish()
                    }
                }
            }
            // 主动切换相机模式事件
            is CaptureEvent.SwitchCaptureModeEvent -> {
                when (event.status) {
                    START -> showLoading(R.string.capture_mode_switching)
                    SUCCESS -> {
                        hideLoading()
                        if (viewModel.isSingleClickAction) {
                            binding.btnCapture.setState(CaptureShutterButton.State.CAPTURE_IDLE)
                        } else {
                            binding.btnCapture.setState(CaptureShutterButton.State.RECORD_IDLE)
                        }
                    }

                    FAILED -> {
                        hideLoading()
                        toast(R.string.capture_mode_switch_failed)
                    }

                    else -> {}
                }
            }

            // 新拍摄流程预览流参数变化通知
            is CaptureEvent.CameraPreviewStreamParamsChangedEvent -> {
                viewModel.cameraPreviewStreamParamsChanged(binding.capturePlayerView)
            }
            // 重启播放器
            CaptureEvent.RestartPlayerViewEvent -> replay()

            // 更新播放器参数，但不重启
            is CaptureEvent.UpdatePlayerViewParamsEvent -> {
                if (event.offsetData != null && event.stabOffset != null) {
                    binding.capturePlayerView.setOffset(event.offsetData, event.stabOffset)
                }
                if (event.windowCropInfo != null) {
                    binding.capturePlayerView.windowCropInfo = event.windowCropInfo
                }
                event.streamResolution?.apply {
                    binding.capturePlayerView.setPreviewResolution(width, height, fps)
                }
            }

            // 拍摄事件
            is CaptureEvent.CameraCaptureEvent -> {
                logger.d("  status=${event.status}")
                when (event.status) {
                    CaptureEvent.CaptureStatus.SD_DISABLE -> toast(R.string.toast_no_sd)
                    CaptureEvent.CaptureStatus.STARTING -> showLoading(R.string.capture_preparing)
                    CaptureEvent.CaptureStatus.STOPPING -> showLoading(R.string.capture_stopping)
                    CaptureEvent.CaptureStatus.WORKING -> {
                        hideLoading()
                        binding.ivCaptureSetting.visibility = View.GONE
                        binding.svCaptureMode.visibility = View.INVISIBLE
                        if (viewModel.isSingleClickAction) {
                            binding.btnCapture.setState(CaptureShutterButton.State.CAPTURING)
                        } else {
                            binding.btnCapture.setState(CaptureShutterButton.State.RECORDING)
                        }
                    }

                    CaptureEvent.CaptureStatus.FINISH -> {
                        captureComplete()
                    }

                    CaptureEvent.CaptureStatus.RECORD_TIME -> {
                        binding.tvRecordTime.visibility = View.VISIBLE
                        binding.tvRecordTime.text = event.recordTime.durationFormat()
                        if (event.videoTime != -1L) {
                            binding.tvVideoDuration.visibility = View.VISIBLE
                            binding.ivArrow.visibility = View.VISIBLE
                            binding.tvVideoDuration.text = event.videoTime.durationFormat()
                        }
                    }

                    CaptureEvent.CaptureStatus.CAPTURE_COUNT -> {
                        binding.tvRecordTime.visibility = View.VISIBLE
                        binding.tvRecordTime.text =
                            getString(R.string.capture_count, event.captureCount)
                    }

                    CaptureEvent.CaptureStatus.ERROR -> {
                        captureComplete()
                        toast(getString(R.string.capture_error, event.errorCode))
                    }
                }
            }

            // 直播事件
            is CaptureEvent.CameraLiveEvent -> {
                when (event.status) {
                    CaptureEvent.LiveStatus.RTMP_EMPTY -> toast(R.string.capture_live_rtmp_empty)

                    CaptureEvent.LiveStatus.START_LIVE ->  showLoading(R.string.capture_live_starting)

                    CaptureEvent.LiveStatus.STOP_LIVE -> showLoading(R.string.capture_live_closing)

                    CaptureEvent.LiveStatus.PUSH_STARTED -> {
                        hideLoading()
                        binding.btnCapture.setState(CaptureShutterButton.State.RECORDING)
                        toast(R.string.capture_live_start_push)
                    }

                    CaptureEvent.LiveStatus.PUSH_FINISHED -> {
                        hideLoading()
                        binding.btnCapture.setState(CaptureShutterButton.State.RECORD_IDLE)
                    }

                    CaptureEvent.LiveStatus.PUSH_ERROR -> {
                        hideLoading()
                        binding.btnCapture.setState(CaptureShutterButton.State.RECORD_IDLE)
                        toast(R.string.capture_live_push_error)
                    }

                }
            }
        }
    }

    private fun captureComplete() {
        hideLoading()
        binding.ivCaptureSetting.visibility = View.VISIBLE
        binding.svCaptureMode.visibility = View.VISIBLE
        binding.tvRecordTime.visibility = View.GONE
        binding.tvVideoDuration.visibility = View.GONE
        binding.ivArrow.setVisibility(View.GONE)
        if (viewModel.isSingleClickAction) {
            binding.btnCapture.setState(CaptureShutterButton.State.CAPTURE_IDLE)
        } else {
            binding.btnCapture.setState(CaptureShutterButton.State.RECORD_IDLE)
        }
    }

    private fun replay() {
        if (isFinishing || isDestroyed) return
        binding.capturePlayerView.prepare(viewModel.getCaptureParams())
        binding.capturePlayerView.play()
    }

    private fun displayPreviewStream() {
        binding.capturePlayerView.setPlayerViewListener(object : PlayerViewListener {
            override fun onFirstFrameRender() {
                hideLoading()
                // calibrate yaw on first frame so current phone heading becomes zero reference
                calibrated = true
                // yawOffset будет выставлен из последнего значения гироскопа, если оно есть
                yawOffset = lastRawYawDeg
                logger.d("Gyro: calibrated yawOffset=$yawOffset")
            }

            override fun onLoadingFinish() {
                instaCameraManager.setPipeline(binding.capturePlayerView.pipeline)
            }

            override fun onReleaseCameraPipeline() {
                instaCameraManager.setPipeline(null)
            }

        })
        binding.capturePlayerView.prepare(viewModel.getCaptureParams())
        binding.capturePlayerView.play()
        binding.capturePlayerView.keepScreenOn = true
    }

    private fun updateCaptureModeUi(captureModeList: List<CaptureMode>?, currentCaptureMode: CaptureMode?) {
        captureModeList?.takeIf { it.isNotEmpty() } ?: return
        currentCaptureMode?.takeIf { it in captureModeList } ?: return

        val data = captureModeList.mapNotNull { mode ->
            getCaptureModeTextResId(mode)?.let { getString(it) }
        }

        captureModeAdapter?.setData(data.toMutableList())
        binding.svCaptureMode.scrollToPosition(captureModeList.indexOf(currentCaptureMode))
    }

    private fun updateCaptureButton(){
        if (viewModel.isSingleClickAction) {
            binding.btnCapture.setState(CaptureShutterButton.State.CAPTURE_IDLE)
        } else {
            binding.btnCapture.setState(CaptureShutterButton.State.RECORD_IDLE)
        }
    }


    override fun onBackPressed() {
        if (binding.pickCaptureSetting.isVisible) {
            binding.pickCaptureSetting.hide()
            return
        }
        if (!instaCameraManager.isCameraWorking) super.onBackPressed()
    }

    override fun onDestroy() {
        binding.capturePlayerView.destroy()
        super.onDestroy()
    }

    // --- sensor lifecycle hooks ---
    override fun onResume() {
        super.onResume()
        rotationVectorSensor?.also { sensor ->
            sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(sensorListener)
    }
    // --- end lifecycle hooks ---

    // SensorEventListener implementation
    private val sensorListener = object : SensorEventListener {
        private val rotMat = FloatArray(9)
        private val remapped = FloatArray(9)
        private val out = FloatArray(3)

        override fun onSensorChanged(event: SensorEvent) {
            if (!gyroControlEnabled) return

            val now = SystemClock.elapsedRealtime()
            if (now - lastSensorUpdate < rateLimitMs) {
                updateRawFromEvent(event)
                return
            }
            lastSensorUpdate = now

            updateRawFromEvent(event)

            // remap coordinate system to screen orientation
            val rotation = windowManager.defaultDisplay.rotation
            when (rotation) {
                Surface.ROTATION_0 -> SensorManager.remapCoordinateSystem(rotMat,
                    SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
                Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(rotMat,
                    SensorManager.AXIS_Z, SensorManager.AXIS_MINUS_X, remapped)
                Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(rotMat,
                    SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Z, remapped)
                Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(rotMat,
                    SensorManager.AXIS_MINUS_Z, SensorManager.AXIS_X, remapped)
                else -> SensorManager.remapCoordinateSystem(rotMat,
                    SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
            }

            SensorManager.getOrientation(remapped, out)
            // out[0]=yaw(рысканье), out[1]=pitch(тангаж), out[2]=roll(крен) in radians
            val yawDeg = Math.toDegrees(out[0].toDouble()).toFloat()
            val pitchDeg = Math.toDegrees(out[1].toDouble()).toFloat()
            val rollDeg = Math.toDegrees(out[2].toDouble()).toFloat()

            lastRawYawDeg = yawDeg
            lastRawPitchDeg = pitchDeg
            lastRawRollDeg = rollDeg

            if (!calibrated) {
                return
            }

            // применяем offset (так нулевой поворот телефона соответствует начальному виду)
            val yawRelative = normalizeAngle(yawDeg - yawOffset)
            val pitchRelative = pitchDeg

            // --- фильтрация ---
            // вычисляем относительный угол (в пределах -180..180)
            val targetYaw = yawRelative * yawSensitivity * if (invertYaw) -1f else 1f
            val targetPitch = pitchRelative * pitchSensitivity * if (invertPitch) -1f else 1f

            // yaw: усредняем по короткой дельте (чтобы не пройти длинный путь через ±180)
            val yawDelta = normalizeAngle(targetYaw - smoothedYaw)
            smoothedYaw = smoothedYaw + smoothingAlpha * yawDelta

            // pitch: на всякий случай тоже используем normalize
            val pitchDelta = normalizeAngle(targetPitch - smoothedPitch)
            smoothedPitch = smoothedPitch + smoothingAlpha * pitchDelta

            // опционально: ограничим углы, чтобы не позволять слишком большие отклонения
            val maxYaw = 180f
            val maxPitch = 80f
            if (smoothedYaw > maxYaw) smoothedYaw = maxYaw
            if (smoothedYaw < -maxYaw) smoothedYaw = -maxYaw
            if (smoothedPitch > maxPitch) smoothedPitch = maxPitch
            if (smoothedPitch < -maxPitch) smoothedPitch = -maxPitch

            // применяем к player view, только если pipeline готов
            tryApplyOrientationToPlayer(smoothedYaw, smoothedPitch)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        private fun updateRawFromEvent(event: SensorEvent) {
            try {
                SensorManager.getRotationMatrixFromVector(rotMat, event.values)
            } catch (t: Throwable) {
                // ignore
            }
        }
    }

    private fun normalizeAngle(angle: Float): Float {
        var a = angle
        while (a <= -180f) a += 360f
        while (a > 180f) a -= 360f
        return a
    }

    private fun tryApplyOrientationToPlayer(yawDeg: Float, pitchDeg: Float) {
        val pipelinePresent = try {
            binding.capturePlayerView.pipeline != null
        } catch (e: Exception) {
            false
        }
        if (!pipelinePresent) return

        try {
            val cls = binding.capturePlayerView.javaClass
            try {
                val mYaw = cls.getMethod("setYaw", Float::class.javaPrimitiveType)
                mYaw.invoke(binding.capturePlayerView, yawDeg)
            } catch (e: NoSuchMethodException) {
                // ignore
            }
            try {
                val mPitch = cls.getMethod("setPitch", Float::class.javaPrimitiveType)
                mPitch.invoke(binding.capturePlayerView, pitchDeg)
            } catch (e: NoSuchMethodException) {
                // ignore
            }

        } catch (e: Exception) {
            logger.e("tryApplyOrientationToPlayer error: ${e.message}")
        }
    }
}
