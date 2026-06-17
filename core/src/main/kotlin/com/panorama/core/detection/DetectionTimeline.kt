package com.panorama.core.detection

import dev.romainguy.kotlin.math.Float2

/** Random-access lookup over a parsed [Sidecar]. The runtime queries it once per rendered frame
 *  with the current playback position (ms), so the lookup is a binary search for the nearest frame.
 *
 *  Each wire frame carries its playback time in seconds; it is compared against the query in ms.
 *  Frames are assumed time-ordered (as produced by the offline detector). Positions before the
 *  first / after the last frame clamp to the ends. An empty sidecar answers every query with no
 *  detections. */
class DetectionTimeline(private val sidecar: Sidecar) {

    private val frames: List<SidecarFrame> = sidecar.frames

    val isEmpty: Boolean get() = frames.isEmpty()

    private fun frameTimeMs(frame: SidecarFrame): Long = (frame.timeSec * 1000f).toLong()

    /** The frame whose time is nearest to [positionMs]. Caller must ensure the timeline is
     *  non-empty (guarded here only via [detectionsAt] for the empty case). */
    fun frameAt(positionMs: Long): SidecarFrame {
        // Binary search for the first frame whose time >= positionMs.
        var lo = 0
        var hi = frames.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (frameTimeMs(frames[mid]) < positionMs) lo = mid + 1 else hi = mid
        }
        val hiIdx = lo
        val loIdx = if (lo > 0) lo - 1 else 0
        val distHi = kotlin.math.abs(frameTimeMs(frames[hiIdx]) - positionMs)
        val distLo = kotlin.math.abs(frameTimeMs(frames[loIdx]) - positionMs)
        return if (distLo <= distHi) frames[loIdx] else frames[hiIdx]
    }

    /** Domain detections at the frame nearest to [positionMs]; empty list if the timeline is empty. */
    fun detectionsAt(positionMs: Long): List<Detection> {
        if (frames.isEmpty()) return emptyList()
        return frameAt(positionMs).objects.map { it.toDetection() }
    }
}

/** Converts a wire object into a domain [Detection]. The centre is taken straight from the wire
 *  center_norm ([0,1] image space); the optional pixel bbox is carried through unnormalised only
 *  when present (the arrow path uses the centre, not the bbox). */
private fun SidecarObject.toDetection(): Detection {
    val cx = centerNorm.getOrElse(0) { 0f }
    val cy = centerNorm.getOrElse(1) { 0f }
    val rect = bboxXyxy.takeIf { it.size >= 4 }?.let { Rect(it[0], it[1], it[2], it[3]) }
    return Detection(Float2(cx, cy), rect, label)
}
