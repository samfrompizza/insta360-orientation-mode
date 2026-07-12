package com.arashivision.sdk.demo.core.detection

data class VideoDetectionSidecar(
    val frames: List<VideoDetectionFrame>,
) {
    val frameCount: Int = frames.size
}

data class VideoDetectionFrame(
    val frameIdx: Int,
    val timeSec: Double,
    val objects: List<VideoDetectedObject>,
)

data class VideoDetectedObject(
    val trackId: Int,
    val bboxXyxy: BboxXyxy,
    val centerXy: Point2d,
    val centerNorm: Point2d,
)

data class BboxXyxy(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
)

data class Point2d(
    val x: Double,
    val y: Double,
)
