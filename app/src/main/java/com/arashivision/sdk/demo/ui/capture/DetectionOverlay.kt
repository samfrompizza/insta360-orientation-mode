package com.arashivision.sdk.demo.ui.capture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog

/**
 * Overlay для отрисовки найденных объектов
 * Показывает красные объекты в режиме реал-тайм
 */
class DetectionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val logger: Logger = XLog.tag(DetectionOverlay::class.java.simpleName).build()

    private var detections: List<SphericalObjectDetector.Detection> = emptyList()
    private var currentYaw: Float = 0f
    private var currentPitch: Float = 0f

    private val textPaint = Paint().apply {
        color = 0xFFFF0000.toInt()  // красный текст
        textSize = 48f
        isAntiAlias = true
    }

    private val circlePaint = Paint().apply {
        color = 0xFFFF0000.toInt()  // красный круг
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val bgPaint = Paint().apply {
        color = 0x80000000.toInt()  // полупрозрачный чёрный фон
    }

    fun updateDetections(
        detections: List<SphericalObjectDetector.Detection>,
        currentYaw: Float,
        currentPitch: Float
    ) {
        this.detections = detections
        this.currentYaw = currentYaw
        this.currentPitch = currentPitch
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Рисуем background для информации
        canvas.drawRect(0f, 0f, 300f, (50 + detections.size * 60).toFloat(), bgPaint)

        // Рисуем текущую ориентацию камеры
        canvas.drawText(
            "Yaw: %.1f°".format(currentYaw),
            10f,
            40f,
            textPaint
        )

        canvas.drawText(
            "Pitch: %.1f°".format(currentPitch),
            10f,
            90f,
            textPaint
        )

        // Рисуем каждый найденный объект
        detections.forEachIndexed { index, detection ->
            val offsetY = 140 + index * 60

            // Вычисляем угловую разницу между камерой и объектом
            val yawDiff = angleDifference(detection.yawDeg, currentYaw)
            val pitchDiff = detection.pitchDeg - currentPitch

            // Если объект в видимой области (±60° по yaw, ±45° по pitch)
            val isVisible = kotlin.math.abs(yawDiff) < 60 && kotlin.math.abs(pitchDiff) < 45

            val statusColor = if (isVisible) 0xFF00FF00.toInt() else 0xFFFFFFFF.toInt()
            textPaint.color = statusColor

            canvas.drawText(
                "Obj ${index + 1}: Δ%.0f°/%.0f°".format(yawDiff, pitchDiff),
                10f,
                offsetY.toFloat(),
                textPaint
            )

            canvas.drawText(
                "Conf: %.1f%%".format(detection.confidence * 100),
                10f,
                (offsetY + 40).toFloat(),
                textPaint
            )
        }
    }

    private fun angleDifference(angle1: Float, angle2: Float): Float {
        var diff = angle1 - angle2

        // Нормализуем в диапазон [-180, 180]
        while (diff > 180) diff -= 360
        while (diff < -180) diff += 360

        return diff
    }
}
