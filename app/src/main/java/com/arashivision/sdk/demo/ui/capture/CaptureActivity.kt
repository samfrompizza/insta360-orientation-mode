package com.arashivision.sdk.demo.ui.capture

import android.annotation.SuppressLint
import android.view.View
import androidx.core.view.isVisible
import com.arashivision.sdk.demo.R
import com.arashivision.sdk.demo.base.BaseActivity
import com.arashivision.sdk.demo.base.BaseEvent
import com.arashivision.sdk.demo.core.vr.UnifiedVrManager
import com.arashivision.sdk.demo.data.sensor.GyroOrientationController
import com.arashivision.sdk.demo.databinding.ActivityCaptureBinding
import com.arashivision.sdk.demo.ext.durationFormat
import com.arashivision.sdk.demo.ext.instaCameraManager
import com.arashivision.sdk.demo.ext.vibrate
import com.arashivision.sdk.demo.ui.capture.EventStatus.FAILED
import com.arashivision.sdk.demo.ui.capture.EventStatus.PROGRESS
import com.arashivision.sdk.demo.ui.capture.EventStatus.START
import com.arashivision.sdk.demo.ui.capture.EventStatus.SUCCESS
import com.arashivision.sdk.demo.view.CaptureShutterButton
import com.arashivision.sdk.demo.view.picker.PickData
import com.arashivision.sdkcamera.camera.model.CaptureMode
import com.arashivision.sdkcamera.camera.model.CaptureSetting
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CaptureActivity :
    BaseActivity<ActivityCaptureBinding, CaptureViewModel>(
        bindingFactory = { ActivityCaptureBinding.inflate(it) },
        viewModelClass = CaptureViewModel::class.java,
    ) {
    private val logger: Logger = XLog.tag(CaptureActivity::class.java.simpleName).build()

    private val gyroController: GyroOrientationController by lazy {
        GyroOrientationController(
            context = this,
            getDisplayRotation = { windowManager.defaultDisplay.rotation },
            applyOrientation = { yaw, pitch -> tryApplyOrientationToPlayer(yaw, pitch) },
        )
    }

    private val vrSource by lazy {
        CapturePlayerVrSource(activity = this, mainPlayerView = binding.capturePlayerView)
    }

    private val vrManager: UnifiedVrManager by lazy {
        UnifiedVrManager(
            activity = this,
            rootContainer = binding.root,
            vrSource = vrSource,
            overlaysToHide = listOf(binding.svCaptureMode, binding.ivCaptureSetting, binding.btnCalibrate),
            onCalibrateGyro = { runCatching { gyroController.calibrate() } },
        )
    }

    private val previewController: CapturePreviewController by lazy {
        CapturePreviewController(
            playerView = binding.capturePlayerView,
            onCalibrateGyro = { gyroController.calibrate() },
            onHideLoading = { hideLoading() },
        )
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) viewModel.closePreviewStream()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun initView() {
        super.initView()
        binding.pickCaptureSetting.setTitleText(getString(R.string.capture_settings))
    }

    override fun initListener() {
        super.initListener()
        binding.btnCapture.setOnClickListener { viewModel.startCapture() }
        binding.ivCaptureSetting.setOnClickListener { showCaptureSettingView() }

        try {
            binding.btnVrToggle.setOnClickListener { vrManager.toggleVrMode() }
        } catch (e: Exception) {
            logger.w("VR toggle button not found in layout: ${e.message}")
        }

        binding.btnCalibrate.setOnClickListener {
            try {
                gyroController.calibrate()
                vibrate(50, 10)
                toast("Gyro calibration requested")
                logger.d("Gyro: calibration requested (manual)")
            } catch (e: Exception) {
                logger.e("Gyro calibration failed: ${e.message}")
                toast("Gyro calibration failed")
            }
        }

        binding.svCaptureMode.onModeChanged { position ->
            vibrate(50, 10)
            viewModel.switchCaptureMode(position)
        }

        binding.pickCaptureSetting.setOnItemClickListener { position, data ->
            val supportCaptureSettingList: List<CaptureSetting> =
                viewModel.cameraOfflineData.let {
                    instaCameraManager.getSupportCaptureSettingList(it.currentCaptureMode)
                }

            if (vrManager.isVrMode && position >= supportCaptureSettingList.size) {
                try {
                    binding.pickCaptureSetting.hide()
                    vrManager.showVrSettingsDialog()
                } catch (e: Exception) {
                    logger.e("Failed to open VR settings dialog: ${e.message}")
                }
                return@setOnItemClickListener
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
            val supportCaptureSettingList: List<CaptureSetting> =
                viewModel.cameraOfflineData.let {
                    instaCameraManager.getSupportCaptureSettingList(it.currentCaptureMode)
                }

            val list = supportCaptureSettingList.map { getCaptureSettingData(it) }.toMutableList()

            try {
                if (vrManager.isVrMode) {
                    val vrPick =
                        PickData(
                            true,
                            "VR: Adjust eyes",
                            0,
                            listOf("Open VR settings" to 0),
                        )
                    list.add(vrPick)
                }
            } catch (e: Exception) {
            }

            return list
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
            is CaptureEvent.CameraWiFiDisconnectEvent -> finish()

            is CaptureEvent.InitCaptureEvent -> {
                logger.d("event.status=${event.status}   event.step=${event.step}")
                when (event.status) {
                    START -> showLoading()
                    PROGRESS -> stepToLoadingTextMap[event.step]?.let { showLoading(it) }
                    SUCCESS -> {
                        showLoading(R.string.capture_rendering_player)
                        previewController.displayPreviewStream(viewModel.getCaptureParams(), lifecycle)
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

            is CaptureEvent.SwitchCaptureModeEvent -> {
                when (event.status) {
                    START -> showLoading(R.string.capture_mode_switching)
                    SUCCESS -> {
                        hideLoading()
                        updateCaptureButton()
                    }
                    FAILED -> {
                        hideLoading()
                        toast(R.string.capture_mode_switch_failed)
                    }
                    else -> {}
                }
            }

            is CaptureEvent.CameraPreviewStreamParamsChangedEvent -> {
                viewModel.cameraPreviewStreamParamsChanged(binding.capturePlayerView)
            }

            CaptureEvent.RestartPlayerViewEvent -> {
                if (!isFinishing && !isDestroyed) {
                    previewController.replay(viewModel.getCaptureParams())
                }
            }

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

            is CaptureEvent.CameraCaptureEvent -> {
                logger.d("  status=${event.status}")
                when (event.status) {
                    CaptureEvent.CaptureStatus.SD_DISABLE -> toast(R.string.toast_no_sd)
                    CaptureEvent.CaptureStatus.STARTING -> showLoading(R.string.capture_preparing)
                    CaptureEvent.CaptureStatus.STOPPING -> showLoading(R.string.capture_stopping)
                    CaptureEvent.CaptureStatus.WORKING -> {
                        hideLoading()
                        binding.ivCaptureSetting.visibility = View.GONE
                        binding.btnCalibrate.visibility = View.GONE
                        binding.svCaptureMode.visibility = View.INVISIBLE
                        updateCaptureButtonState(true)
                    }
                    CaptureEvent.CaptureStatus.FINISH -> captureComplete()
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

            is CaptureEvent.CameraLiveEvent -> {
                when (event.status) {
                    CaptureEvent.LiveStatus.RTMP_EMPTY -> toast(R.string.capture_live_rtmp_empty)
                    CaptureEvent.LiveStatus.START_LIVE -> showLoading(R.string.capture_live_starting)
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
        binding.tvRecordTime.visibility = View.GONE
        binding.tvVideoDuration.visibility = View.GONE
        binding.ivArrow.setVisibility(View.GONE)
        updateCaptureButton()
        if (!vrManager.isVrMode) {
            binding.ivCaptureSetting.visibility = View.VISIBLE
            binding.btnCalibrate.visibility = View.VISIBLE
            binding.svCaptureMode.visibility = View.VISIBLE
        }
    }

    private fun updateCaptureModeUi(
        captureModeList: List<CaptureMode>?,
        currentCaptureMode: CaptureMode?,
    ) {
        captureModeList?.takeIf { it.isNotEmpty() } ?: return
        currentCaptureMode?.takeIf { it in captureModeList } ?: return

        val data =
            captureModeList.mapNotNull { mode ->
                getCaptureModeTextResId(mode)?.let { getString(it) }
            }

        binding.svCaptureMode.setModes(data, captureModeList.indexOf(currentCaptureMode))
    }

    private fun updateCaptureButton() {
        if (viewModel.isSingleClickAction) {
            binding.btnCapture.setState(CaptureShutterButton.State.CAPTURE_IDLE)
        } else {
            binding.btnCapture.setState(CaptureShutterButton.State.RECORD_IDLE)
        }
    }

    private fun updateCaptureButtonState(isWorking: Boolean) {
        if (viewModel.isSingleClickAction) {
            binding.btnCapture.setState(if (isWorking) CaptureShutterButton.State.CAPTURING else CaptureShutterButton.State.CAPTURE_IDLE)
        } else {
            binding.btnCapture.setState(if (isWorking) CaptureShutterButton.State.RECORDING else CaptureShutterButton.State.RECORD_IDLE)
        }
    }

    override fun onBackPressed() {
        if (binding.pickCaptureSetting.isVisible) {
            binding.pickCaptureSetting.hide()
            return
        }
        if (vrManager.isVrMode) {
            vrManager.exitVrModeAndRestart()
            return
        }
        if (!instaCameraManager.isCameraWorking) super.onBackPressed()
    }

    override fun onDestroy() {
        vrManager.destroy()
        previewController.destroy()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        gyroController.start()

        try {
            val pipelinePresent =
                try {
                    binding.capturePlayerView.pipeline != null
                } catch (e: Exception) {
                    false
                }
            if (!pipelinePresent) {
                previewController.displayPreviewStream(viewModel.getCaptureParams(), lifecycle)
            } else {
                previewController.play()
            }
        } catch (t: Throwable) {
            logger.e("onResume preview reinit failed: ${t.message}")
        }

        vrManager.onResume()
    }

    override fun onPause() {
        super.onPause()
        gyroController.stop()
        vrManager.onPause()
    }

    private fun tryApplyOrientationToPlayer(
        yawDeg: Float,
        pitchDeg: Float,
    ) {
        val pipelinePresent =
            try {
                binding.capturePlayerView.pipeline != null
            } catch (e: Exception) {
                false
            }
        if (!pipelinePresent) return

        fun applyTo(
            obj: Any?,
            yaw: Float,
            pitch: Float,
        ) {
            if (obj == null) return
            try {
                val cls = obj.javaClass
                try {
                    val mYaw = cls.getMethod("setYaw", Float::class.javaPrimitiveType)
                    mYaw.invoke(obj, yaw)
                } catch (e: NoSuchMethodException) {
                }

                try {
                    val mPitch = cls.getMethod("setPitch", Float::class.javaPrimitiveType)
                    mPitch.invoke(obj, pitch)
                } catch (e: NoSuchMethodException) {
                }
            } catch (e: Exception) {
                logger.e("applyTo error: ${e.message}")
            }
        }

        try {
            if (vrManager.isVrMode) {
                vrManager.applyOrientation(yawDeg, pitchDeg)
            } else {
                applyTo(binding.capturePlayerView, yawDeg, pitchDeg)
            }
        } catch (e: Exception) {
            logger.e("tryApplyOrientationToPlayer error: ${e.message}")
        }
    }
}
