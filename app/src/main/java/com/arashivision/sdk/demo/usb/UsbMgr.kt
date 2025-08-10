package com.arashivision.sdk.demo.usb

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat

@SuppressLint("StaticFieldLeak")
object UsbMgr {
    private var usbManager: UsbManager? = null

    private const val ACTION_USB_PERMISSION = "com.example.USB_PERMISSION"

    private var context: Context? = null

    private val usbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                // 设备插入（未获得权限时触发）
                println("设备插入")
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (device != null) {
                    requestUsbPermission(device)
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (device != null) {
                    println("设备拔出")
                }
            } else if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            // 已获得权限，处理设备
                            handleUsbDevice(device)
                        }
                    } else {
                        println("USB 权限被拒绝")
                    }
                }
            }
        }
    }

    fun init(context: Context) {
        this.context = context
        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        // 注册广播接收器
        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        ContextCompat.registerReceiver(
            context,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        checkConnectedUsbDevices()
    }

    // 检查当前已连接的 USB 设备
    private fun checkConnectedUsbDevices() {
        val deviceList = usbManager!!.deviceList
        val deviceIterator: Iterator<UsbDevice> = deviceList.values.iterator()

        if (deviceList.isEmpty()) {
            return
        }

        // 遍历所有已连接的设备
        while (deviceIterator.hasNext()) {
            val device = deviceIterator.next()
            requestUsbPermission(device)
        }
    }

    private fun requestUsbPermission(device: UsbDevice) {
        if (usbManager!!.hasPermission(device)) {
            handleUsbDevice(device)
        } else {
            // 创建 PendingIntent 用于接收权限响应
            val permissionIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE
            )
            usbManager!!.requestPermission(device, permissionIntent)
        }
    }

    // 处理 USB 设备（示例：打印设备信息）
    private fun handleUsbDevice(device: UsbDevice) {
        val info = StringBuilder()
        info.append("设备名称: ").append(device.deviceName).append("\n")
        info.append("厂商 ID: ").append(device.vendorId).append("\n")
        info.append("产品 ID: ").append(device.productId).append("\n")
        info.append("接口数量: ").append(device.interfaceCount)

        // 这里可以添加与 USB 设备通信的逻辑
        // 例如：使用 UsbDeviceConnection 和 UsbInterface 进行数据传输
    }

}
