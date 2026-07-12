package com.arashivision.sdk.demo.core.math.panorama

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

object PanoramaFovMath {
    fun resolveTarget(
        gaze: PanoramaDirection,
        target: PanoramaDirection,
        horizontalFovRad: Double,
        verticalFovRad: Double,
    ): TargetFovState {
        require(horizontalFovRad > 0.0 && horizontalFovRad <= FULL_TURN_RAD) {
            "horizontalFovRad must be in (0, 2*PI]"
        }
        require(verticalFovRad > 0.0 && verticalFovRad <= PI) {
            "verticalFovRad must be in (0, PI]"
        }

        val yawDeltaRad = wrapToMinusPiPlusPi(target.yawRad - gaze.yawRad)
        val pitchDeltaRad = target.pitchRad - gaze.pitchRad
        val insideFov =
            abs(yawDeltaRad) <= horizontalFovRad / 2.0 &&
                abs(pitchDeltaRad) <= verticalFovRad / 2.0

        return TargetFovState(
            isInsideFov = insideFov,
            yawDeltaRad = yawDeltaRad,
            pitchDeltaRad = pitchDeltaRad,
            arrowAngleRad = if (insideFov) null else atan2(-pitchDeltaRad, yawDeltaRad),
        )
    }

    fun resolveTargetQuat(
        gaze: PanoramaDirection,
        target: PanoramaDirection,
        horizontalFovRad: Double,
        verticalFovRad: Double,
    ): TargetFovState {
        require(horizontalFovRad > 0.0 && horizontalFovRad <= FULL_TURN_RAD) {
            "horizontalFovRad must be in (0, 2*PI]"
        }
        require(verticalFovRad > 0.0 && verticalFovRad <= PI) {
            "verticalFovRad must be in (0, PI]"
        }

        val localTarget = gaze.orientation.conjugate().rotate(target.unitVector)

        val localYawRad = atan2(localTarget.y, localTarget.x)
        val horizontalLen = sqrt(localTarget.x * localTarget.x + localTarget.y * localTarget.y)
        val localPitchRad = atan2(localTarget.z, horizontalLen)

        val insideFov =
            abs(localYawRad) <= horizontalFovRad / 2.0 &&
                abs(localPitchRad) <= verticalFovRad / 2.0

        return TargetFovState(
            isInsideFov = insideFov,
            yawDeltaRad = localYawRad,
            pitchDeltaRad = localPitchRad,
            arrowAngleRad = if (insideFov) null else atan2(-localPitchRad, localYawRad),
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
    val arrowAngleRad: Double?,
)
