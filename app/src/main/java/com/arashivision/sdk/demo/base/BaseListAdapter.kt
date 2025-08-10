package com.arashivision.sdk.demo.base

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.arashivision.sdk.demo.util.ViewBindingUtils.createBinding

@SuppressLint("NotifyDataSetChanged")
abstract class BaseListAdapter<T : ViewBinding, K> : BaseAdapter() {

    private var itemClickListener: ((view: View, data: K, position: Int) -> Unit)? = null

    private var itemLongClickListener: ((view: View, data: K, position: Int) -> Unit)? = null

    private var dataList: MutableList<K> = ArrayList()

    val data: List<K>
        get() = dataList

    fun setData(newDataList: MutableList<K>) {
        dataList = newDataList
        notifyDataSetChanged()
    }

    fun addData(data: K) {
        dataList.add(data)
        notifyDataSetChanged()
    }

    fun addData(index: Int, data: K) {
        dataList.add(index, data)
        notifyDataSetChanged()
    }

    fun remove(data: K) {
        dataList.remove(data)
        notifyDataSetChanged()
    }

    fun clear() {
        dataList.clear()
        notifyDataSetChanged()
    }

    fun setItemClickListener(itemClickListener: (view: View, data: K, position: Int) -> Unit) {
        this.itemClickListener = itemClickListener
    }

    fun setItemLongClickListener(itemLongClickListener: (view: View, data: K, position: Int) -> Unit) {
        this.itemLongClickListener = itemLongClickListener
    }

    @Suppress("UNCHECKED_CAST")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val viewHolder: ViewHolder<T> = convertView?.tag as? ViewHolder<T> ?: run {
            val binding = createBinding<T>(this.javaClass, LayoutInflater.from(parent.context), 0, parent)
            val holder = ViewHolder(binding)
            binding.root.tag = holder
            holder
        }
        bind(viewHolder.binding, dataList[position], position)
        viewHolder.binding.root.setOnClickListener {
            itemClickListener?.invoke(it, dataList[position], position)
        }

        viewHolder.binding.root.setOnLongClickListener {
            itemLongClickListener?.invoke(it, dataList[position], position)
            false
        }
        return viewHolder.binding.root
    }


    protected abstract fun bind(binding: T, data: K, position: Int)

    override fun getCount(): Int {
        return dataList.size
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItem(position: Int): Any {
        return position
    }

    class ViewHolder<V : ViewBinding>(var binding: V) : RecyclerView.ViewHolder(binding.root)
}
