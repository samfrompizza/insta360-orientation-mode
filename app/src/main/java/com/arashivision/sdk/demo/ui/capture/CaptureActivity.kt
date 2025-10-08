package com.arashivision.sdk.demo.ui.capture

import android.annotation.SuppressLint
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

class CaptureActivity : BaseActivity<ActivityCaptureBinding, CaptureViewModel>() {

    private val logger: Logger = XLog.tag(CaptureActivity::class.java.simpleName).build()
    private var captureModeAdapter: CaptureModeAdapter? = null

    private lateinit var gyroController: GyroOrientationController

    // --- VR fields ---
    private var isVrMode: Boolean = false
    private var vrContainer: android.widget.LinearLayout? = null
    private var leftVrPlayer: com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView? = null
    private var rightVrPlayer: com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView? = null
    // Межзрачковое смещение (в градусах) — подберите экспериментально
    private val vrIpdYawDeg: Float = 3.0f
    // --- end VR fields ---

    override fun onStop() {
        super.onStop()
        if (isFinishing) viewModel.closePreviewStream()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun initView() {
        super.initView()
        binding.capturePlayerView.setLifecycle(this.lifecycle)

        // init gyro controller
        gyroController = GyroOrientationController(
            context = this,
            getDisplayRotation = { windowManager.defaultDisplay.rotation },
            applyOrientation = { yaw, pitch -> tryApplyOrientationToPlayer(yaw, pitch) }
        )

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

        // NOTE: button btn_vr_toggle expected in activity_capture.xml
        // If you used a different id, replace binding.btnVrToggle with your id.
    }

    override fun initListener() {
        super.initListener()
        binding.btnCapture.setOnClickListener { viewModel.startCapture() }

        binding.ivCaptureSetting.setOnClickListener { showCaptureSettingView() }

        // VR toggle button (must be present in XML with id btn_vr_toggle)
        try {
            binding.btnVrToggle.setOnClickListener { toggleVrMode() }
        } catch (e: Exception) {
            // если кнопки нет в макете — просто логируем, приложение продолжит работать без VR-переключателя
            logger.w("VR toggle button not found in layout: ${e.message}")
        }

        // calibrate button listener
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
            is CaptureEvent.CameraWiFiDisconnectEvent -> finish()

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

            is CaptureEvent.CameraPreviewStreamParamsChangedEvent -> {
                viewModel.cameraPreviewStreamParamsChanged(binding.capturePlayerView)
            }

            CaptureEvent.RestartPlayerViewEvent -> replay()

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
                        // hide settings + calibrate while recording
                        binding.ivCaptureSetting.visibility = View.GONE
                        binding.btnCalibrate.visibility = View.GONE
                        binding.svCaptureMode.visibility = View.INVISIBLE
                        if (viewModel.isSingleClickAction) {
                            binding.btnCapture.setState(CaptureShutterButton.State.CAPTURING)
                        } else {
                            binding.btnCapture.setState(CaptureShutterButton.State.RECORDING)
                        }
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
        binding.btnCalibrate.visibility = View.VISIBLE
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
                gyroController.calibrate()
                logger.d("Gyro: controller calibrated (requested)")
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
        if (isVrMode) {
            disableVrMode()
            return
        }
        if (!instaCameraManager.isCameraWorking) super.onBackPressed()
    }

    override fun onDestroy() {
        try {
            leftVrPlayer?.destroy()
            rightVrPlayer?.destroy()
        } catch (_: Exception) {}
        binding.capturePlayerView.destroy()
        super.onDestroy()
    }

    // --- sensor lifecycle hooks ---
    override fun onResume() {
        super.onResume()
        gyroController.start()

        try {
            val pipelinePresent = try { binding.capturePlayerView.pipeline != null } catch (e: Exception) { false }
            if (!pipelinePresent) {
                displayPreviewStream()
            } else {
                binding.capturePlayerView.play()
            }
        } catch (t: Throwable) {
            logger.e("onResume preview reinit failed: ${t.message}")
        }

        if (isVrMode) {
            try {
                leftVrPlayer?.play()
                rightVrPlayer?.play()
            } catch (t: Throwable) {
                logger.e("VR resume failed: ${t.message}")
            }
        }
    }


    override fun onPause() {
        super.onPause()
        gyroController.stop()

        if (isVrMode) {
            try {
                // TODO: Pause VR
                //leftVrPlayer?.pause()
                //rightVrPlayer?.pause()
            } catch (_: Exception) {}
        }
    }
    // --- end lifecycle hooks ---

    private fun tryApplyOrientationToPlayer(yawDeg: Float, pitchDeg: Float) {
        val pipelinePresent = try {
            binding.capturePlayerView.pipeline != null
        } catch (e: Exception) {
            false
        }
        if (!pipelinePresent) return

        fun applyTo(obj: Any?, yaw: Float, pitch: Float) {
            if (obj == null) return
            try {
                val cls = obj.javaClass
                try {
                    val mYaw = cls.getMethod("setYaw", Float::class.javaPrimitiveType)
                    mYaw.invoke(obj, yaw)
                } catch (e: NoSuchMethodException) { /* ignore */ }

                try {
                    val mPitch = cls.getMethod("setPitch", Float::class.javaPrimitiveType)
                    mPitch.invoke(obj, pitch)
                } catch (e: NoSuchMethodException) { /* ignore */ }

            } catch (e: Exception) {
                logger.e("applyTo error: ${e.message}")
            }
        }

        try {
            // main player (original)
            applyTo(binding.capturePlayerView, yawDeg, pitchDeg)

            // if VR mode, apply to left/right
            if (isVrMode) {
                leftVrPlayer?.let { applyTo(it, yawDeg - vrIpdYawDeg, pitchDeg) }
                rightVrPlayer?.let { applyTo(it, yawDeg + vrIpdYawDeg, pitchDeg) }
            }
        } catch (e: Exception) {
            logger.e("tryApplyOrientationToPlayer error: ${e.message}")
        }
    }

    // ---------------- VR helper methods ----------------

    private fun toggleVrMode() {
        if (isVrMode) disableVrMode() else enableVrMode()
    }

    private fun enableVrMode() {
        if (isVrMode) return
        isVrMode = true

        // hide main player (мы делаем side-by-side)
        binding.capturePlayerView.isVisible = false

        // create container if needed
        if (vrContainer == null) {
            vrContainer = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            (binding.root as? android.view.ViewGroup)?.addView(vrContainer)
        }

        fun createVrPlayer(): com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView {
            val p = com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView(this)
            p.setLifecycle(this.lifecycle)
            val lp = android.widget.LinearLayout.LayoutParams(0, android.widget.FrameLayout.LayoutParams.MATCH_PARENT, 1f)
            p.layoutParams = lp
            p.keepScreenOn = true
            return p
        }

        if (leftVrPlayer == null) {
            leftVrPlayer = createVrPlayer()
            vrContainer?.addView(leftVrPlayer)
            leftVrPlayer?.setPlayerViewListener(object : PlayerViewListener {
                override fun onFirstFrameRender() {}
                override fun onLoadingFinish() {
                    try { instaCameraManager.setPipeline(leftVrPlayer!!.pipeline) } catch (_: Exception) {}
                }
                override fun onReleaseCameraPipeline() {
                    try { instaCameraManager.setPipeline(null) } catch (_: Exception) {}
                }
            })
            leftVrPlayer?.prepare(viewModel.getCaptureParams())
            leftVrPlayer?.play()
        }

        if (rightVrPlayer == null) {
            rightVrPlayer = createVrPlayer()
            vrContainer?.addView(rightVrPlayer)
            rightVrPlayer?.setPlayerViewListener(object : PlayerViewListener {
                override fun onFirstFrameRender() {}
                override fun onLoadingFinish() {
                    try { instaCameraManager.setPipeline(rightVrPlayer!!.pipeline) } catch (_: Exception) {}
                }
                override fun onReleaseCameraPipeline() {
                    try { instaCameraManager.setPipeline(null) } catch (_: Exception) {}
                }
            })
            rightVrPlayer?.prepare(viewModel.getCaptureParams())
            rightVrPlayer?.play()
        }

        // optional: прятать элементы UI для полноты VR-вида
        //binding.ivCaptureSetting.visibility = View.GONE
        //binding.btnCalibrate.visibility = View.GONE
        //binding.svCaptureMode.visibility = View.INVISIBLE
        // калибровка гироскопа при включении VR
        try { gyroController.calibrate() } catch (_: Exception) {}
    }

    private fun disableVrMode() {
        if (!isVrMode) return
        isVrMode = false

        // restore UI
        binding.ivCaptureSetting.visibility = View.VISIBLE
        binding.btnCalibrate.visibility = View.VISIBLE
        binding.svCaptureMode.visibility = View.VISIBLE

        // stop & destroy VR players
        try {
            //leftVrPlayer?.pause()
            leftVrPlayer?.destroy()
        } catch (_: Exception) {}
        try {
            //rightVrPlayer?.pause()
            rightVrPlayer?.destroy()
        } catch (_: Exception) {}

        // remove container
        vrContainer?.removeAllViews()
        (binding.root as? android.view.ViewGroup)?.removeView(vrContainer)
        vrContainer = null
        leftVrPlayer = null
        rightVrPlayer = null

        // show main player again
        binding.capturePlayerView.isVisible = true
        try { binding.capturePlayerView.play() } catch (_: Exception) {}
    }

    // ---------------------------------------------------

}
