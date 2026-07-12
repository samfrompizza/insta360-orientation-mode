package com.arashivision.sdk.demo.ui.capture.adapter

import com.arashivision.sdk.demo.base.BaseAdapter
import com.arashivision.sdk.demo.feature.capture.databinding.ItemCaptureModeBinding

class CaptureModeAdapter :
    BaseAdapter<ItemCaptureModeBinding, String>(
        bindingFactory = { inflater, parent -> ItemCaptureModeBinding.inflate(inflater, parent, false) },
    ) {
    override fun bind(
        binding: ItemCaptureModeBinding,
        data: String,
        position: Int,
    ) {
        binding.tvName.text = data
    }
}
