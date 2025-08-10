package com.arashivision.sdk.demo.ext

import java.util.Locale


fun Long.gb(): String {
    return String.format(Locale.ENGLISH, "%.1f", this / 1024f / 1024f / 1024f) + "GB"
}

fun Long.mb(): String {
    return String.format(Locale.ENGLISH, "%.2f", this / 1024f / 1024f) + "GB"
}