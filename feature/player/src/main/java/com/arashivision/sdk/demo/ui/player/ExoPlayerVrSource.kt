package com.arashivision.sdk.demo.ui.player

import android.view.View
import com.arashivision.sdk.demo.core.vr.VrSourceView

class ExoPlayerVrSource(
    private val sphericalView: View,
) : VrSourceView {
    override val contentView: View
        get() = sphericalView

    override fun onVrEnabled() {
        // No-op: sphericalView is already rendering
    }

    override fun onVrDisabled() {
        // No-op
    }

    override fun applyOrientation(
        yawDeg: Float,
        pitchDeg: Float,
    ) {
        try {
            val cls = sphericalView.javaClass
            try {
                val mYaw = cls.getMethod("setYaw", Float::class.javaPrimitiveType)
                mYaw.invoke(sphericalView, yawDeg)
            } catch (_: NoSuchMethodException) {
            }
            try {
                val mPitch = cls.getMethod("setPitch", Float::class.javaPrimitiveType)
                mPitch.invoke(sphericalView, pitchDeg)
            } catch (_: NoSuchMethodException) {
            }
        } catch (_: Exception) {
        }
    }
}
