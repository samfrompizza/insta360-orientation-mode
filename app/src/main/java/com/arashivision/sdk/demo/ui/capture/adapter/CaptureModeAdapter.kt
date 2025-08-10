package com.arashivision.sdk.demo.ui.capture.adapter

import com.arashivision.sdk.demo.base.BaseAdapter
import com.arashivision.sdk.demo.databinding.ItemCaptureModeBinding

class CaptureModeAdapter : BaseAdapter<ItemCaptureModeBinding, String>() {

    override fun bind(binding: ItemCaptureModeBinding, data: String, position: Int) {
        binding.tvName.text = data
    }
}
