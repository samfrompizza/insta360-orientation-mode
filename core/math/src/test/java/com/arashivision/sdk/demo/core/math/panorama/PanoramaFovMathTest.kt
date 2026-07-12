package com.arashivision.sdk.demo.core.math.panorama

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class PanoramaFovMathTest {
    @Test
    fun targetAtGazeDirectionIsInsideFovAndHasNoArrow() {
        val state =
            PanoramaFovMath.resolveTarget(
                gaze = direction(yaw = 0.0, pitch = 0.0),
                target = direction(yaw = 0.0, pitch = 0.0),
                horizontalFovRad = HALF_PI,
                verticalFovRad = HALF_PI,
            )

        assertTrue(state.isInsideFov)
        assertEquals(0.0, state.yawDeltaRad, EPSILON)
        assertEquals(0.0, state.pitchDeltaRad, EPSILON)
        assertNull(state.arrowAngleRad)
    }

    @Test
    fun targetOutsideRightSidePointsArrowRight() {
        val state =
            PanoramaFovMath.resolveTarget(
                gaze = direction(yaw = 0.0, pitch = 0.0),
                target = direction(yaw = HALF_PI, pitch = 0.0),
                horizontalFovRad = HALF_PI,
                verticalFovRad = HALF_PI,
            )

        assertFalse(state.isInsideFov)
        assertEquals(HALF_PI, state.yawDeltaRad, EPSILON)
        assertEquals(0.0, state.arrowAngleRad ?: Double.NaN, EPSILON)
    }

    @Test
    fun targetOutsideAbovePointsArrowUp() {
        val state =
            PanoramaFovMath.resolveTarget(
                gaze = direction(yaw = 0.0, pitch = 0.0),
                target = direction(yaw = 0.0, pitch = HALF_PI),
                horizontalFovRad = HALF_PI,
                verticalFovRad = HALF_PI,
            )

        assertFalse(state.isInsideFov)
        assertEquals(-HALF_PI, state.arrowAngleRad ?: Double.NaN, EPSILON)
    }

    @Test
    fun yawDeltaWrapsAcrossPanoramaSeam() {
        val state =
            PanoramaFovMath.resolveTarget(
                gaze = direction(yaw = Math.toRadians(170.0), pitch = 0.0),
                target = direction(yaw = Math.toRadians(-170.0), pitch = 0.0),
                horizontalFovRad = Math.toRadians(60.0),
                verticalFovRad = HALF_PI,
            )

        assertTrue(state.isInsideFov)
        assertEquals(Math.toRadians(20.0), state.yawDeltaRad, EPSILON)
    }

    @Test
    fun quat_targetAtGazeDirectionIsInsideFovAndHasNoArrow() {
        val state =
            PanoramaFovMath.resolveTargetQuat(
                gaze = direction(yaw = 0.0, pitch = 0.0),
                target = direction(yaw = 0.0, pitch = 0.0),
                horizontalFovRad = HALF_PI,
                verticalFovRad = HALF_PI,
            )

        assertTrue(state.isInsideFov)
        assertEquals(0.0, state.yawDeltaRad, EPSILON)
        assertEquals(0.0, state.pitchDeltaRad, EPSILON)
        assertNull(state.arrowAngleRad)
    }

    @Test
    fun quat_targetOutsideRightSidePointsArrowRight() {
        val state =
            PanoramaFovMath.resolveTargetQuat(
                gaze = direction(yaw = 0.0, pitch = 0.0),
                target = direction(yaw = HALF_PI, pitch = 0.0),
                horizontalFovRad = HALF_PI,
                verticalFovRad = HALF_PI,
            )

        assertFalse(state.isInsideFov)
        assertEquals(HALF_PI, state.yawDeltaRad, EPSILON)
        assertEquals(0.0, state.arrowAngleRad ?: Double.NaN, EPSILON)
    }

    @Test
    fun quat_targetOutsideAbovePointsArrowUp() {
        val state =
            PanoramaFovMath.resolveTargetQuat(
                gaze = direction(yaw = 0.0, pitch = 0.0),
                target = direction(yaw = 0.0, pitch = HALF_PI),
                horizontalFovRad = HALF_PI,
                verticalFovRad = HALF_PI,
            )

        assertFalse(state.isInsideFov)
        assertEquals(-HALF_PI, state.arrowAngleRad ?: Double.NaN, EPSILON)
    }

    @Test
    fun quat_yawDeltaWrapsAcrossPanoramaSeam() {
        val state =
            PanoramaFovMath.resolveTargetQuat(
                gaze = direction(yaw = Math.toRadians(170.0), pitch = 0.0),
                target = direction(yaw = Math.toRadians(-170.0), pitch = 0.0),
                horizontalFovRad = Math.toRadians(60.0),
                verticalFovRad = HALF_PI,
            )

        assertTrue(state.isInsideFov)
        assertEquals(Math.toRadians(20.0), state.yawDeltaRad, EPSILON)
    }

    @Test
    fun quat_targetBehindViewerIsOutsideFov() {
        val state =
            PanoramaFovMath.resolveTargetQuat(
                gaze = direction(yaw = 0.0, pitch = 0.0),
                target = direction(yaw = Math.toRadians(179.0), pitch = 0.0),
                horizontalFovRad = HALF_PI,
                verticalFovRad = HALF_PI,
            )

        assertFalse(state.isInsideFov)
        val angle = state.arrowAngleRad ?: error("Expected arrow angle")
        assertTrue(
            "Arrow should point roughly right (angle approx 0), got angle=$angle",
            abs(angle) < 0.1,
        )
    }

    @Test
    fun quat_targetUpAndRightPointsArrowUpRight() {
        val state =
            PanoramaFovMath.resolveTargetQuat(
                gaze = direction(yaw = 0.0, pitch = 0.0),
                target = direction(yaw = Math.toRadians(45.0), pitch = Math.toRadians(30.0)),
                horizontalFovRad = Math.toRadians(30.0),
                verticalFovRad = Math.toRadians(30.0),
            )

        assertFalse(state.isInsideFov)
        val angle = state.arrowAngleRad ?: error("Expected arrow angle")
        assertTrue(
            "Arrow should point up-right, got angle=$angle",
            angle < 0.0 && angle > -HALF_PI,
        )
    }

    @Test
    fun quat_and_euler_variants_agree_for_simple_case() {
        val gaze = direction(yaw = 0.0, pitch = 0.0)
        val target = direction(yaw = Math.toRadians(60.0), pitch = Math.toRadians(20.0))

        val eulerResult = PanoramaFovMath.resolveTarget(gaze, target, HALF_PI, HALF_PI)
        val quatResult = PanoramaFovMath.resolveTargetQuat(gaze, target, HALF_PI, HALF_PI)

        assertEquals(eulerResult.isInsideFov, quatResult.isInsideFov)
        assertEquals(eulerResult.yawDeltaRad, quatResult.yawDeltaRad, 1e-6)
        assertEquals(eulerResult.pitchDeltaRad, quatResult.pitchDeltaRad, 1e-6)
        if (eulerResult.arrowAngleRad != null && quatResult.arrowAngleRad != null) {
            assertEquals(eulerResult.arrowAngleRad, quatResult.arrowAngleRad, 1e-6)
        }
    }

    private fun direction(
        yaw: Double,
        pitch: Double,
    ): PanoramaDirection = EquirectangularProjection.fromYawPitch(yaw, pitch)

    private companion object {
        const val EPSILON = 1e-9
        const val HALF_PI = PI / 2.0
    }
}
