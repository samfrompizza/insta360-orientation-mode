package com.arashivision.sdk.demo.ui.upgrade

import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import arte.programar.materialfile.MaterialFilePicker
import arte.programar.materialfile.ui.FilePickerActivity
import com.arashivision.sdk.demo.R
import com.arashivision.sdk.demo.base.BaseActivity
import com.arashivision.sdk.demo.base.BaseEvent
import com.arashivision.sdk.demo.databinding.ActivityFwUpgradeBinding
import com.arashivision.sdkcamera.upgrade.FwUpgradeListener
import com.arashivision.sdkcamera.upgrade.FwUpgradeManager
import java.io.File
import java.util.Locale
import java.util.regex.Pattern

class FwUpgradeActivity : BaseActivity<ActivityFwUpgradeBinding, FwUpgradeViewModel>(), FwUpgradeListener {

    private var mFwFile: File? = null

    private val startForResultFiles = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            activityResult.data?.getStringExtra(FilePickerActivity.RESULT_FILE_PATH)?.let {
                mFwFile = File(it)
                binding.tvFilePath.text = getString(R.string.ability_fw_upgrade_file_path, it)
            }
        }
    }

    override fun initView() {
        super.initView()
        binding.tvFilePath.text = getString(R.string.ability_fw_upgrade_file_path, "")
        binding.btnUpgrade.setChecked(false)
    }

    override fun initListener() {
        super.initListener()
        binding.btnUpgrade.setOnClickListener {
            if (binding.btnUpgrade.isChecked) {
                FwUpgradeManager.getInstance().cancelUpgrade()
                return@setOnClickListener
            }
            mFwFile?.let {
                if (!it.exists()) {
                    binding.btnUpgrade.setChecked(false)
                    binding.tvFilePath.text = getString(R.string.ability_fw_upgrade_file_path, "")
                } else {
                    FwUpgradeManager.getInstance().startUpgrade(it.absolutePath, this)
                }
            }
        }

        binding.tvChooseFile.setOnClickListener { openFilePicker() }
    }

    private fun openFilePicker() {
        MaterialFilePicker()
            .withActivity(this)
            .withCloseMenu(true)
            .withHiddenFiles(true)
            .withFilter(Pattern.compile(".*\\.(bin|pkg)$"))
            .withTitle(getString(R.string.ability_fw_upgrade_choose_file))
            .withFilterDirectories(false)
            .withActivityResultApi(startForResultFiles)
            .start()
    }

    override fun onUpgradeSuccess() {
        binding.tvState.setText(R.string.ability_fw_upgrade_success)
        binding.btnUpgrade.setChecked(false)
    }

    override fun onUpgradeFail(errorCode: Int, message: String?) {
        binding.tvState.text = getString(R.string.ability_fw_upgrade_fail, errorCode, message)
        binding.btnUpgrade.setChecked(false)
    }

    override fun onUpgradeCancel() {
        binding.tvState.setText(R.string.ability_fw_upgrade_cancel)
        binding.btnUpgrade.setChecked(false)
    }

    override fun onUpgradeProgress(progress: Double) {
        binding.tvState.text = getString(
            R.string.ability_fw_upgrade_progress,
            String.format(Locale.getDefault(), "%.2f%%", (progress * 100).toFloat())
        )
    }

    override fun onEvent(event: BaseEvent) {
        super.onEvent(event)
        when (event) {
            is BaseEvent.CameraStatusChangedEvent -> if (!event.enable) finish()
        }
    }
}