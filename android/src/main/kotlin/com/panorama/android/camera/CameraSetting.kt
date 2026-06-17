package com.panorama.android.camera

/** The basic camera settings this app exposes (a curated subset of the SDK's CaptureSetting). */
enum class CameraSetting { ISO, SHUTTER, EV, WB, RECORD_RESOLUTION, PHOTO_RESOLUTION }

/** One selectable value of a [CameraSetting]: [label] for display, [token] is the SDK enum's name
 *  used to apply it back (decouples the UI from the SDK enum types). */
data class SettingOption(val label: String, val token: String)

/** A setting's current value + its available options, as read from the camera. */
data class SettingState(
    val setting: CameraSetting,
    val current: SettingOption?,
    val options: List<SettingOption>,
)
