package com.panorama.core.detection

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

/** Pins the nearest-frame lookup and the wire->domain conversion. time_sec is compared against the
 *  ms query; positions before the first / after the last frame clamp to the ends; an in-between
 *  position snaps to the nearest frame time; center_norm is carried straight into the domain
 *  detection. An empty timeline yields no detections. */
class DetectionTimelineTest : FunSpec({

    fun frame(timeSec: Float, cx: Float, cy: Float, label: String) =
        SidecarFrame(timeSec, listOf(SidecarObject(centerNorm = listOf(cx, cy), label = label)))

    // 1.0 s = 1000 ms, 2.0 s = 2000 ms.
    val timeline = DetectionTimeline(
        Sidecar(listOf(frame(1.0f, 0.5f, 0.5f, "a"), frame(2.0f, 0.6f, 0.6f, "b"))),
    )

    test("position before first frame clamps to first") {
        timeline.detectionsAt(0)[0].label shouldBe "a"
    }

    test("position after last frame clamps to last") {
        timeline.detectionsAt(99999)[0].label shouldBe "b"
    }

    test("in-between position snaps to nearest frame time") {
        timeline.detectionsAt(1100)[0].label shouldBe "a"  // ~1000 ms
        timeline.detectionsAt(1900)[0].label shouldBe "b"  // ~2000 ms
    }

    test("center_norm is carried into the domain detection") {
        val a = timeline.detectionsAt(1000)[0]
        a.centerNorm.x shouldBe 0.5f.plusOrMinus(1e-4f)
        a.centerNorm.y shouldBe 0.5f.plusOrMinus(1e-4f)
    }

    test("empty timeline yields no detections") {
        DetectionTimeline(Sidecar(emptyList())).detectionsAt(50) shouldBe emptyList()
    }
})
