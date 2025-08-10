package com.arashivision.sdk.demo.view

import android.view.View
import android.view.animation.AnimationSet
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.TextView
import com.arashivision.sdk.demo.R
import com.arashivision.sdk.demo.base.BaseDialog

class LoadingView(private var message: String = "") : BaseDialog() {

    fun getMessage(): String {
        return message
    }

    fun setMessage(message: String) {
        this.message = message
        (mView?.findViewById<View>(R.id.tv_message) as TextView).text = message
    }

    public override fun layoutResId(): Int {
        return R.layout.layout_loading
    }

    override fun initView(view: View) {
        // 空实现
    }


    public override fun initListener() {
        // 空实现
    }

    private fun loading() {
        if (mView != null && mView!!.findViewById<View?>(R.id.iv_loading) != null) {
            val animationSet = AnimationSet(true)
            val rotate = RotateAnimation(
                0.0f,
                359.0f,
                RotateAnimation.RELATIVE_TO_SELF,
                0.5f,
                RotateAnimation.RELATIVE_TO_SELF,
                0.5f
            )
            // 第一个参数fromDegrees为动画起始时的旋转角度
            // 第二个参数toDegrees为动画旋转到的角度
            // 第三个参数pivotXType为动画在X轴相对于物件位置类型
            // 第四个参数pivotXValue为动画相对于物件的X坐标的开始位置
            // 第五个参数pivotXType为动画在Y轴相对于物件位置类型
            // 第六个参数pivotYValue为动画相对于物件的Y坐标的开始位置
            rotate.repeatCount = -1
            rotate.startOffset = 0
            rotate.duration = 800
            animationSet.interpolator = LinearInterpolator()
            animationSet.addAnimation(rotate)
            mView?.findViewById<View>(R.id.iv_loading)?.clearAnimation()
            mView?.findViewById<View>(R.id.iv_loading)?.startAnimation(animationSet)
        }
    }

    override fun onResume() {
        super.onResume()
        loading()
        mView?.findViewById<View?>(R.id.tv_message)?.let {
            (it as TextView).text = message
        }
        dialog?.setCancelable(false)
    }

    override fun onPause() {
        super.onPause()
        mView?.findViewById<View>(R.id.iv_loading)?.clearAnimation()
    }
}
