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

/** Draws a single minimal direction arrow when the selected target is outside the current FOV. */
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val angle = arrowAngleRad ?: return
        if (width == 0 || height == 0) return

        val radius = min(width, height) * 0.36f
        val centerX = width / 2f + cos(angle).toFloat() * radius
        val centerY = height / 2f + sin(angle).toFloat() * radius
        val size = min(width, height) * 0.055f

        canvas.save()
        canvas.translate(centerX, centerY)
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
