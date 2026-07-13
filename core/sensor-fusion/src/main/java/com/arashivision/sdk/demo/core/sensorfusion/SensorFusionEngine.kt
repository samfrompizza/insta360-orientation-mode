package com.arashivision.sdk.demo.core.sensorfusion

import com.arashivision.sdk.demo.core.math.Quaternion
import kotlin.math.atan2
import kotlin.math.sqrt

data class SensorFusionState(
    val yawDeg: Float,
    val pitchDeg: Float,
    val eulerYaw: Float,
    val eulerPitch: Float,
    val eulerRoll: Float,
    val rawYawDeg: Float,
    val rawPitchDeg: Float,
    val rawRollDeg: Float,
    val isCalibrated: Boolean,
)

class SensorFusionEngine(
    var smoothingAlpha: Float = SLERP_SMOOTHING_ALPHA,
) {
    companion object {
        const val SLERP_SMOOTHING_ALPHA = 0.12f
        const val DEFAULT_SENSITIVITY = 0.42f
        const val YAW_SENSITIVITY_FACTOR = 0.04f
        const val PITCH_SENSITIVITY_FACTOR = 0.02f

        const val ROTATION_0 = 0
        const val ROTATION_90 = 1
        const val ROTATION_180 = 2
        const val ROTATION_270 = 3

        private fun forwardVectorForRotation(displayRotation: Int): FloatArray =
            when (displayRotation) {
                ROTATION_0 -> floatArrayOf(0f, -1f, 0f)
                ROTATION_90 -> floatArrayOf(-1f, 0f, 0f)
                ROTATION_180 -> floatArrayOf(0f, 1f, 0f)
                ROTATION_270 -> floatArrayOf(1f, 0f, 0f)
                else -> floatArrayOf(0f, -1f, 0f)
            }
    }

    private var currentQuaternion = Quaternion(1f, 0f, 0f, 0f)
    private var smoothedQuaternion = Quaternion(1f, 0f, 0f, 0f)
    private var calibrationQuaternion = Quaternion(1f, 0f, 0f, 0f)
    private var calibrated = false

    private var lastEulerYaw = 0f
    private var lastEulerPitch = 0f
    private var lastEulerRoll = 0f

    private var lastRawYawDeg = 0f
    private var lastRawPitchDeg = 0f
    private var lastRawRollDeg = 0f

    private var calibrationRawYawDeg = 0f
    private var calibrationRawPitchDeg = 0f

    private var smoothedYaw = 0f
    private var smoothedPitch = 0f

    var sensitivity: Float = DEFAULT_SENSITIVITY
        private set

    val yawSensitivity: Float get() = YAW_SENSITIVITY_FACTOR * sensitivity
    val pitchSensitivity: Float get() = PITCH_SENSITIVITY_FACTOR * sensitivity

    var invertYaw = false
    var invertPitch = true

    fun setSensitivity(value: Float) {
        sensitivity = value
    }

    fun update(
        remappedRotationMatrix: FloatArray,
        displayRotation: Int,
    ): SensorFusionState {
        currentQuaternion = Quaternion.fromRotationMatrix(remappedRotationMatrix)

        computeRawAngles(remappedRotationMatrix, displayRotation)

        if (!calibrated) {
            calibrateInternal()
        }

        return if (calibrated) {
            val calibrationInverse = calibrationQuaternion.conjugate()
            val relativeQuaternion = currentQuaternion.multiply(calibrationInverse)

            smoothedQuaternion =
                Quaternion.slerp(smoothedQuaternion, relativeQuaternion, smoothingAlpha)

            val (yaw, pitch, roll) =
                smoothedQuaternion.toEulerAngles(
                    previousYaw = lastEulerYaw,
                    previousPitch = lastEulerPitch,
                    previousRoll = lastEulerRoll,
                )

            lastEulerYaw = yaw
            lastEulerPitch = pitch
            lastEulerRoll = roll

            val targetYaw = yaw * yawSensitivity * if (invertYaw) -1f else 1f
            val targetPitch = pitch * pitchSensitivity * if (invertPitch) -1f else 1f

            val maxYaw = 360f
            val maxPitch = 270f

            smoothedYaw = targetYaw.coerceIn(-maxYaw, maxYaw)
            smoothedPitch = targetPitch.coerceIn(-maxPitch, maxPitch)

            SensorFusionState(
                yawDeg = smoothedYaw,
                pitchDeg = smoothedPitch,
                eulerYaw = lastEulerYaw,
                eulerPitch = lastEulerPitch,
                eulerRoll = lastEulerRoll,
                rawYawDeg = lastRawYawDeg,
                rawPitchDeg = lastRawPitchDeg,
                rawRollDeg = lastRawRollDeg,
                isCalibrated = true,
            )
        } else {
            val (yaw, pitch, roll) =
                currentQuaternion.toEulerAngles(
                    previousYaw = lastEulerYaw,
                    previousPitch = lastEulerPitch,
                    previousRoll = lastEulerRoll,
                )
            lastEulerYaw = yaw
            lastEulerPitch = pitch
            lastEulerRoll = roll

            SensorFusionState(
                yawDeg = 0f,
                pitchDeg = 0f,
                eulerYaw = lastEulerYaw,
                eulerPitch = lastEulerPitch,
                eulerRoll = lastEulerRoll,
                rawYawDeg = lastRawYawDeg,
                rawPitchDeg = lastRawPitchDeg,
                rawRollDeg = lastRawRollDeg,
                isCalibrated = false,
            )
        }
    }

    fun calibrate() = calibrateInternal()

    private fun calibrateInternal() {
        calibrationQuaternion = currentQuaternion.copy()
        calibrationRawYawDeg = lastRawYawDeg
        calibrationRawPitchDeg = lastRawPitchDeg
        lastEulerYaw = 0f
        lastEulerPitch = 0f
        lastEulerRoll = 0f
        calibrated = true
    }

    private fun computeRawAngles(
        remappedMatrix: FloatArray,
        displayRotation: Int,
    ) {
        val fwdRemapped = forwardVectorForRotation(displayRotation)
        val r = remappedMatrix
        val fwX = r[0] * fwdRemapped[0] + r[3] * fwdRemapped[1] + r[6] * fwdRemapped[2]
        val fwY = r[1] * fwdRemapped[0] + r[4] * fwdRemapped[1] + r[7] * fwdRemapped[2]
        val fwZ = r[2] * fwdRemapped[0] + r[5] * fwdRemapped[1] + r[8] * fwdRemapped[2]
        val horizLen = sqrt((fwX * fwX + fwY * fwY).toDouble()).toFloat()

        lastRawYawDeg = Math.toDegrees(atan2(fwY.toDouble(), fwX.toDouble())).toFloat()
        lastRawPitchDeg = Math.toDegrees(atan2(fwZ.toDouble(), horizLen.toDouble())).toFloat()
        lastRawRollDeg = 0f
    }

    fun getLastRawYawDeg(): Float = lastRawYawDeg

    fun getLastRawPitchDeg(): Float = lastRawPitchDeg

    fun getLastRawRollDeg(): Float = lastRawRollDeg

    fun getSmoothedYaw(): Float = smoothedYaw

    fun getSmoothedPitch(): Float = smoothedPitch

    fun getRawEulerYawDeg(): Float = lastEulerYaw

    fun getRawEulerPitchDeg(): Float = lastEulerPitch

    fun getGazeYawDeg(): Float {
        var relative = lastRawYawDeg - calibrationRawYawDeg
        while (relative > 180f) relative -= 360f
        while (relative <= -180f) relative += 360f
        return relative
    }

    fun getGazePitchDeg(): Float = -(lastRawPitchDeg - calibrationRawPitchDeg)

    fun getCurrentQuaternion(): Quaternion = currentQuaternion.copy()

    fun getSmoothedQuaternion(): Quaternion = smoothedQuaternion.copy()
}
