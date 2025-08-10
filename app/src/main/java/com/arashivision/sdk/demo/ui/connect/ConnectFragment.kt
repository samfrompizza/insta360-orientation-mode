package com.arashivision.sdk.demo.ui.connect

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arashivision.sdk.demo.R
import com.arashivision.sdk.demo.base.BaseEvent
import com.arashivision.sdk.demo.base.BaseFragment
import com.arashivision.sdk.demo.base.EventStatus
import com.arashivision.sdk.demo.databinding.FragmentConnectBinding
import com.arashivision.sdk.demo.ext.durationFormat
import com.arashivision.sdk.demo.ext.gb
import com.arashivision.sdk.demo.ext.instaCameraManager
import com.arashivision.sdk.demo.ext.isPrimaryConnect
import com.arashivision.sdk.demo.ext.timeFormat
import com.arashivision.sdk.demo.ui.capture.CaptureActivity
import com.arashivision.sdk.demo.ui.connect.adapter.BleDeviceAdapter
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog


class ConnectFragment : BaseFragment<FragmentConnectBinding, ConnectViewModel>() {

    private val logger: Logger = XLog.tag(ConnectFragment::class.java.simpleName).build()

    private var bleDeviceAdapter: BleDeviceAdapter? = null

    override fun initView() {
        super.initView()
        bleDeviceAdapter = BleDeviceAdapter()
        binding.rvBleDevices.setAdapter(bleDeviceAdapter)
        binding.rvBleDevices.setLayoutManager(LinearLayoutManager(context, RecyclerView.VERTICAL, false))
    }

    override fun initListener() {
        super.initListener()
        this.binding.tvConnectBle.setOnClickListener {
            // 开始扫描  15秒之后主动结束
            if (!viewModel.isConnected) {
                viewModel.startBleScan()
            } else {
                viewModel.disconnectCamera()
            }
        }

        this.binding.tvConnectWifi.setOnClickListener {
            if (!viewModel.isConnected) {
                viewModel.connectDeviceByWiFi()
            } else {
                viewModel.disconnectCamera()
            }
        }

        this.binding.tvConnectUsb.setOnClickListener {
            if (!viewModel.isConnected) {
                viewModel.connectDeviceByUsb()
            } else {
                viewModel.disconnectCamera()
            }
        }

        bleDeviceAdapter?.setOnConnectWiFiClickListener { data, _ ->
            viewModel.connectDeviceByBle(data, false)
        }

        bleDeviceAdapter?.setOnConnectBleClickListener { data, _ ->
            viewModel.connectDeviceByBle(data, true)
        }

        binding.tvEnterCapture.setOnClickListener {
            startActivity(Intent(activity, CaptureActivity::class.java))
        }

        binding.tvRefreshMediaTime.setOnClickListener {
            viewModel.refreshMediaTime()
        }
    }

