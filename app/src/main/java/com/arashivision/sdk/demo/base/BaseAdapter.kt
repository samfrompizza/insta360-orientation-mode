package com.arashivision.sdk.demo.base

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.arashivision.sdk.demo.util.ViewBindingUtils
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog

@SuppressLint("NotifyDataSetChanged")
abstract class BaseAdapter<T : ViewBinding, K> : RecyclerView.Adapter<BaseAdapter.ViewHolder<T>>() {

    private val logger: Logger = XLog.tag(BaseAdapter::class.java.simpleName).build()

    private var itemClickListener: ((data: K, position: Int) -> Unit)? = null

    private var itemLongClickListener: ((data: K, position: Int) -> Unit)? = null

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

    fun setItemClickListener(listener: (data: K, position: Int) -> Unit) {
        this.itemClickListener = listener
    }

    fun setItemLongClickListener(listener: (data: K, position: Int) -> Unit) {
        this.itemLongClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<T> {
        val binding = ViewBindingUtils.createBinding<T>(this.javaClass, LayoutInflater.from(parent.context), 0, parent)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder<T>, position: Int) {
        bind(holder.binding, dataList[position], position)
        holder.binding.root.setOnClickListener {
            itemClickListener?.invoke(dataList[position], position)
        }
        holder.binding.root.setOnLongClickListener {
            itemLongClickListener?.invoke(dataList[position], position)
            false
        }
    }

    protected abstract fun bind(binding: T, data: K, position: Int)

    override fun getItemCount(): Int = dataList.size


    class ViewHolder<V : ViewBinding>(var binding: V) : RecyclerView.ViewHolder(binding.root)

}
