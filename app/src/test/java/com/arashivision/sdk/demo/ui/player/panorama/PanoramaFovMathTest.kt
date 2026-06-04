package com.arashivision.sdk.demo.ui.player.panorama

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanoramaFovMathTest {

    @Test
    fun targetAtGazeDirectionIsInsideFovAndHasNoArrow() {
        val state = PanoramaFovMath.resolveTarget(
            gaze = direction(yaw = 0.0, pitch = 0.0),
            target = direction(yaw = 0.0, pitch = 0.0),
            horizontalFovRad = HALF_PI,
            verticalFovRad = HALF_PI
        )

        assertTrue(state.isInsideFov)
        assertEquals(0.0, state.yawDeltaRad, EPSILON)
        assertEquals(0.0, state.pitchDeltaRad, EPSILON)
        assertNull(state.arrowAngleRad)
    }

    @Test
    fun targetOutsideRightSidePointsArrowRight() {
        val state = PanoramaFovMath.resolveTarget(
            gaze = direction(yaw = 0.0, pitch = 0.0),
            target = direction(yaw = HALF_PI, pitch = 0.0),
            horizontalFovRad = HALF_PI,
            verticalFovRad = HALF_PI
        )

        assertFalse(state.isInsideFov)
        assertEquals(HALF_PI, state.yawDeltaRad, EPSILON)
        assertEquals(0.0, state.arrowAngleRad ?: Double.NaN, EPSILON)
    }

    @Test
    fun targetOutsideAbovePointsArrowUp() {
        val state = PanoramaFovMath.resolveTarget(
            gaze = direction(yaw = 0.0, pitch = 0.0),
            target = direction(yaw = 0.0, pitch = HALF_PI),
            horizontalFovRad = HALF_PI,
            verticalFovRad = HALF_PI
        )

        assertFalse(state.isInsideFov)
        assertEquals(-HALF_PI, state.arrowAngleRad ?: Double.NaN, EPSILON)
    }

    @Test
    fun yawDeltaWrapsAcrossPanoramaSeam() {
        val state = PanoramaFovMath.resolveTarget(
            gaze = direction(yaw = Math.toRadians(170.0), pitch = 0.0),
            target = direction(yaw = Math.toRadians(-170.0), pitch = 0.0),
            horizontalFovRad = Math.toRadians(60.0),
            verticalFovRad = HALF_PI
        )

        assertTrue(state.isInsideFov)
        assertEquals(Math.toRadians(20.0), state.yawDeltaRad, EPSILON)
    }

    private fun direction(yaw: Double, pitch: Double): PanoramaDirection =
        EquirectangularProjection.fromYawPitch(yaw, pitch)

    private companion object {
        const val EPSILON = 1e-9
        const val HALF_PI = PI / 2.0
    }
}
