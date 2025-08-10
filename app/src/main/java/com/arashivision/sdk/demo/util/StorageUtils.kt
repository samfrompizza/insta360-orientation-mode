package com.arashivision.sdk.demo.util

import android.os.Environment
import com.arashivision.sdk.demo.InstaApp


object StorageUtils {

    private val sRootPathCache: String by lazy {
        InstaApp.instance.getExternalFilesDir("")?.absolutePath ?: ""
    }


    val logCacheDir: String
        get() = "$sRootPathCache/log"

    val logZipDir: String
        get() = "$sRootPathCache/zip"

    val workDir: String
        get() = "$sRootPathCache/work"

    val exportVideoDir: String
        get() {
            return "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)}/insta360/export/video"
        }

    val exportImageDir: String
        get() {
            return "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)}/insta360/export/image"
        }

    val hdrStitchDir: String
        get() = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)}/insta360/stitch/hdr"

    val pureShotStitchDir: String
        get() = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)}/insta360/stitch/pure_shot/"

}
