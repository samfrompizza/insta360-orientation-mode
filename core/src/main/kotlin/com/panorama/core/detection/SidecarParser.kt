package com.panorama.core.detection

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Wire object inside a sidecar frame, matching the offline-360-cv output: a normalised [0,1]
 *  centre (center_norm) plus optional pixel bbox / label / track id. The runtime consumes the
 *  centre directly — no normalisation needed. */
@Serializable
data class SidecarObject(
    @SerialName("center_norm") val centerNorm: List<Float> = emptyList(),
    @SerialName("bbox_xyxy") val bboxXyxy: List<Float> = emptyList(),
    val label: String? = null,
    @SerialName("track_id") val trackId: Int? = null,
)

/** Wire frame: the playback time (seconds) and the objects detected at that time. */
@Serializable
data class SidecarFrame(
    @SerialName("time_sec") val timeSec: Float = 0f,
    val objects: List<SidecarObject> = emptyList(),
)

/** Top-level wire shape: a time-ordered list of frames. */
@Serializable
data class Sidecar(
    val frames: List<SidecarFrame> = emptyList(),
)

/** Parses sidecar JSON into the [Sidecar] wire model. A missing or corrupt sidecar must never
 *  crash the runtime, so any parse failure (including empty input) degrades to an empty Sidecar. */
object SidecarParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(text: String): Sidecar =
        try {
            json.decodeFromString<Sidecar>(text)
        } catch (_: Exception) {
            Sidecar()
        }
}
