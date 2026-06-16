package com.panorama.core.fov

import com.panorama.core.calibration.AxisConvention
import com.panorama.core.calibration.ViewCalibration
import com.panorama.core.math.GazeState
import dev.romainguy.kotlin.math.Float2
import dev.romainguy.kotlin.math.Quaternion
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.PI

/** Pins the SCREEN-SPACE direction of the guidance arrow, not just its visibility (which
 *  [ArrowResolverTest] already covers). The bug these tests guard: the arrow pointed the wrong way
 *  on the phone because [PanoramaFov.arrowAngle] returned a world-frame angle while [ArrowOverlay]
 *  consumes it as a Compose screen angle (X right, Y DOWN). Convention here: 0 = right, +pi/2 = down,
 *  -pi/2 = up, +-pi = left. */
class PanoramaFovArrowAngleTest : FunSpec({

    val conv = AxisConvention()
    val identity = GazeState(Quaternion(), 0f, 0f, 0f)
    val tol = 0.01f

    // A detection straight to the viewer's right (equirect x=0.75 -> yaw +90 deg) must draw the
    // arrow pointing screen-right: angle ~ 0.
    test("target to the right points the arrow right") {
        val world = ViewCalibration.detectionDirToWorld(Float2(0.75f, 0.5f), conv)
        PanoramaFov.arrowAngle(identity, world) shouldBe 0f.plusOrMinus(tol)
    }

    // A detection straight to the viewer's left (equirect x=0.25 -> yaw -90 deg) must point
    // screen-left: angle ~ +-pi.
    test("target to the left points the arrow left") {
        val world = ViewCalibration.detectionDirToWorld(Float2(0.25f, 0.5f), conv)
        val a = PanoramaFov.arrowAngle(identity, world)
        kotlin.math.abs(a) shouldBe PI.toFloat().plusOrMinus(tol)
    }

    // A detection above the horizon (equirect y small -> +pitch) must point screen-UP. On the
    // Compose canvas +Y is down, so "up" is angle ~ -pi/2.
    test("target above points the arrow up") {
        val world = ViewCalibration.detectionDirToWorld(Float2(0.5f, 0.1f), conv)
        PanoramaFov.arrowAngle(identity, world) shouldBe (-PI.toFloat() / 2f).plusOrMinus(tol)
    }

    // A detection below the horizon (equirect y large -> -pitch) must point screen-DOWN: angle ~ +pi/2.
    test("target below points the arrow down") {
        val world = ViewCalibration.detectionDirToWorld(Float2(0.5f, 0.9f), conv)
        PanoramaFov.arrowAngle(identity, world) shouldBe (PI.toFloat() / 2f).plusOrMinus(tol)
    }
})
