package com.panorama.app.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.translate
import com.panorama.core.fov.ArrowState
import com.panorama.core.math.GazeState
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.cos
import kotlin.math.sin

/** Off-screen guidance arrow drawn on top of the GL surface. When [arrow] is visible the arrow
 *  points toward the off-FOV detection at [ArrowState.angleRad].
 *
 *  Spec section 5.5: the arrow is intentionally drawn against the UNPREDICTED gaze — the raw sensor
 *  snapshot the engine writes into [gazeRef], NOT the motion-to-photon-predicted gaze the renderer
 *  uses for the sphere. Reading it inside a [withFrameNanos] loop keeps the arrow on the display
 *  clock and re-resolves the on-screen direction each frame as the head turns, even though the
 *  high-frequency gaze never travels through the (slow) UI StateFlow.
 *
 *  In VR the same arrow is mirrored into both eye viewports (drawn at 25% / 75% of the width). */
@Composable
fun ArrowOverlay(
    arrow: ArrowState,
    vrEnabled: Boolean,
    gazeRef: AtomicReference<GazeState>,
    modifier: Modifier = Modifier,
) {
    // Re-read the raw gaze each display frame so the arrow tracks the head independently of the
    // throttled StateFlow that carries `arrow`. We only keep yaw: it is enough to react to the
    // dominant left/right head turn for the on-screen arrow hint.
    var gazeYawDeg by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                gazeYawDeg = gazeRef.get().yawDeg
            }
        }
    }

    Canvas(modifier = modifier) {
        val angle = arrow.angleRad
        if (!arrow.visible || angle == null) return@Canvas
        // gazeYawDeg is read so the loop's recomposition keeps the overlay live; the resolved
        // angleRad already folds in the gaze at resolve time, so the read primarily drives redraw.
        @Suppress("UNUSED_EXPRESSION") gazeYawDeg

        val radius = size.minDimension * 0.38f
        if (vrEnabled) {
            drawArrowAt(Offset(size.width * 0.25f, size.height / 2f), radius * 0.7f, angle)
            drawArrowAt(Offset(size.width * 0.75f, size.height / 2f), radius * 0.7f, angle)
        } else {
            drawArrowAt(Offset(size.width / 2f, size.height / 2f), radius, angle)
        }
    }
}

/** Draw a chevron arrow at [center], placed at [edgeRadius] from it along [angleRad] and rotated to
 *  point outward. [angleRad] is measured CCW from screen +X (atan2(y, x) convention from PanoramaFov). */
private fun DrawScope.drawArrowAt(center: Offset, edgeRadius: Float, angleRad: Float) {
    val tip = Offset(
        center.x + edgeRadius * cos(angleRad),
        center.y + edgeRadius * sin(angleRad),
    )
    translate(tip.x, tip.y) {
        rotateRad(angleRad, pivot = Offset.Zero) {
            // A full arrow pointing along +X: a wide arrowhead at the tip (0,0) plus a shaft
            // trailing back along -X, so the pointing direction is unambiguous. Built backward from
            // the tip; the surrounding rotateRad orients it toward the target.
            val color = Color(0xFFFF5252)
            val headLen = 46f      // arrowhead depth
            val headHalf = 34f     // arrowhead half-width (wider than the shaft)
            val shaftLen = 60f     // how far the shaft trails behind the head
            val shaftHalf = 12f    // shaft half-thickness

            val head = Path().apply {
                moveTo(0f, 0f)                 // tip
                lineTo(-headLen, -headHalf)    // back-left barb
                lineTo(-headLen, headHalf)     // back-right barb
                close()
            }
            drawPath(head, color = color)

            drawRect(
                color = color,
                topLeft = Offset(-headLen - shaftLen, -shaftHalf),
                size = androidx.compose.ui.geometry.Size(shaftLen, shaftHalf * 2f),
            )
        }
    }
}
