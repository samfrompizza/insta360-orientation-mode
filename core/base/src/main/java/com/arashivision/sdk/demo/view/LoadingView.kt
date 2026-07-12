package com.arashivision.sdk.demo.view

import android.view.View
import android.view.animation.AnimationSet
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.TextView
import com.arashivision.sdk.demo.base.BaseDialog
import com.arashivision.sdk.demo.base.R as BaseR

class LoadingView(
    private var message: String = "",
) : BaseDialog() {
    fun getMessage(): String = message

    fun setMessage(message: String) {
        this.message = message
        (mView?.findViewById<View>(BaseR.id.tv_message) as TextView).text = message
    }

    public override fun layoutResId(): Int = BaseR.layout.layout_loading

    override fun initView(view: View) {
    }

    public override fun initListener() {
    }

    private fun loading() {
        if (mView != null && mView!!.findViewById<View?>(BaseR.id.iv_loading) != null) {
            val animationSet = AnimationSet(true)
            val rotate =
                RotateAnimation(
                    0.0f,
                    359.0f,
                    RotateAnimation.RELATIVE_TO_SELF,
                    0.5f,
                    RotateAnimation.RELATIVE_TO_SELF,
                    0.5f,
                )
            rotate.repeatCount = -1
            rotate.startOffset = 0
            rotate.duration = 800
            animationSet.interpolator = LinearInterpolator()
            animationSet.addAnimation(rotate)
            mView?.findViewById<View>(BaseR.id.iv_loading)?.clearAnimation()
            mView?.findViewById<View>(BaseR.id.iv_loading)?.startAnimation(animationSet)
        }
    }

    override fun onResume() {
        super.onResume()
        loading()
        mView?.findViewById<View?>(BaseR.id.tv_message)?.let {
            (it as TextView).text = message
        }
        dialog?.setCancelable(false)
    }

    override fun onPause() {
        super.onPause()
        mView?.findViewById<View>(BaseR.id.iv_loading)?.clearAnimation()
    }
}
