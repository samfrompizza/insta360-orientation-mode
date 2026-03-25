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
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.asin

/**
 * Контроллер работы с гироскопом на основе кватернионов.
 *
 * Отвечает за:
 * - связь с сенсором
 * - remap по ориентации экрана
 * - обработку кватернионов
 * - калибровку
 * - сглаживание кватернионов SLERP (Spherical Linear Interpolation)
 * - конвертирование итогового кватерниона в (yaw/pitch) для совместимости
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
    var rateLimitMs = 8L // ~90Hz
    private var lastSensorUpdate = 0L
    var smoothingAlpha = 0.12f // коэффициент интерполяции

    companion object {
        var sensivity: Float = 1.2f
        private val yawFactor = 0.04f
        private val pitchFactor = 0.02f
        private val yawSensitivity: Float
            get() = yawFactor * sensivity
        private val pitchSensitivity: Float
            get() = pitchFactor * sensivity
        var invertYaw = false
        var invertPitch = true
    }
    private var lastLogTime = 0L
    private var lastRawYawDeg = 0f
    private var lastRawPitchDeg = 0f
    private var lastRawRollDeg = 0f
    private var smoothedYaw = 0f
    private var smoothedPitch = 0f
    private var lastEulerYaw = 0f
    private var lastEulerPitch = 0f
    private var lastEulerRoll = 0f

    private var calibrationQuaternion = Quaternion(1f, 0f, 0f, 0f) // идентичный кватернион
    private var calibrated = false
    var enabled = true

    private val rotMat = FloatArray(9)
    private val remapped = FloatArray(9)
    private val out = FloatArray(3)

    private var currentQuaternion = Quaternion(1f, 0f, 0f, 0f)
    private var smoothedQuaternion = Quaternion(1f, 0f, 0f, 0f)

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
        calibrationQuaternion = currentQuaternion.copy()
        lastEulerYaw = 0f
        lastEulerPitch = 0f
        lastEulerRoll = 0f
        calibrated = true
        logger.d("Gyro calibrated at quaternion: w=${calibrationQuaternion.w}, x=${calibrationQuaternion.x}, y=${calibrationQuaternion.y}, z=${calibrationQuaternion.z}")
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

        currentQuaternion = Quaternion.fromRotationMatrix(remapped)

        SensorManager.getOrientation(remapped, out)
        lastRawYawDeg = Math.toDegrees(out[0].toDouble()).toFloat()
        lastRawPitchDeg = Math.toDegrees(out[1].toDouble()).toFloat()
        lastRawRollDeg = Math.toDegrees(out[2].toDouble()).toFloat()

        if (!calibrated) {
            return
        }

        // q_relative = q_current * inverse(q_calibration)
        // inverse(q) = conjugate(q) / |q|^2
        val calibrationInverse = calibrationQuaternion.conjugate()
        val relativeQuaternion = currentQuaternion.multiply(calibrationInverse)

        smoothedQuaternion = Quaternion.slerp(smoothedQuaternion, relativeQuaternion, smoothingAlpha)

        val (yaw, pitch, roll) = smoothedQuaternion.toEulerAngles(
            previousYaw = lastEulerYaw,
            previousPitch = lastEulerPitch,
            previousRoll = lastEulerRoll
        )

        lastEulerYaw = yaw
        lastEulerPitch = pitch
        lastEulerRoll = roll

        val targetYaw = yaw * yawSensitivity * if (invertYaw) -1f else 1f
        val targetPitch = pitch * pitchSensitivity * if (invertPitch) -1f else 1f

        val maxYaw = 360f
        val maxPitch = 270f

        val clampedYaw = targetYaw.coerceIn(-maxYaw, maxYaw)
        val clampedPitch = targetPitch.coerceIn(-maxPitch, maxPitch)

        smoothedYaw = clampedYaw
        smoothedPitch = clampedPitch

        val thisMoment = SystemClock.elapsedRealtime()
        if (thisMoment - lastLogTime >= 500L) {
            lastLogTime = thisMoment
            logger.d(
                "yaw=$smoothedYaw pitch=$smoothedPitch " +
                        "rawYaw=$lastRawYawDeg rawPitch=$lastRawPitchDeg rawRoll=$lastRawRollDeg " +
                        "q=${smoothedQuaternion}"
            )
            logger.d(
                "yawRawFromQuat=$yaw pitchRawFromQuat=$pitch rollRawFromQuat=$roll " +
                        "targetYaw=${yaw * yawSensitivity} targetPitch=${pitch * pitchSensitivity}"
            )
        }
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

    fun getLastRawYawDeg(): Float = lastRawYawDeg
    fun getLastRawPitchDeg(): Float = lastRawPitchDeg
    fun getLastRawRollDeg(): Float = lastRawRollDeg
    fun getSmoothedYaw(): Float = smoothedYaw
    fun getSmoothedPitch(): Float = smoothedPitch

    fun getCurrentQuaternion(): Quaternion = currentQuaternion.copy()
    fun getSmoothedQuaternion(): Quaternion = smoothedQuaternion.copy()

    /**
     * Внутренний класс для работы с кватернионами (w, x, y, z)
     */
    data class Quaternion(
        val w: Float,  // скаляр
        val x: Float,  // i
        val y: Float,  // j
        val z: Float   // k
    ) {
        fun magnitude(): Float = sqrt(w * w + x * x + y * y + z * z)
        fun normalize(): Quaternion {
            val mag = magnitude()
            return if (mag > 0) {
                Quaternion(w / mag, x / mag, y / mag, z / mag)
            } else {
                Quaternion(1f, 0f, 0f, 0f)
            }
        }
        fun copy(): Quaternion = Quaternion(w, x, y, z)

        // conjugate(w + xi + yj + zk) = w - xi - yj - zk
        fun conjugate(): Quaternion = Quaternion(w, -x, -y, -z)

        // (a, b) * (c, d) = (ac - b·d, ad + bc + b × d)
        fun multiply(other: Quaternion): Quaternion {
            val w = this.w * other.w - this.x * other.x - this.y * other.y - this.z * other.z
            val x = this.w * other.x + this.x * other.w + this.y * other.z - this.z * other.y
            val y = this.w * other.y - this.x * other.z + this.y * other.w + this.z * other.x
            val z = this.w * other.z + this.x * other.y - this.y * other.x + this.z * other.w
            return Quaternion(w, x, y, z).normalize()
        }

        fun dot(other: Quaternion): Float =
            this.w * other.w + this.x * other.x + this.y * other.y + this.z * other.z

        /**
         * ZYX convention
         * return (yaw, pitch, roll) in deg
         *
         * standard transformation:
         * - yaw   : atan2(2(wz + xy), 1 - 2(y² + z²))
         * - pitch : asin(2(wy - xz))
         * - roll  : atan2(2(wx + yz), 1 - 2(x² + y²))
         */
        fun toEulerAngles(
            previousYaw: Float? = null,
            previousPitch: Float? = null,
            previousRoll: Float? = null
        ): Triple<Float, Float, Float> {
            val q = this.normalize()

            val sinPitch = (2.0f * (q.w * q.y - q.z * q.x)).coerceIn(-1f, 1f)

            var pitch = if (sinPitch >= 1.0f) {
                90f
            } else if (sinPitch <= -1.0f) {
                -90f
            } else {
                Math.toDegrees(asin(sinPitch.toDouble())).toFloat()
            }

            var yaw = Math.toDegrees(
                atan2(
                    2.0 * (q.w * q.z + q.x * q.y).toDouble(),
                    1.0 - 2.0 * (q.y * q.y + q.z * q.z).toDouble()
                )
            ).toFloat()

            var roll = Math.toDegrees(
                atan2(
                    2.0 * (q.w * q.x + q.y * q.z).toDouble(),
                    1.0 - 2.0 * (q.x * q.x + q.y * q.y).toDouble()
                )
            ).toFloat()

            if (previousYaw != null) yaw = unwrapToNearest(yaw, previousYaw)
            if (previousPitch != null) pitch = unwrapToNearest(pitch, previousPitch)
            if (previousRoll != null) roll = unwrapToNearest(roll, previousRoll)

            return Triple(yaw, pitch, roll)
        }

        private fun wrap180(a: Float): Float {
            var x = (a + 180f) % 360f
            if (x < 0f) x += 360f
            return x - 180f
        }

        private fun unwrapToNearest(angle: Float, reference: Float): Float {
            var a = wrap180(angle)
            var diff = a - reference
            if (diff > 180f) a -= 360f
            if (diff < -180f) a += 360f
            return a
        }

        companion object {
            /**
             * Конвертирование матрицы ротации 3x3 в кватернион
             *
             * Источник: "Quaternion from Rotation Matrix" by S.W. Shepperd
             */
            fun fromRotationMatrix(mat: FloatArray): Quaternion {
                val trace = mat[0] + mat[4] + mat[8]

                val result: Quaternion = when {
                    trace > 0 -> {
                        val s = 0.5f / sqrt(trace + 1.0f)
                        val w = 0.25f / s
                        val x = (mat[7] - mat[5]) * s
                        val y = (mat[2] - mat[6]) * s
                        val z = (mat[3] - mat[1]) * s
                        Quaternion(w, x, y, z)
                    }
                    // m00 — наибольший диагональный элемент
                    mat[0] > mat[4] && mat[0] > mat[8] -> {
                        val s = 2.0f * sqrt(1.0f + mat[0] - mat[4] - mat[8])
                        val w = (mat[7] - mat[5]) / s  // (m21 - m12) / s
                        val x = 0.25f * s
                        val y = (mat[1] + mat[3]) / s  // (m01 + m10) / s
                        val z = (mat[2] + mat[6]) / s  // (m02 + m20) / s
                        Quaternion(w, x, y, z)
                    }
                    // m11 — наибольший диагональный элемент
                    mat[4] > mat[8] -> {
                        val s = 2.0f * sqrt(1.0f + mat[4] - mat[0] - mat[8])
                        val w = (mat[2] - mat[6]) / s  // (m02 - m20) / s
                        val x = (mat[1] + mat[3]) / s  // (m01 + m10) / s
                        val y = 0.25f * s
                        val z = (mat[5] + mat[7]) / s  // (m12 + m21) / s
                        Quaternion(w, x, y, z)
                    }
                    // m22 — наибольший диагональный элемент
                    else -> {
                        val s = 2.0f * sqrt(1.0f + mat[8] - mat[0] - mat[4])
                        val w = (mat[3] - mat[1]) / s  // (m10 - m01) / s
                        val x = (mat[2] + mat[6]) / s  // (m02 + m20) / s
                        val y = (mat[5] + mat[7]) / s  // (m12 + m21) / s
                        val z = 0.25f * s
                        Quaternion(w, x, y, z)
                    }
                }

                return result.normalize()
            }

            /**
             * Сферическая линейная интерполяция (SLERP)
             * Интерполирует между двумя кватернионами с постоянной угловой скоростью
             *
             * @param q1 Начальный кватернион
             * @param q2 Конечный кватернион
             * @param t Параметр интерполяции [0, 1]: 0 = q1, 1 = q2
             * @return Интерполированный кватернион
             */
            fun slerp(q1: Quaternion, q2: Quaternion, t: Float): Quaternion {
                val a = q1.normalize()
                val b = q2.normalize()

                var dotProduct = a.dot(b)

                val q2Final = if (dotProduct < 0.0f) {
                    dotProduct = -dotProduct
                    Quaternion(-b.w, -b.x, -b.y, -b.z)
                } else {
                    b
                }

                dotProduct = dotProduct.coerceIn(-1.0f, 1.0f)

                val theta0 = acos(dotProduct.toDouble()).toFloat()
                val theta = theta0 * t

                val q3 = (q2Final - (a * dotProduct)).normalize()

                // SLERP: q(t) = q1 * cos(θ) + q3 * sin(θ)
                return (a * cos(theta.toDouble()).toFloat() + q3 * sin(theta.toDouble()).toFloat()).normalize()
            }

            private operator fun Quaternion.minus(other: Quaternion): Quaternion =
                Quaternion(this.w - other.w, this.x - other.x, this.y - other.y, this.z - other.z)

            private operator fun Quaternion.times(scalar: Float): Quaternion =
                Quaternion(this.w * scalar, this.x * scalar, this.y * scalar, this.z * scalar)

            private operator fun Quaternion.plus(other: Quaternion): Quaternion =
                Quaternion(this.w + other.w, this.x + other.x, this.y + other.y, this.z + other.z)
        }
    }
}
