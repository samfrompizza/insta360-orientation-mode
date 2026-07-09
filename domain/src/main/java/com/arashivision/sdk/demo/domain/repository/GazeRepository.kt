package com.arashivision.sdk.demo.domain.repository

import com.arashivision.sdk.demo.core.math.Quaternion

interface GazeRepository {
    fun getCurrentOrientation(): Quaternion

    fun startTracking()

    fun stopTracking()

    fun recenter()
}
