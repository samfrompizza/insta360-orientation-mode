package com.arashivision.sdk.demo.ui.player.detection

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException

/** Parses detection sidecar JSON files produced for offline panoramic videos. */
/*JSON example (coordinates are in pixels):
{
  "video": {
    "path": "data/raw/red.mp4",
    "width": 1280,
    "height": 640,
    "fps": 29.97002997002997,
    "frame_count": 2378,
    "duration_sec": 79.34593333333333
  },
  {
      "frame_idx": 198,
      "objects": [
        {
          "track_id": 2,
          "bbox_xyxy": [
            1204.0,
            270.0,
            1243.0,
            283.0
          ],
          "center_xy": [
            1223.5,
            276.5
          ],
          "center_norm": [
            0.955859,
            0.432031
          ]
        }
      ],
      "time_sec": 6.6066
    },
 */
class VideoDetectionSidecarParser {

    fun parse(json: String): VideoDetectionSidecar {
        val trimmed = json.trim()
        require(trimmed.isNotEmpty()) { "Detection JSON is empty" }

        val framesArray = when (trimmed.first()) {
            '[' -> JSONArray(trimmed)
            '{' -> parseObjectRootOrObjectSequence(trimmed)
            else -> error("Detection JSON must start with '[' or '{'")
        }

        val frames = buildList {
            for (index in 0 until framesArray.length()) {
                add(parseFrame(framesArray.getJSONObject(index)))
            }
        }.sortedWith(compareBy<VideoDetectionFrame> { it.timeSec }.thenBy { it.frameIdx })

        return VideoDetectionSidecar(frames)
    }

    private fun parseObjectRootOrObjectSequence(json: String): JSONArray {
        return try {
            val root = JSONObject(json)
            when {
                root.has("frame_idx") -> JSONArray().put(root)
                else -> findFramesArray(root)
            }
        } catch (exception: JSONException) {
            // Some exported examples are shared as a comma-separated list of frame objects with
            // the enclosing '[' and ']' omitted. Accept that shape as a convenience for sidecars.
            JSONArray("[${json.trim().trimEnd(',')}]")
        }
    }

    private fun findFramesArray(root: JSONObject): JSONArray {
        val supportedKeys = listOf("frames", "detections", "data")
        val key = supportedKeys.firstOrNull { root.optJSONArray(it) != null }
        return key?.let(root::getJSONArray)
            ?: error("Detection JSON object must contain one of: ${supportedKeys.joinToString()}")
    }

    private fun parseFrame(frameJson: JSONObject): VideoDetectionFrame {
        val objectsJson = frameJson.optJSONArray("objects") ?: JSONArray()
        val objects = buildList {
            for (index in 0 until objectsJson.length()) {
                add(parseObject(objectsJson.getJSONObject(index)))
            }
        }

        return VideoDetectionFrame(
            frameIdx = frameJson.getInt("frame_idx"),
            timeSec = frameJson.getDouble("time_sec"),
            objects = objects
        )
    }

    private fun parseObject(objectJson: JSONObject): VideoDetectedObject {
        return VideoDetectedObject(
            trackId = objectJson.getInt("track_id"),
            bboxXyxy = objectJson.getJSONArray("bbox_xyxy").toBboxXyxy(),
            centerXy = objectJson.getJSONArray("center_xy").toPoint2d(),
            centerNorm = objectJson.getJSONArray("center_norm").toPoint2d()
        )
    }

    private fun JSONArray.toBboxXyxy(): BboxXyxy {
        require(length() == 4) { "bbox_xyxy must contain four numbers" }
        return BboxXyxy(
            left = getDouble(0),
            top = getDouble(1),
            right = getDouble(2),
            bottom = getDouble(3)
        )
    }

    private fun JSONArray.toPoint2d(): Point2d {
        require(length() == 2) { "point array must contain two numbers" }
        return Point2d(
            x = getDouble(0),
            y = getDouble(1)
        )
    }
}
