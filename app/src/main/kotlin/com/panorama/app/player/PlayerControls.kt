package com.panorama.app.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Transport + mode controls overlaid at the bottom of the player. The slider reflects
 *  [positionMs]; while the user drags it the value is echoed locally and committed on release via
 *  [onSeek]. All actions are plain callbacks into [PlayerViewModel]. */
@Composable
fun PlayerControls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    vrEnabled: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onRecalibrate: () -> Unit,
    onToggleVr: () -> Unit,
    onToggleOrientation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Seek slider. Duration may be unknown (<= 0) before the player prepares; fall back to a
        // 1 ms range so the thumb stays clamped at the start rather than NaN-ing the Slider.
        val max = if (durationMs > 0L) durationMs.toFloat() else 1f
        Slider(
            value = positionMs.toFloat().coerceIn(0f, max),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..max,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onPlayPause) {
                Text(if (isPlaying) "Pause" else "Play")
            }
            FilledTonalButton(onClick = onRecalibrate) {
                Text("Recalibrate")
            }
            FilledTonalButton(onClick = onToggleVr) {
                Text(if (vrEnabled) "VR On" else "VR Off")
            }
            // Mono only: orientation is a manual toggle (no auto-rotate). VR is always landscape.
            if (!vrEnabled) {
                FilledTonalIconButton(onClick = onToggleOrientation) {
                    RotateIcon()
                }
            }
        }
    }
}

/** A small "rotate" glyph (a ~270° arc with an arrowhead) drawn with Canvas so we need no
 *  material-icons dependency. Sized for an IconButton. */
@Composable
private fun RotateIcon() {
    val color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = size.minDimension * 0.11f
        val r = size.minDimension * 0.33f
        val c = Offset(size.width / 2f, size.height / 2f)
        // A ~290° arc; the arrowhead caps its END, pointing along the tangent (clockwise sense).
        val box = Rect(Offset(c.x - r, c.y - r), Size(r * 2f, r * 2f))
        val startDeg = -50f
        val sweepDeg = 290f
        drawArc(
            color = color,
            startAngle = startDeg,
            sweepAngle = sweepDeg,
            useCenter = false,
            topLeft = box.topLeft,
            size = box.size,
            style = Stroke(width = stroke),
        )
        // End point of the arc and its clockwise tangent direction.
        val endRad = Math.toRadians((startDeg + sweepDeg).toDouble())
        val end = Offset(c.x + r * kotlin.math.cos(endRad).toFloat(), c.y + r * kotlin.math.sin(endRad).toFloat())
        // Tangent (clockwise) = derivative of (cos,sin) by +angle: (-sin, cos).
        val tx = (-kotlin.math.sin(endRad)).toFloat()
        val ty = (kotlin.math.cos(endRad)).toFloat()
        // Normal (toward centre/away): (cos, sin) is radial-outward.
        val nx = kotlin.math.cos(endRad).toFloat()
        val ny = kotlin.math.sin(endRad).toFloat()
        val h = stroke * 1.8f
        val head = Path().apply {
            moveTo(end.x + tx * h * 1.4f, end.y + ty * h * 1.4f)         // tip, ahead along tangent
            lineTo(end.x - nx * h, end.y - ny * h)                       // inner barb
            lineTo(end.x + nx * h, end.y + ny * h)                       // outer barb
            close()
        }
        drawPath(head, color = color)
    }
}
