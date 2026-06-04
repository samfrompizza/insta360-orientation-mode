package com.arashivision.sdk.demo.ui.player.panorama

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Minimal field-of-view helper for deciding whether a spherical target is visible.
 *
 * It uses the same yaw/pitch convention as [EquirectangularProjection]: yaw grows to the right,
 * pitch grows upward, and deltas are evaluated in screen terms (positive yaw delta = target is to
 * the right of the current gaze, positive pitch delta = target is above the current gaze).
 */
object PanoramaFovMath {

    fun resolveTarget(
        gaze: PanoramaDirection,
        target: PanoramaDirection,
        horizontalFovRad: Double,
        verticalFovRad: Double
    ): TargetFovState {
        require(horizontalFovRad > 0.0 && horizontalFovRad <= FULL_TURN_RAD) {
            "horizontalFovRad must be in (0, 2*PI]"
        }
        require(verticalFovRad > 0.0 && verticalFovRad <= PI) {
            "verticalFovRad must be in (0, PI]"
        }

        val yawDeltaRad = wrapToMinusPiPlusPi(target.yawRad - gaze.yawRad)
        val pitchDeltaRad = target.pitchRad - gaze.pitchRad
        val insideFov = abs(yawDeltaRad) <= horizontalFovRad / 2.0 &&
            abs(pitchDeltaRad) <= verticalFovRad / 2.0

        return TargetFovState(
            isInsideFov = insideFov,
            yawDeltaRad = yawDeltaRad,
            pitchDeltaRad = pitchDeltaRad,
            // Canvas coordinates have +Y downward, so an upward pitch delta becomes negative Y.
            arrowAngleRad = if (insideFov) null else atan2(-pitchDeltaRad, yawDeltaRad)
        )
    }

    private fun wrapToMinusPiPlusPi(angleRad: Double): Double {
        var result = angleRad
        while (result <= -PI) result += FULL_TURN_RAD
        while (result > PI) result -= FULL_TURN_RAD
        return result
    }

    private const val FULL_TURN_RAD = 6.283185307179586
}

data class TargetFovState(
    val isInsideFov: Boolean,
    val yawDeltaRad: Double,
    val pitchDeltaRad: Double,
    /** Screen-space arrow angle: 0 points right, -PI/2 points up, +PI/2 points down. */
    val arrowAngleRad: Double?
)
