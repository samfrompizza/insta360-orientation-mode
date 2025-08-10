package com.arashivision.sdk.demo.ui.ability

import android.content.Intent
import com.arashivision.sdk.demo.R
import com.arashivision.sdk.demo.base.BaseEvent
import com.arashivision.sdk.demo.base.BaseFragment
import com.arashivision.sdk.demo.base.EventStatus.FAILED
import com.arashivision.sdk.demo.base.EventStatus.START
import com.arashivision.sdk.demo.base.EventStatus.SUCCESS
import com.arashivision.sdk.demo.databinding.FragmentAbilityBinding
import com.arashivision.sdk.demo.ext.instaCameraManager
import com.arashivision.sdk.demo.ext.isPrimaryConnect
import com.arashivision.sdk.demo.ui.shot.ShotActivity
import com.arashivision.sdk.demo.ui.upgrade.FwUpgradeActivity
import com.arashivision.sdkcamera.camera.InstaCameraManager

class AbilityFragment : BaseFragment<FragmentAbilityBinding, AbilityViewModel>() {

    override fun initView() {
        super.initView()
        binding.tvCameraBeep.text = getString(R.string.ability_camera_beep, getString(R.string.off))
        updateUi(
            instaCameraManager.cameraConnectedType != InstaCameraManager.CONNECT_TYPE_NONE,
            instaCameraManager.cameraConnectedType
        )
    }

    override fun initListener() {
        super.initListener()

        binding.tvCameraBeep.setOnClickListener {
            instaCameraManager.setCameraBeepSwitch(!instaCameraManager.isCameraBeep)
            val beep = getString(if (instaCameraManager.isCameraBeep) R.string.on else R.string.off)
            binding.tvCameraBeep.text = getString(R.string.ability_camera_beep, beep)
        }

        binding.tvFormatSd.setOnClickListener {
            viewModel.formatSdCard()
        }

        binding.tvShutdown.setOnClickListener {
            viewModel.shutdownCamera()
        }

        binding.tvFirmwareUpgrade.setOnClickListener {
            activity?.let { startActivity(Intent(it, FwUpgradeActivity::class.java)) }
        }

        binding.tvCapture.setOnClickListener {
            activity?.let { startActivity(Intent(it, ShotActivity::class.java)) }
        }

        binding.tvActiveCamera.setOnClickListener {
            viewModel.activeCamera()
        }
    }

    override fun onEvent(event: BaseEvent?) {
        super.onEvent(event)
        when (event) {
            is BaseEvent.CameraStatusChangedEvent -> updateUi(event.enable, event.connectType)
            is AbilityEvent.FormatSdCardEvent -> {
                when (event.status) {
                    START -> showLoading(R.string.ability_formatting_sd_card)
                    SUCCESS -> {
                        hideLoading()
                        toast(R.string.ability_format_sd_card_success)
                    }

                    FAILED -> {
                        hideLoading()
                        toast(R.string.ability_format_sd_card_failed)
                    }

                    else -> {}
                }
            }

            is AbilityEvent.ActiveCameraEvent -> {
                when (event.status) {
                    START -> showLoading(R.string.ability_activating_camera)
                    SUCCESS -> {
                        hideLoading()
                        toast(R.string.ability_active_camera_success)
                    }

                    FAILED -> {
                        hideLoading()
                        toast(getString(R.string.ability_active_camera_failed, event.error))
                    }

                    else -> {}
                }
            }
        }
    }

    private fun updateUi(enable: Boolean, connectType: Int) {
        binding.tvCapture.isEnabled = enable
        binding.tvShutdown.isEnabled = enable
        binding.tvFirmwareUpgrade.isEnabled = enable && isPrimaryConnect(connectType)
        binding.tvCameraBeep.isEnabled = enable
        binding.tvFormatSd.isEnabled = enable
        binding.tvActiveCamera.isEnabled = enable

        if (enable) {
            val beep = getString(if (instaCameraManager.isCameraBeep) R.string.on else R.string.off)
            binding.tvCameraBeep.text = getString(R.string.ability_camera_beep, beep)
        }
    }

}
