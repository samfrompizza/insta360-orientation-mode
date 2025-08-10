package com.arashivision.sdk.demo.ui.play

import android.content.Context
import android.util.Size
import com.arashivision.sdk.demo.R
import com.arashivision.sdkmedia.player.config.InstaStabType
import com.arashivision.sdkmedia.player.offset.OffsetType
import com.arashivision.sdkmedia.player.video.VideoParamsBuilder


val offsetTypeOptions: List<Pair<Int, OffsetType>>
    get() = listOf(
        R.string.player_setting_offset_type_original to OffsetType.ORIGINAL,
        R.string.player_setting_offset_type_protector_fasten to OffsetType.PROTECTOR_FASTEN,
        R.string.player_setting_offset_type_diving_water to OffsetType.DIVING_WATER,
        R.string.player_setting_offset_type_diving_air to OffsetType.DIVING_AIR,
        R.string.player_setting_offset_type_waterproof to OffsetType.WATERPROOF,
        R.string.player_setting_offset_type_protector_adhere to OffsetType.PROTECTOR_ADHERE,
        R.string.player_setting_offset_type_diving_invisible_water to OffsetType.DIVING_INVISIBLE_WATER,
        R.string.player_setting_offset_type_diving_invisible_air to OffsetType.DIVING_INVISIBLE_AIR,
        R.string.player_setting_offset_type_protector_a to OffsetType.PROTECTOR_A,
        R.string.player_setting_offset_type_protector_s to OffsetType.PROTECTOR_S,
        R.string.player_setting_offset_type_protector_as_average to OffsetType.PROTECTOR_AS_AVERAGE,
    )

val stabTypeOptions: List<Pair<Int, Int>>
    get() = listOf(
        R.string.off to InstaStabType.STAB_TYPE_OFF,
        R.string.player_setting_stab_type_panorama to InstaStabType.STAB_TYPE_PANORAMA,
        R.string.player_setting_stab_type_calibrate_horizon to InstaStabType.STAB_TYPE_CALIBRATE_HORIZON,
        R.string.player_setting_stab_type_footage_motion_smooth to InstaStabType.STAB_TYPE_FOOTAGE_MOTION_SMOOTH
    )

val renderModeOptions: List<Pair<Int, Int>>
    get() = listOf(
        R.string.auto to VideoParamsBuilder.RENDER_MODE_AUTO,
        R.string.player_setting_render_plane to VideoParamsBuilder.RENDER_MODE_PLANE_STITCH
    )

val screenRatioOptions: List<Pair<String, IntArray>>
    get() = listOf(
        "9:16" to intArrayOf(9, 16),
        "16:9" to intArrayOf(16, 9),
        "4:3" to intArrayOf(4, 3),
        "3:4" to intArrayOf(3, 4),
        "1:1" to intArrayOf(1, 1),
        "2:1" to intArrayOf(2, 1),
        "2.35:1" to intArrayOf(47, 20)
    )

val switchData: List<Pair<Int,Boolean>>
    get() = listOf(
        R.string.on to true,
        R.string.off to false
    )

val sizeOptionsList: List<Triple<String, Size, IntArray>>
    get() = listOf(
        // 9:16
        Triple("720×1280", Size(720, 1280), intArrayOf(9, 16)),
        Triple("1080×2560", Size(1080, 1920), intArrayOf(9, 16)),
        Triple("1440×2560", Size(1440, 2560), intArrayOf(9, 16)),
        Triple("2160×3840", Size(2160, 3840), intArrayOf(9, 16)),

        // 16:9
        Triple("1280×720", Size(1280, 720), intArrayOf(16, 9)),
        Triple("1920×1080", Size(1920, 1080), intArrayOf(16, 9)),
        Triple("2560×1440", Size(2560, 1440), intArrayOf(16, 9)),
        Triple("3840×2160", Size(3840, 2160), intArrayOf(16, 9)),

        // 1：1
        Triple("1920×1920", Size(1920, 1920), intArrayOf(1, 1)),
        Triple("2880×2880", Size(2880, 2880), intArrayOf(1, 1)),
        Triple("3840×3840", Size(3840, 3840), intArrayOf(1, 1)),

        // 2:1
        Triple("1440×720", Size(1440, 720), intArrayOf(2, 1)),
        Triple("1920×960", Size(1920, 960), intArrayOf(2, 1)),
        Triple("2560×1280", Size(2560, 1280), intArrayOf(2, 1)),
        Triple("2880×1440", Size(2880, 1440), intArrayOf(2, 1)),
        Triple("3840×1920", Size(3840, 1920), intArrayOf(2, 1)),
        Triple("5760×2880", Size(5760, 2880), intArrayOf(2, 1)),
        Triple("6400×3200", Size(6400, 3200), intArrayOf(2, 1)),
        Triple("7680×3840", Size(7680, 3840), intArrayOf(2, 1)),

        // 2.35:1
        Triple("4096×1744", Size(4096, 1744), intArrayOf(47, 20)),
        Triple("5472×2328", Size(5472, 2328), intArrayOf(47, 20)),
        Triple("6144×2614", Size(6144, 2614), intArrayOf(47, 20)),
        Triple("6720×2856", Size(6720, 2856), intArrayOf(47, 20)),
        Triple("7680×3268", Size(7680, 3268), intArrayOf(47, 20)),

        // 4:3
        Triple("1440×1080", Size(1440, 1080), intArrayOf(4, 3)),
        Triple("1920×1440", Size(1920, 1440), intArrayOf(4, 3)),
        Triple("2720×2040", Size(2720, 2040), intArrayOf(4, 3)),
        Triple("4000×3000", Size(4000, 3000), intArrayOf(4, 3)),
        Triple("8000×6000", Size(8000, 6000), intArrayOf(4, 3)),


        // 3:4
        Triple("1080×1440", Size(1080, 1440), intArrayOf(3, 4)),
        Triple("1440×1920", Size(1440, 1920), intArrayOf(3, 4)),
        Triple("2040×2720", Size(2040, 2720), intArrayOf(3, 4)),
        Triple("3000×4000", Size(3000, 4000), intArrayOf(3, 4)),
        Triple("6000×8000", Size(6000, 8000), intArrayOf(3, 4))
    )

val fpsOptionsList: List<Pair<String, Int>>
    get() = listOf(
        "24" to 24,
        "25" to 25,
        "30" to 30,
        "60" to 60,
        "120" to 120,
    )

fun getBitrateOptionsList(context: Context): List<Pair<String, Int>> {
    return listOf(
        context.getString(R.string.export_original) to -1,
        "2" to 2,
        "5" to 5,
        "10" to 10,
        "15" to 15,
        "25" to 25,
        "50" to 50,
        "150" to 150,
    )
}



