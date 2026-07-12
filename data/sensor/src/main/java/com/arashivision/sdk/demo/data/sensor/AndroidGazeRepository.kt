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
import com.arashivision.sdk.demo.domain.repository.GazeRepository

class AndroidGazeRepository(
    private val context: Context,
    private val engine: SensorFusionEngine,
) : GazeRepository, SensorEventListener {
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotMat = FloatArray(9)
    private val remapped = FloatArray(9)

    private var lastSensorUpdate = 0L

    override fun getCurrentOrientation(): Quaternion = engine.getCurrentQuaternion()

    override fun startTracking() {
        rotationVectorSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun stopTracking() {
        try {
            sensorManager.unregisterListener(this)
        } catch (t: Throwable) {
            // ignore
        }
    }

    override fun recenter() {
        engine.calibrate()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSensorUpdate < RATE_LIMIT_MS) {
            updateRawMatrix(event)
            return
        }
        lastSensorUpdate = now
        updateRawMatrix(event)

        remapMatrix()

        engine.update(remapped, displayRotation())

        val state = engine.getSmoothedQuaternion()
        // State is updated in the engine; consumers poll via getCurrentOrientation()
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int,
    ) {
        // no-op
    }

    private fun updateRawMatrix(event: SensorEvent) {
        try {
            SensorManager.getRotationMatrixFromVector(rotMat, event.values)
        } catch (t: Throwable) {
            // ignore
        }
    }

    private fun remapMatrix() {
        when (displayRotation()) {
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
    }

    private fun displayRotation(): Int {
        return try {
            context.display?.rotation ?: Surface.ROTATION_0
        } catch (t: Throwable) {
            Surface.ROTATION_0
        }
    }

    companion object {
        private const val RATE_LIMIT_MS = 8L
    }
}
