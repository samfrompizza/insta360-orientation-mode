package com.arashivision.sdk.demo.ui.shot

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import com.arashivision.sdk.demo.R
import com.arashivision.sdk.demo.base.BaseActivity
import com.arashivision.sdk.demo.base.BaseEvent
import com.arashivision.sdk.demo.base.EventStatus
import com.arashivision.sdk.demo.databinding.ActivityShotBinding
import com.arashivision.sdk.demo.ext.durationFormat
import com.arashivision.sdk.demo.ext.getCaptureSettingSupportList
import com.arashivision.sdk.demo.ext.getCaptureSettingValue
import com.arashivision.sdk.demo.ext.instaCameraManager
import com.arashivision.sdk.demo.ext.setCaptureSettingValue
import com.arashivision.sdk.demo.ui.capture.getCaptureModeTextResId
import com.arashivision.sdk.demo.ui.capture.getCaptureSettingNameResId
import com.arashivision.sdk.demo.ui.capture.getCaptureSettingValueName
import com.arashivision.sdk.demo.util.LocationManager
import com.arashivision.sdk.demo.view.picker.PickData
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.model.CaptureSetting

class ShotActivity : BaseActivity<ActivityShotBinding, ShotViewModel>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocationManager.registerLocation(this)
    }

    override fun initView() {
        super.initView()
        binding.btnWork.text = getString(R.string.shot_capture_start)

        binding.tvCaptureMode.text = getCaptureModeTextResId(viewModel.currentCaptureMode)?.let { id -> getString(id) }

    }

    override fun initListener() {
        super.initListener()
        binding.tvSwitchDualSensor.setOnClickListener {
            viewModel.switchPanoramaSensorMode()
        }

        binding.tvSelectCaptureMode.setOnClickListener {
            showCaptureModePopupMenu()
        }

        binding.btnWork.setOnClickListener {
            if (viewModel.isCameraWorking) {
                viewModel.stopWork()
            } else {
                viewModel.startWork()
            }
        }

        binding.tvCaptureSetting.setOnClickListener { showCaptureSettingView() }

        binding.pickCaptureSetting.setOnItemClickListener { position, data ->
            val supportCaptureSettingList: List<CaptureSetting> = instaCameraManager.getSupportCaptureSettingList(viewModel.currentCaptureMode)
            val captureSetting: CaptureSetting = supportCaptureSettingList[position]
            setCaptureSettingValue(viewModel.currentCaptureMode, captureSetting, data) {
                binding.pickCaptureSetting.setData(viewModel.supportCaptureSettings.map { getCaptureSettingData(it) })
            }
            binding.pickCaptureSetting.setData(viewModel.supportCaptureSettings.map { getCaptureSettingData(it) })
        }
    }

    override fun onEvent(event: BaseEvent) {
        super.onEvent(event)
        when (event) {
            is BaseEvent.CameraStatusChangedEvent -> if (!event.enable) finish()

            // 无SD卡
            ShotEvent.SDCardDisableEvent -> toast(R.string.toast_no_sd)

            // 切换镜头事件
            is ShotEvent.SwitchPanoramaSensorModeEvent -> {
                when (event.status) {
                    EventStatus.START -> showLoading(R.string.shot_switching_panorama_sensor_mode)
                    EventStatus.SUCCESS -> {
                        hideLoading()
                        toast(R.string.shot_switch_panorama_sensor_mode_success)
                    }

                    EventStatus.FAILED -> {
                        hideLoading()
                        toast(R.string.shot_switch_panorama_sensor_mode_failed)
                    }

                    else -> {}
                }
            }

            // 准备拍摄
            ShotEvent.CaptureStartingEvent -> {
                showLoading(R.string.shot_capture_starting)
                binding.btnWork.isEnabled = false
                binding.tvCaptureFilePath.visibility = View.GONE
            }

            // 相机工作
            is ShotEvent.CaptureWorkingEvent -> {
                if (!event.isSingleClickAction) {
                    hideLoading()
                    binding.btnWork.isEnabled = true
                    binding.btnWork.text = getString(R.string.shot_capture_stop)
                } else {
                    showLoading(R.string.shot_capture_working)
                }
            }

            // 相机停止工作中
            ShotEvent.CaptureStoppingEvent -> {
                showLoading(R.string.shot_capture_stopping)
            }

            is ShotEvent.CaptureErrorEvent -> {
                hideLoading()
                toast(getString(R.string.shot_capture_failed, event.code))
                binding.btnWork.isEnabled = true
                binding.btnWork.text = getString(R.string.shot_capture_start)
            }

            // 录制时长
            is ShotEvent.CaptureTimeChangedEvent -> {
                binding.tvCaptureTimeOrCount.visibility = View.VISIBLE
                binding.tvCaptureTimeOrCount.text =
                    getString(R.string.shot_capture_time, event.time.durationFormat())
            }

            // 拍摄数量
            is ShotEvent.CaptureCountChangedEvent -> {
                binding.tvCaptureTimeOrCount.visibility = View.VISIBLE
                binding.tvCaptureTimeOrCount.text =
                    getString(R.string.shot_capture_count, event.count)
            }

            // 拍摄完成
            is ShotEvent.CaptureFinishEvent -> {
                hideLoading()
                binding.tvCaptureTimeOrCount.visibility = View.INVISIBLE
                binding.btnWork.isEnabled = true
                binding.btnWork.text = getString(R.string.shot_capture_start)
                var pathText = ""
                event.paths.forEachIndexed { index, s ->
                    if (index > 0) pathText = "$pathText\n"
                    pathText = "$pathText$s"
                }
                binding.tvCaptureFilePath.visibility = View.VISIBLE
                binding.tvCaptureFilePath.text = pathText
            }
        }
    }

    private fun showCaptureModePopupMenu() {
        val popup = PopupMenu(this, binding.tvSelectCaptureMode)

        val texts: List<String> = viewModel.captureModeList.mapNotNull {
            getCaptureModeTextResId(it)?.let { id -> getString(id) }
        }

        texts.forEachIndexed { index, s ->
            popup.menu.add(0, index, index, s)
        }

        popup.setOnMenuItemClickListener { item ->
            viewModel.captureModeList.getOrNull(item.itemId)?.let {
                viewModel.switchCaptureMode(it)
                binding.tvCaptureMode.text = getCaptureModeTextResId(it)?.let { id -> getString(id) }
            }
            true
        }

        popup.show()
    }

    private fun showCaptureSettingView() {
        binding.pickCaptureSetting.setData(viewModel.supportCaptureSettings.map {
            println("showCaptureSettingView  ===>$it")
            getCaptureSettingData(it)
        })
        binding.pickCaptureSetting.setImmediateEffectiveTransparent(false)
        binding.pickCaptureSetting.show()
    }

    private fun getCaptureSettingData(captureSetting: CaptureSetting): PickData {
        val title = getString(getCaptureSettingNameResId(captureSetting))
        val captureSettingValue = getCaptureSettingValue(viewModel.currentCaptureMode, captureSetting)
        val captureSettingSupportList = getCaptureSettingSupportList(viewModel.currentCaptureMode, captureSetting)
        val index: Int = captureSettingSupportList.indexOfFirst { captureSettingValue == it }.coerceAtLeast(0)
        val options = captureSettingSupportList.map { value -> getCaptureSettingValueName(this, captureSetting, value) to value }
        return PickData(true, title, index, options)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocationManager.unregisterLocation()
    }
}