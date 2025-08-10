package com.arashivision.sdk.demo.base

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.arashivision.sdk.demo.R
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ImmersionBar

abstract class BaseDialog(
    private var viewSite: Int = Gravity.CENTER,
    private var isOnTouchOutSide: Boolean = false,
    private var alpha: Float = 0.8f,
    private var windowWidth: Int = ViewGroup.LayoutParams.MATCH_PARENT,
    private var windowHeight: Int = ViewGroup.LayoutParams.MATCH_PARENT
) : DialogFragment() {

    protected var mView: View? = null

    @LayoutRes
    protected abstract fun layoutResId(): Int

    protected abstract fun initView(view: View)

    protected abstract fun initListener()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BaseDialogStyle)
    }

    override fun show(manager: FragmentManager, tag: String?) {
        if (manager.isDestroyed) {
            return
        }
        try {
            manager.beginTransaction().remove(this).commit()
            super.show(manager, tag)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        dialog?.setCanceledOnTouchOutside(isOnTouchOutSide)
        dialog?.setCancelable(false)
        mView = LayoutInflater.from(context).inflate(layoutResId(), container, false)
        return mView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ImmersionBar.with(this).hideBar(BarHide.FLAG_HIDE_BAR).init()
        initView(view)
        initListener()
    }

    fun setOnTouchOutSide(onTouchOutSide: Boolean): BaseDialog {
        this.isOnTouchOutSide = onTouchOutSide
        dialog?.setCanceledOnTouchOutside(onTouchOutSide)
        return this
    }

    fun setViewSite(viewSite: Int): BaseDialog {
        this.viewSite = viewSite
        setSite(viewSite)
        return this
    }

    override fun onStart() {
        super.onStart()
        setSite(viewSite)
    }

    private fun setSite(site: Int) {
        val mLayoutParams = dialog?.window?.attributes
        mLayoutParams?.width = windowWidth
        mLayoutParams?.height = windowHeight
        mLayoutParams?.gravity = site
        dialog?.window?.attributes = mLayoutParams
        dialog?.window?.setDimAmount(alpha) // 0~1 , 1表示完全昏暗
    }
}

