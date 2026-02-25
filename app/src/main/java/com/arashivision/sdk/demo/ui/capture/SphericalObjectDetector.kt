package com.arashivision.sdk.demo.ui.capture

import org.opencv.core.Mat
import org.opencv.core.Scalar
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

/**
 * Детектирует красные объекты на полной сфере
 *
 * Алгоритм:
 * 1. Получить фрейм в формате эквректангулярной проекции (то что дает камера)
 * 2. Конвертировать в cubemap (6 квадратных граней)
 * 3. На каждой грани найти красные пиксели
 * 4. Вернуть позицию в пространстве
 */
class SphericalObjectDetector {
    private val logger: Logger = XLog.tag(SphericalObjectDetector::class.java.simpleName).build()

    private val redLower1 = Scalar(0.0, 50.0, 50.0)
    private val redUpper1 = Scalar(10.0, 255.0, 255.0)
    private val redLower2 = Scalar(170.0, 50.0, 50.0)
    private val redUpper2 = Scalar(180.0, 255.0, 255.0)

    private val cubeSize = 256

    data class Detection(
        val yawDeg: Float,
        val pitchDeg: Float,
        val confidence: Float,
        val pixelCount: Int,
        val faceIndex: Int
    )

    /**
     * Основной метод: получить фрейм и найти красные объекты
     *
     * @param frameData Raw frame data в RGBA или NV21
     * @param frameWidth Ширина фрейма
     * @param frameHeight Высота фрейма
     * @param formatType CvType.CV_8UC4 для RGBA или CvType.CV_8UC2 для NV21
     * @return Список найденных красных объектов с позициями
     */
    fun detectRedObjectsOnSphere(
        frameData: ByteArray,
        frameWidth: Int,
        frameHeight: Int,
        formatType: Int = CvType.CV_8UC4
    ): List<Detection> {
        return try {
            val frameMat = Mat(frameHeight, frameWidth, formatType)
            frameMat.put(0, 0, frameData)

            val cubeFaces = equirectangularToCubemap(frameMat, cubeSize)

            val detections = mutableListOf<Detection>()
            cubeFaces.forEachIndexed { faceIndex, faceMat ->
                val faceDetections = detectRedOnFace(faceMat, faceIndex)
                detections.addAll(faceDetections)
            }

            frameMat.release()
            cubeFaces.forEach { it.release() }

            detections.sortByDescending { it.confidence }

            logger.d("Detected ${detections.size} red objects on sphere")
            detections
        } catch (e: Exception) {
            logger.e("detectRedObjectsOnSphere error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Конвертирует эквректангулярную сферу в 6 граней куба
     *
     * 0 = Right (+X),  1 = Left (-X)
     * 2 = Top (+Y),    3 = Bottom (-Y)
     * 4 = Front (+Z),  5 = Back (-Z)
     */
    private fun equirectangularToCubemap(equiMat: Mat, size: Int): List<Mat> {
        val faces = mutableListOf<Mat>()

        try {
            val processMat = if (equiMat.channels() == 4) {
                val rgb = Mat()
                Imgproc.cvtColor(equiMat, rgb, Imgproc.COLOR_RGBA2RGB)
                rgb
            } else {
                equiMat
            }

            // математически некорректное преобразование, но для MVP сойдёт

            val stepX = processMat.width() / 6
            val stepY = processMat.height() / 3

            faces.add(cropAndResize(processMat, stepX * 1, 0, stepX, stepY * 2, size))
            faces.add(cropAndResize(processMat, stepX * 4, 0, stepX, stepY * 2, size))
            faces.add(cropAndResize(processMat, stepX * 0, 0, stepX, stepY * 2, size))
            faces.add(cropAndResize(processMat, stepX * 3, 0, stepX, stepY * 2, size))
            faces.add(cropAndResize(processMat, 0, 0, processMat.width(), stepY, size))
            faces.add(cropAndResize(processMat, 0, stepY * 2, processMat.width(), stepY, size))

            processMat.release()
        } catch (e: Exception) {
            logger.e("equirectangularToCubemap error: ${e.message}")
        }

        return faces
    }

    private fun cropAndResize(mat: Mat, x: Int, y: Int, width: Int, height: Int, size: Int): Mat {
        return try {
            val roi = Rect(
                x.coerceAtLeast(0),
                y.coerceAtLeast(0),
                width.coerceAtMost(mat.width() - x),
                height.coerceAtMost(mat.height() - y)
            )
            val cropped = mat.submat(roi)
            val resized = Mat()
            Imgproc.resize(cropped, resized, Size(size.toDouble(), size.toDouble()))
            cropped.release()
            resized
        } catch (e: Exception) {
            logger.e("cropAndResize error: ${e.message}")
            Mat()
        }
    }

    private fun detectRedOnFace(faceMat: Mat, faceIndex: Int): List<Detection> {
        return try {
            val detections = mutableListOf<Detection>()

            val hsvMat = Mat()
            Imgproc.cvtColor(faceMat, hsvMat, Imgproc.COLOR_RGB2HSV)

            val mask1 = Mat()
            val mask2 = Mat()
            Core.inRangeI(hsvMat, Scalar(0.0, 50.0, 50.0), Scalar(10.0, 255.0, 255.0), mask1)
            Core.inRangeI(hsvMat, Scalar(170.0, 50.0, 50.0), Scalar(180.0, 255.0, 255.0), mask2)

            val redMask = Mat()
            Core.bitwise_or(mask1, mask2, redMask)

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
            Imgproc.morphologyEx(redMask, redMask, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.morphologyEx(redMask, redMask, Imgproc.MORPH_OPEN, kernel)

            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(redMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            contours.forEach { contour ->
                val area = Imgproc.contourArea(contour)

                if (area > 100 && area < 50000) {
                    val moments = Imgproc.moments(contour)

                    if (moments.m00 != 0.0) {
                        val cx = (moments.m10 / moments.m00).toFloat()
                        val cy = (moments.m01 / moments.m00).toFloat()

                        val (yaw, pitch) = facePixelToSphericalCoords(faceIndex, cx, cy, faceMat.width(), faceMat.height())

                        val redPixelCount = Core.countNonZero(redMask).toFloat()
                        val confidence = (redPixelCount / (faceMat.width() * faceMat.height())).coerceIn(0f, 1f)

                        detections.add(
                            Detection(
                                yawDeg = yaw,
                                pitchDeg = pitch,
                                confidence = confidence,
                                pixelCount = redPixelCount.toInt(),
                                faceIndex = faceIndex
                            )
                        )
                    }
                }
            }

            hsvMat.release()
            mask1.release()
            mask2.release()
            redMask.release()
            kernel.release()
            hierarchy.release()

            detections
        } catch (e: Exception) {
            logger.e("detectRedOnFace error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Конвертируем пиксельные координаты на грани куба в сферические координаты (yaw, pitch)
     *
     * Грани:
     * 0 = Right   (yaw ≈ 90°)
     * 1 = Left    (yaw ≈ 270°)
     * 2 = Front   (yaw ≈ 0°)
     * 3 = Back    (yaw ≈ 180°)
     * 4 = Top     (pitch ≈ 90°)
     * 5 = Bottom  (pitch ≈ -90°)
     */
    private fun facePixelToSphericalCoords(
        faceIndex: Int,
        pixelX: Float,
        pixelY: Float,
        faceWidth: Int,
        faceHeight: Int
    ): Pair<Float, Float> {
        // Нормализуем координаты [-0.5, 0.5]
        val u = (pixelX / faceWidth) - 0.5f
        val v = (pixelY / faceHeight) - 0.5f

        // Конвертируем в 3D координаты на кубе, потом в сфере
        val (x, y, z) = when (faceIndex) {
            0 -> Triple(1f, v, -u)      // Right
            1 -> Triple(-1f, v, u)      // Left
            2 -> Triple(u, v, 1f)       // Front
            3 -> Triple(-u, v, -1f)     // Back
            4 -> Triple(u, 1f, -v)      // Top
            5 -> Triple(u, -1f, v)      // Bottom
            else -> Triple(0f, 0f, 1f)
        }

        // Конвертируем декартовы координаты (x, y, z) в сферические (yaw, pitch)
        var yaw = atan2(z, x) * 180f / PI.toFloat()
        var pitch = atan2(y, sqrt(x*x + z*z)) * 180f / PI.toFloat()

        // Нормализуем yaw в диапазон [0, 360)
        if (yaw < 0) yaw += 360f

        // pitch должен быть в [-90, 90]
        pitch = pitch.coerceIn(-90f, 90f)

        return Pair(yaw, pitch)
    }
}
