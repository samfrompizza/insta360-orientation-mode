package com.arashivision.sdk.demo.ui.capture

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
    private val mapCache = HashMap<Int, List<Pair<Mat, Mat>>>()

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
                equiMat.clone()
            }

            val faceMaps = getOrCreateFaceMaps(processMat.width(), processMat.height(), size)
            repeat(6) { faceIndex ->
                val (mapX, mapY) = faceMaps[faceIndex]
                val dst = Mat(size, size, processMat.type())
                Imgproc.remap(processMat, dst, mapX, mapY, Imgproc.INTER_LINEAR)
                faces.add(dst)
            }

            processMat.release()
        } catch (e: Exception) {
            logger.e("equirectangularToCubemap error: ${e.message}")
        }

        return faces
    }

    private fun getOrCreateFaceMaps(equiWidth: Int, equiHeight: Int, size: Int): List<Pair<Mat, Mat>> {
        val key = (equiWidth shl 20) xor (equiHeight shl 8) xor size
        mapCache[key]?.let { return it }

        val maps = mutableListOf<Pair<Mat, Mat>>()
        for (face in 0..5) {
            val mapX = Mat(size, size, CvType.CV_32FC1)
            val mapY = Mat(size, size, CvType.CV_32FC1)
            for (y in 0 until size) {
                val v = (2.0 * (y + 0.5) / size) - 1.0
                for (x in 0 until size) {
                    val u = (2.0 * (x + 0.5) / size) - 1.0
                    val (dx, dy, dz) = faceUvToDirection(face, u, -v)
                    val norm = sqrt(dx * dx + dy * dy + dz * dz)
                    val nx = dx / norm
                    val ny = dy / norm
                    val nz = dz / norm

                    val theta = atan2(nx, nz)
                    val phi = asin(ny)

                    val srcX = ((theta + PI) / (2.0 * PI) * equiWidth).toFloat().coerceIn(0f, (equiWidth - 1).toFloat())
                    val srcY = ((PI / 2 - phi) / PI * equiHeight).toFloat().coerceIn(0f, (equiHeight - 1).toFloat())
                    mapX.put(y, x, srcX)
                    mapY.put(y, x, srcY)
                }
            }
            maps.add(Pair(mapX, mapY))
        }

        // Храним только один часто используемый набор карт в памяти.
        mapCache.values.flatten().forEach { (x, y) -> x.release(); y.release() }
        mapCache.clear()
        mapCache[key] = maps
        return maps
    }

    private fun faceUvToDirection(faceIndex: Int, u: Double, v: Double): Triple<Double, Double, Double> {
        return when (faceIndex) {
            0 -> Triple(1.0, v, -u)     // Right (+X)
            1 -> Triple(-1.0, v, u)     // Left (-X)
            2 -> Triple(u, 1.0, -v)     // Top (+Y)
            3 -> Triple(u, -1.0, v)     // Bottom (-Y)
            4 -> Triple(u, v, 1.0)      // Front (+Z)
            5 -> Triple(-u, v, -1.0)    // Back (-Z)
            else -> Triple(u, v, 1.0)
        }
    }

    private fun detectRedOnFace(faceMat: Mat, faceIndex: Int): List<Detection> {
        return try {
            val detections = mutableListOf<Detection>()

            val hsvMat = Mat()
            Imgproc.cvtColor(faceMat, hsvMat, Imgproc.COLOR_RGB2HSV)

            val mask1 = Mat()
            val mask2 = Mat()
            Core.inRange(hsvMat, redLower1, redUpper1, mask1)
            Core.inRange(hsvMat, redLower2, redUpper2, mask2)

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
