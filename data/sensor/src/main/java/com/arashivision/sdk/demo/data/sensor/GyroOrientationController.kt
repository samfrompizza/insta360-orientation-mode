package com.arashivision.sdk.demo.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Surface
import com.arashivision.sdk.demo.core.math.Quaternion
import com.arashivision.sdk.demo.core.sensorfusion.SensorFusionEngine
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog

class GyroOrientationController(
    context: Context,
    private val getDisplayRotation: () -> Int,
    private val applyOrientation: (yawDeg: Float, pitchDeg: Float) -> Unit,
) : SensorEventListener {
    private val logger: Logger = XLog.tag(GyroOrientationController::class.java.simpleName).build()
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val engine = SensorFusionEngine()

    var rateLimitMs = SENSOR_RATE_LIMIT_MS
    private var lastSensorUpdate = 0L

    companion object {
        private const val SENSOR_RATE_LIMIT_MS = 8L

        var sensivity: Float = SensorFusionEngine.DEFAULT_SENSITIVITY

        var invertYaw = false
        var invertPitch = true
    }

    var enabled = true

    private val rotMat = FloatArray(9)
    private val remapped = FloatArray(9)

    fun start() {
        if (!enabled) return
        rotationVectorSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            logger.d("GyroOrientationController started (quaternion-based)")
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

    fun calibrate() {
        engine.calibrate()
        logger.d("Gyro calibrated via SensorFusionEngine")
    }

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

        when (getDisplayRotation()) {
            Surface.ROTATION_0 ->
                SensorManager.remapCoordinateSystem(
                    rotMat,
                    SensorManager.AXIS_X,
                    SensorManager.AXIS_Z,
                    remapped,
                )
            Surface.ROTATION_90 ->
                SensorManager.remapCoordinateSystem(
                    rotMat,
                    SensorManager.AXIS_Z,
                    SensorManager.AXIS_MINUS_X,
                    remapped,
                )
            Surface.ROTATION_180 ->
                SensorManager.remapCoordinateSystem(
                    rotMat,
                    SensorManager.AXIS_MINUS_X,
                    SensorManager.AXIS_MINUS_Z,
                    remapped,
                )
            Surface.ROTATION_270 ->
                SensorManager.remapCoordinateSystem(
                    rotMat,
                    SensorManager.AXIS_MINUS_Z,
                    SensorManager.AXIS_X,
                    remapped,
                )
            else ->
                SensorManager.remapCoordinateSystem(
                    rotMat,
                    SensorManager.AXIS_X,
                    SensorManager.AXIS_Z,
                    remapped,
                )
        }

        engine.setSensitivity(sensivity)
        engine.invertYaw = invertYaw
        engine.invertPitch = invertPitch

        val state = engine.update(remapped, getDisplayRotation())

        if (state.isCalibrated) {
            applyOrientation(state.yawDeg, state.pitchDeg)
        } else {
            applyOrientation(0f, 0f)
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int,
    ) {
        // no-op
    }

    private fun updateRawFromEvent(event: SensorEvent) {
        try {
            SensorManager.getRotationMatrixFromVector(rotMat, event.values)
        } catch (t: Throwable) {
            // ignore
        }
    }

    fun getLastRawYawDeg(): Float = engine.getLastRawYawDeg()

    fun getLastRawPitchDeg(): Float = engine.getLastRawPitchDeg()

    fun getLastRawRollDeg(): Float = engine.getLastRawRollDeg()

    fun getSmoothedYaw(): Float = engine.getSmoothedYaw()

    fun getSmoothedPitch(): Float = engine.getSmoothedPitch()

    fun getRawEulerYawDeg(): Float = engine.getRawEulerYawDeg()

    fun getRawEulerPitchDeg(): Float = engine.getRawEulerPitchDeg()

    fun getGazeYawDeg(): Float = engine.getGazeYawDeg()

    fun getGazePitchDeg(): Float = engine.getGazePitchDeg()

    fun getCurrentQuaternion(): Quaternion = engine.getCurrentQuaternion()

    fun getSmoothedQuaternion(): Quaternion = engine.getSmoothedQuaternion()

    fun getRawCurrentQuaternion(): Quaternion = engine.getCurrentQuaternion()
}
