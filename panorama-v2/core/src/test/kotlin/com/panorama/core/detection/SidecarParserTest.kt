package com.panorama.core.detection

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Pins the sidecar JSON contract against the REAL offline-360-cv output shape: frames carry
 *  time_sec and objects carry a normalised center_norm (plus pixel bbox_xyxy / track_id).
 *  Malformed/empty input degrades to an empty Sidecar instead of throwing — the runtime must never
 *  crash on a missing or corrupt sidecar. */
class SidecarParserTest : FunSpec({

    test("parses real offline-360-cv shape: time_sec + center_norm + bbox_xyxy") {
        val json = """
            {
              "video": "data/raw/red.mov",
              "frames": [
                {"frame_idx": 0, "time_sec": 0.0, "objects": [
                  {"track_id": 1, "bbox_xyxy": [0.0, 256.0, 30.0, 268.0],
                   "center_xy": [15.0, 262.0], "center_norm": [0.011719, 0.409375]}
                ]},
                {"frame_idx": 10, "time_sec": 0.4, "objects": []}
              ]
            }
        """.trimIndent()
        val sidecar = SidecarParser.parse(json)
        sidecar.frames.size shouldBe 2
        sidecar.frames[0].timeSec shouldBe 0.0f
        sidecar.frames[0].objects[0].centerNorm shouldBe listOf(0.011719f, 0.409375f)
        sidecar.frames[0].objects[0].bboxXyxy shouldBe listOf(0.0f, 256.0f, 30.0f, 268.0f)
        sidecar.frames[0].objects[0].trackId shouldBe 1
        sidecar.frames[1].objects shouldBe emptyList()
    }

    test("empty string parses to empty sidecar without crashing") {
        SidecarParser.parse("").frames shouldBe emptyList()
    }

    test("garbage parses to empty sidecar without crashing") {
        SidecarParser.parse("{garbage").frames shouldBe emptyList()
    }
})