    override fun onEvent(event: BaseEvent?) {
        super.onEvent(event)
        when (event) {
            // 设备蓝牙扫描事件
            is ConnectEvent.ScanDeviceEvent -> {
                logger.d("      status ==>" + event.status)
                when (event.status) {
                    EventStatus.START -> {
                        bleDeviceAdapter?.clear()
                        binding.tvConnectBle.setText(R.string.button_scanning_device_by_ble)
                        binding.tvConnectBle.isEnabled = false
                        binding.rvBleDevices.visibility = View.VISIBLE
                        binding.tvConnectWifi.isEnabled = false
                        binding.tvConnectUsb.isEnabled = false
                    }

                    EventStatus.SUCCESS -> {
                        binding.tvConnectBle.setText(R.string.button_scan_device_by_ble)
                        binding.tvConnectBle.isEnabled = true
                        binding.tvConnectWifi.isEnabled = true
                        binding.tvConnectUsb.isEnabled = true
                    }

                    EventStatus.PROGRESS -> event.bleDevice?.let { bleDeviceAdapter?.addData(it) }
                    EventStatus.FAILED -> {
                        toast(R.string.toast_ble_scan_failed)
                        binding.tvConnectBle.setText(R.string.button_scan_device_by_ble)
                        binding.rvBleDevices.visibility = View.GONE
                        binding.tvConnectBle.isEnabled = true
                        binding.tvConnectWifi.isEnabled = true
                        binding.tvConnectUsb.isEnabled = true
                    }
                }
            }

            // 设备连接事件
            is ConnectEvent.ConnectDeviceEvent -> {
                when (event.status) {
                    EventStatus.START -> when (event.connectType) {
                        InstaCameraManager.CONNECT_TYPE_WIFI -> showLoading(getString(R.string.loading_connecting_wifi))
                        InstaCameraManager.CONNECT_TYPE_USB -> showLoading(getString(R.string.loading_connecting_usb))
                        InstaCameraManager.CONNECT_TYPE_BLE -> showLoading(getString(R.string.loading_connecting_ble))
                    }

                    EventStatus.SUCCESS -> {
                        if (event.connectType == InstaCameraManager.CONNECT_TYPE_BLE) {
                            toast(R.string.toast_camera_ble_connected)
                            // 蓝牙连接只是中间状态，最终的目的是连接上Wi-Fi,因此return
                            if (!viewModel.onlyConnectBle) return
                        }

                        // 连接上Wi-Fi或者USB，才算最终连接成功
                        hideLoading()
                        when (event.connectType) {
                            InstaCameraManager.CONNECT_TYPE_WIFI -> {
                                binding.tvCameraConnectStatus.text = getString(R.string.text_camera_connected, "(Wi-Fi)")
                                binding.tvConnectBle.setEnabled(false)
                                binding.tvConnectUsb.setEnabled(false)
                                binding.tvConnectWifi.setText(R.string.button_disconnect)
                            }

                            InstaCameraManager.CONNECT_TYPE_USB -> {
                                binding.tvCameraConnectStatus.text = getString(R.string.text_camera_connected, "(USB)")
                                binding.tvConnectBle.setEnabled(false)
                                binding.tvConnectWifi.setEnabled(false)
                                binding.tvConnectUsb.setText(R.string.button_disconnect)
                            }

                            InstaCameraManager.CONNECT_TYPE_BLE -> {
                                binding.tvCameraConnectStatus.text = getString(R.string.text_camera_connected, "(BLE)")
                                binding.tvConnectUsb.setEnabled(false)
                                binding.tvConnectWifi.setEnabled(false)
                                binding.tvConnectBle.setText(R.string.button_disconnect)
                            }
                        }
                        binding.tvCameraConnectStatus.setTextColor(Color.GREEN)
                        binding.rvBleDevices.visibility = View.GONE
                        binding.llCameraInfo.visibility = View.VISIBLE
                        updateCameraInfoUi(event.connectType)
                        toast(R.string.toast_camera_connected)
                    }

                    EventStatus.FAILED -> {
                        hideLoading()
                        toast(getString(
                                when (event.connectType) {
                                    InstaCameraManager.CONNECT_TYPE_BLE -> R.string.toast_ble_connect_failed
                                    InstaCameraManager.CONNECT_TYPE_USB -> R.string.toast_usb_connect_failed
                                    else -> R.string.toast_wifi_connect_failed
                                },
                                event.errorCode
                            )
                        )
                    }

                    else -> {}
                }
            }

            // 相机断连事件
            ConnectEvent.CameraDisconnectedEvent -> {
                binding.tvCameraConnectStatus.text = getString(R.string.text_camera_disconnected)
                binding.tvCameraConnectStatus.setTextColor(Color.RED)
                binding.llCameraInfo.visibility = View.GONE
                binding.tvConnectBle.setText(R.string.button_scan_device_by_ble)
                binding.tvConnectBle.isEnabled = true
                binding.tvConnectWifi.setText(R.string.button_connect_camera_wifi)
                binding.tvConnectWifi.isEnabled = true
                binding.tvConnectUsb.setText(R.string.button_connect_camera_usb)
                binding.tvConnectUsb.isEnabled = true
            }

            // 相机电量更新事件
            is BaseEvent.CameraBatteryUpdateEvent -> {
                batteryUpdate(event.batteryLevel, event.isCharging)
            }

            // 相机SD卡插拔事件
            is BaseEvent.CameraSDCardStateChangedEvent -> {
                sdCardUpdate(event.enabled)
            }

            // 刷新media time
            is ConnectEvent.RefreshMediaTimeEvent -> {
                when (event.status) {
                    EventStatus.START -> showLoading()
                    EventStatus.SUCCESS -> {
                        hideLoading()
                        binding.tvCameraMediaTimeValue.text = event.mediaTime.durationFormat()
                    }
                    EventStatus.FAILED -> {
                        hideLoading()
                        toast(R.string.refresh_failed)
                    }

                    else -> {}
                }
            }
        }
    }

    private fun updateCameraInfoUi(connectType: Int) {
        binding.tvCameraModelValue.text = instaCameraManager.cameraType
        binding.tvCameraSerialValue.text = instaCameraManager.cameraSerial
        binding.tvCameraVersionValue.text = instaCameraManager.cameraVersion
        binding.tvCameraMediaTimeValue.text = instaCameraManager.mediaTime.durationFormat()

        binding.tvCameraActiveValue.text = instaCameraManager.activeTime.timeFormat()

        binding.tvEnterCapture.isEnabled = isPrimaryConnect(connectType)

        val level = instaCameraManager.cameraCurrentBatteryLevel
        val isCharging = instaCameraManager.isCameraCharging
        batteryUpdate(level, isCharging)

        val enabled: Boolean = instaCameraManager.isSdCardEnabled
        sdCardUpdate(enabled)
    }

    @SuppressLint("SetTextI18n")
    private fun batteryUpdate(batteryLevel: Int, isCharging: Boolean) {
        binding.tvCameraBatteryLevelValue.text = "$batteryLevel%"
        if (isCharging) {
            binding.tvCameraChargeStatusValue.setText(R.string.text_camera_charging)
        } else {
            binding.tvCameraChargeStatusValue.setText(R.string.text_camera_uncharged)
        }
    }

    private fun sdCardUpdate(enabled: Boolean) {
        if (enabled) {
            binding.tvCameraSdStatusValue.setText(R.string.text_camera_sd_status_enable)
            binding.tvCameraSdTotalValue.text = instaCameraManager.cameraStorageTotalSpace.gb()
            binding.tvCameraSdFreeValue.text = instaCameraManager.cameraStorageFreeSpace.gb()
        } else {
            binding.tvCameraSdStatusValue.setText(R.string.text_camera_sd_status_disable)
            binding.tvCameraSdTotalValue.text = "-"
            binding.tvCameraSdFreeValue.text = "-"
        }
    }
}
