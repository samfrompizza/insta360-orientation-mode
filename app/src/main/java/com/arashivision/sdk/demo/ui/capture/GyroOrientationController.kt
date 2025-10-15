package com.arashivision.sdk.demo.ui.capture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Surface
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import kotlin.math.abs

/**
 * Контроллер работы с гироскопом / rotation vector.
 *
 * Отвечает за:
 * - подписку на сенсор
 * - remap по ориентации экрана
 * - преобразование в градусы
 * - калибровку (calibrate())
 * - сглаживание и чувствительность
 * - выдачу готовых yaw/pitch через applyOrientation callback
 *
 * Конструируется с:
 * - context для получения SensorManager
 * - getDisplayRotation - лямбда, возвращающая Surface.ROTATION_*
 * - applyOrientation - функция, которая будет вызвана с готовыми значениями (градусы)
 */
class GyroOrientationController(
    context: Context,
    private val getDisplayRotation: () -> Int,
    private val applyOrientation: (yawDeg: Float, pitchDeg: Float) -> Unit
) : SensorEventListener {

    private val logger: Logger = XLog.tag(GyroOrientationController::class.java.simpleName).build()

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // rate limiting & smoothing
    var rateLimitMs = 33L // ~30Hz
    private var lastSensorUpdate = 0L

    // smoothing
    var smoothingAlpha = 0.15f

    // sensitivity
    companion object {
        var sensivity: Float = 1.0f
    }
    private val yawFactor = 0.04f
    private val pitchFactor = 0.02f
    private val yawSensitivity: Float
        get() = yawFactor * sensivity

    private val pitchSensitivity: Float
        get() = pitchFactor * sensivity

    // inversion
    var invertYaw = true
    var invertPitch = true

    // raw values
    private var lastRawYawDeg = 0f
    private var lastRawPitchDeg = 0f
    private var lastRawRollDeg = 0f

    // smoothed values
    private var smoothedYaw = 0f
    private var smoothedPitch = 0f

    // calibration
    private var yawOffset = 0f
    private var pitchOffset = 0f
    private var calibrated = false

    // enabled orientation controll flag
    var enabled = true

    // internal buffers
    private val rotMat = FloatArray(9)
    private val remapped = FloatArray(9)
    private val out = FloatArray(3)

    fun start() {
        if (!enabled) return
        rotationVectorSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            logger.d("GyroOrientationController started")
        }
    }

    fun stop() {
        try {
            sensorManager.unregisterListener(this)
        } catch (t: Throwable) {
            // ignore
        }
        logger.d("GyroOrientationController stopped")
    }

    /** Re-calibrate */
    fun calibrate() {
        yawOffset = lastRawYawDeg
        pitchOffset = lastRawPitchDeg
        calibrated = true
        logger.d("calibrated yawOffset=$yawOffset")
        logger.d("calibrated pitchOffset=$pitchOffset")
    }

    /** Orientation control disable */
    fun setzOrientationEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!enabled) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastSensorUpdate < rateLimitMs) {
            updateRawFromEvent(event)
            return
        }
        lastSensorUpdate = now

        updateRawFromEvent(event)

        // remap coordinate system to screen orientation
        when (getDisplayRotation()) {
            Surface.ROTATION_0 -> SensorManager.remapCoordinateSystem(
                rotMat,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                remapped
            )
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                rotMat,
                SensorManager.AXIS_Z,
                SensorManager.AXIS_MINUS_X,
                remapped
            )
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                rotMat,
                SensorManager.AXIS_MINUS_X,
                SensorManager.AXIS_MINUS_Z,
                remapped
            )
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                rotMat,
                SensorManager.AXIS_MINUS_Z,
                SensorManager.AXIS_X,
                remapped
            )
            else -> SensorManager.remapCoordinateSystem(
                rotMat,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                remapped
            )
        }

        SensorManager.getOrientation(remapped, out)
        // out[0]=yaw, out[1]=pitch, out[2]=roll in radians
        val yawDeg = Math.toDegrees(out[0].toDouble()).toFloat()
        val pitchDeg = Math.toDegrees(out[1].toDouble()).toFloat()
        val rollDeg = Math.toDegrees(out[2].toDouble()).toFloat()

        lastRawYawDeg = yawDeg
        lastRawPitchDeg = pitchDeg
        lastRawRollDeg = rollDeg

        if (!calibrated) {
            return
        }

        val yawRelative = normalizeAngle(yawDeg - yawOffset)
        val pitchRelative = normalizeAngle(pitchDeg - pitchOffset)

        // apply sensitivity and inversion
        val targetYaw = yawRelative * yawSensitivity * if (invertYaw) -1f else 1f
        val targetPitch = pitchRelative * pitchSensitivity * if (invertPitch) -1f else 1f

        val yawDelta = normalizeAngle(targetYaw - smoothedYaw)
        smoothedYaw = smoothedYaw + smoothingAlpha * yawDelta

        val pitchDelta = normalizeAngle(targetPitch - smoothedPitch)
        smoothedPitch = smoothedPitch + smoothingAlpha * pitchDelta

        // clamp
        val maxYaw = 180f
        val maxPitch = 80f
        if (smoothedYaw > maxYaw) smoothedYaw = maxYaw
        if (smoothedYaw < -maxYaw) smoothedYaw = -maxYaw
        if (smoothedPitch > maxPitch) smoothedPitch = maxPitch
        if (smoothedPitch < -maxPitch) smoothedPitch = -maxPitch

        // exporting result
        applyOrientation(smoothedYaw, smoothedPitch)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // no-op
    }

    private fun updateRawFromEvent(event: SensorEvent) {
        try {
            SensorManager.getRotationMatrixFromVector(rotMat, event.values)
        } catch (t: Throwable) {
            // ignore
        }
    }

    private fun normalizeAngle(angle: Float): Float {
        var a = angle
        while (a <= -180f) a += 360f
        while (a > 180f) a -= 360f
        return a
    }


    fun getLastRawYawDeg(): Float = lastRawYawDeg
    fun getSmoothedYaw(): Float = smoothedYaw
    fun getSmoothedPitch(): Float = smoothedPitch
}
