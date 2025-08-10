package com.arashivision.sdk.demo.view.picker

import android.view.LayoutInflater
import android.view.View
import com.arashivision.sdk.demo.base.BaseAdapter
import com.arashivision.sdk.demo.databinding.ItemSettingSelectBinding
import com.arashivision.sdk.demo.databinding.ItemSettingValueBinding
import com.zhy.view.flowlayout.FlowLayout
import com.zhy.view.flowlayout.TagAdapter
import com.zhy.view.flowlayout.TagFlowLayout

class PickerAdapter : BaseAdapter<ItemSettingSelectBinding, PickData>() {

    private var mOnItemSelectListener: ((position: Int, data: Any) -> Unit)? = null

    fun setOnItemSelectListener(onSecondItemSelectListener: (position: Int, data: Any) -> Unit) {
        this.mOnItemSelectListener = onSecondItemSelectListener
    }

    override fun bind(binding: ItemSettingSelectBinding, data: PickData, position: Int) {
        binding.tvName.text = data.title
        val adapter = PlayerSettingTagAdapter(data.options.map { it.first })
        binding.flowLayout.adapter = adapter
        binding.flowLayout.setOnTagClickListener { view: View?, index: Int, _: FlowLayout? ->
            mOnItemSelectListener?.invoke(position, data.options[index].second)
            view?.isSelected = true
            false
        }
        adapter.setSelectedList(data.currentPosition)
        if (data.enable) {
            enableTagItem(binding.flowLayout)
        } else {
            disableTagItem(binding.flowLayout)
        }
    }

    private fun disableTagItem(tagFlowLayout: TagFlowLayout?) {
        tagFlowLayout?.let {
            for (i in 0..<it.childCount) {
                it.getChildAt(i).isEnabled = false
                it.getChildAt(i).alpha = 0.5f
            }
        }
    }

    private fun enableTagItem(tagFlowLayout: TagFlowLayout?) {
        tagFlowLayout?.let {
            for (i in 0..<it.childCount) {
                it.getChildAt(i).isEnabled = true
                it.getChildAt(i).alpha = 1f
            }
        }
    }


    internal class PlayerSettingTagAdapter(datas: List<String>) : TagAdapter<String>(datas) {

        override fun getView(parent: FlowLayout, position: Int, data: String): View {
            val bind = ItemSettingValueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            bind.tvSettingValue.text = data
            return bind.root
        }

        override fun onSelected(position: Int, view: View) {
            super.onSelected(position, view)
            view.isSelected = true
        }

        override fun unSelected(position: Int, view: View) {
            super.unSelected(position, view)
            view.isSelected = false
        }
    }
}
