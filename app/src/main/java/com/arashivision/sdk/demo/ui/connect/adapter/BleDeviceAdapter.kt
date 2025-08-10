package com.arashivision.sdk.demo.ui.connect.adapter

import com.arashivision.sdk.demo.base.BaseAdapter
import com.arashivision.sdk.demo.databinding.ItemBleDevicesBinding
import com.clj.fastble.data.BleDevice

class BleDeviceAdapter : BaseAdapter<ItemBleDevicesBinding, BleDevice>() {

    private var connectBleClickListener: ((data: BleDevice, position: Int) -> Unit)? = null
    private var connectWiFiClickListener: ((data: BleDevice, position: Int) -> Unit)? = null
    override fun bind(binding: ItemBleDevicesBinding, data: BleDevice, position: Int) {
        binding.tvName.text = data.name
        binding.tvConnectBle.setOnClickListener {
            connectBleClickListener?.invoke(data, position)
        }
        binding.tvConnectWifi.setOnClickListener {
            connectWiFiClickListener?.invoke(data, position)
        }
    }

    fun setOnConnectBleClickListener(listener: (data: BleDevice, position: Int) -> Unit) {
        connectBleClickListener = listener
    }

    fun setOnConnectWiFiClickListener(listener: (data: BleDevice, position: Int) -> Unit) {
        connectWiFiClickListener = listener
    }
}
