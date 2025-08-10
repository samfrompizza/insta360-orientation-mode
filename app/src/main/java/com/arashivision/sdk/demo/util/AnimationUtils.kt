package com.arashivision.sdk.demo.util

import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation

object AnimationUtils {
    fun animateShowAndHide(view: View) {
        // 确保 View 可见
        view.visibility = View.VISIBLE

        // 显示阶段：透明度动画
        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.duration = 250
        fadeIn.fillAfter = true

        // 显示阶段：缩放动画
        val scaleIn = ScaleAnimation(
            0.5f,
            1f,
            0.5f,
            1f,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f
        )
        scaleIn.duration = 250
        scaleIn.fillAfter = true

        // 隐藏阶段：透明度动画
        val fadeOut = AlphaAnimation(1f, 0f)
        fadeOut.duration = 250
        fadeOut.startOffset = 250
        fadeOut.fillAfter = true

        // 隐藏阶段：缩放动画
        val scaleOut = ScaleAnimation(
            1f,
            0.5f,  // X 从 1 缩放到 0.5
            1f,
            0.5f,  // Y 从 1 缩放到 0.5
            Animation.RELATIVE_TO_SELF,
            0.5f,  // X 轴中心点
            Animation.RELATIVE_TO_SELF,
            0.5f // Y 轴中心点
        )
        scaleOut.duration = 250
        scaleOut.startOffset = 250
        scaleOut.fillAfter = true

        // 动画集合
        val animationSet = AnimationSet(true)
        animationSet.addAnimation(fadeIn)
        animationSet.addAnimation(scaleIn)
        animationSet.addAnimation(fadeOut)
        animationSet.addAnimation(scaleOut)
        animationSet.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
            }

            override fun onAnimationEnd(animation: Animation) {
                view.visibility = View.GONE
            }

            override fun onAnimationRepeat(animation: Animation) {
            }
        })
        // 启动动画
        view.startAnimation(animationSet)
    }
}
