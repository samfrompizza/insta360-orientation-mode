package com.arashivision.sdk.demo.ui.player.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Draws a single minimal direction arrow when the selected target is outside the current FOV.
 *  In VR mode draws two arrows — one for each eye at 25% and 75% screen width. */
class DirectionArrowOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val arrowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val arrowPath = Path()

    private var arrowAngleRad: Double? = null
    private var isVrMode = false

    init {
        isClickable = false
        isFocusable = false
        visibility = GONE
    }

    fun showArrow(angleRad: Double) {
        arrowAngleRad = angleRad
        visibility = VISIBLE
        invalidate()
    }

    fun hideArrow() {
        arrowAngleRad = null
        visibility = GONE
        invalidate()
    }

    /** Call when VR mode toggles to switch between single-eye and dual-eye rendering. */
    fun setVrMode(vrMode: Boolean) {
        if (isVrMode != vrMode) {
            isVrMode = vrMode
            if (arrowAngleRad != null) {
                invalidate()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val angle = arrowAngleRad ?: return
        if (width == 0 || height == 0) return

        if (isVrMode) {
            drawVrMode(canvas, angle)
        } else {
            drawNormalMode(canvas, angle)
        }
    }

    private fun drawNormalMode(canvas: Canvas, angle: Double) {
        val radius = min(width, height) * 0.36f
        val centerX = width / 2f
        val centerY = height / 2f
        drawSingleArrow(canvas, centerX, centerY, radius, angle)
    }

    private fun drawVrMode(canvas: Canvas, angle: Double) {
        // Each eye occupies half the screen width, so use a smaller radius based on height.
        val radius = height * 0.28f
        val centerY = height / 2f
        val leftEyeCenterX = width * 0.25f
        val rightEyeCenterX = width * 0.75f

        drawSingleArrow(canvas, leftEyeCenterX, centerY, radius, angle)
        drawSingleArrow(canvas, rightEyeCenterX, centerY, radius, angle)
    }

    private fun drawSingleArrow(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, angle: Double) {
        val arrowCenterX = centerX + cos(angle).toFloat() * radius
        val arrowCenterY = centerY + sin(angle).toFloat() * radius
        val size = min(width.toFloat(), height.toFloat()) * 0.055f

        canvas.save()
        canvas.translate(arrowCenterX, arrowCenterY)
        canvas.rotate(Math.toDegrees(angle).toFloat())

        canvas.drawCircle(0f, 0f, size * 1.25f, backgroundPaint)
        arrowPath.reset()
        arrowPath.moveTo(size, 0f)
        arrowPath.lineTo(-size * 0.65f, -size * 0.72f)
        arrowPath.lineTo(-size * 0.35f, 0f)
        arrowPath.lineTo(-size * 0.65f, size * 0.72f)
        arrowPath.close()
        canvas.drawPath(arrowPath, arrowPaint)
        canvas.drawPath(arrowPath, arrowStrokePaint)

        canvas.restore()
    }
}
