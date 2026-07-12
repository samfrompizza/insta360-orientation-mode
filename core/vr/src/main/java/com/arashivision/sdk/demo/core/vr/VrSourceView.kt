package com.arashivision.sdk.demo.core.vr

import android.view.View

interface VrSourceView {
    val contentView: View

    fun onVrEnabled()

    fun onVrDisabled()

    fun applyOrientation(
        yawDeg: Float,
        pitchDeg: Float,
    )
}
