package com.arashivision.sdk.demo.pref

import androidx.preference.PreferenceManager
import com.arashivision.sdk.demo.InstaApp
import com.arashivision.sdk.demo.R

object Pref {

    fun getStabCacheFrameNum(): Int {
        val sp = PreferenceManager.getDefaultSharedPreferences(InstaApp.instance)
        return sp.getString(InstaApp.instance.getString(R.string.pref_stab_cache_frame_num), "0")
            ?.toInt() ?: 0
    }

    fun getRealTimeCaptureLogs(): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(InstaApp.instance)
        return sp.getBoolean(
            InstaApp.instance.getString(R.string.pref_real_time_capture_logs),
            true
        )
    }

    fun getLiveRtmp(): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(InstaApp.instance)
        return sp.getString(InstaApp.instance.getString(R.string.pref_live_rtmp), "") ?: ""
    }

    fun getLiveBindMobileNetwork(): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(InstaApp.instance)
        return sp.getBoolean(InstaApp.instance.getString(R.string.pref_live_bind_mobile_network), true)
    }
}