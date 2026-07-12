package com.arashivision.sdk.demo.pref

import androidx.preference.PreferenceManager
import com.arashivision.sdk.demo.base.AppContext

object Pref {
    private const val KEY_STAB_CACHE_FRAME_NUM = "pref_stab_cache_frame_num"
    private const val KEY_REAL_TIME_CAPTURE_LOGS = "pref_real_time_capture_logs"
    private const val KEY_LIVE_RTMP = "pref_live_rtmp"
    private const val KEY_LIVE_BIND_MOBILE_NETWORK = "pref_live_bind_mobile_network"

    fun getStabCacheFrameNum(): Int {
        val sp = PreferenceManager.getDefaultSharedPreferences(AppContext.application)
        return sp
            .getString(KEY_STAB_CACHE_FRAME_NUM, "0")
            ?.toInt() ?: 0
    }

    fun getRealTimeCaptureLogs(): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(AppContext.application)
        return sp.getBoolean(KEY_REAL_TIME_CAPTURE_LOGS, true)
    }

    fun getLiveRtmp(): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(AppContext.application)
        return sp.getString(KEY_LIVE_RTMP, "") ?: ""
    }

    fun getLiveBindMobileNetwork(): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(AppContext.application)
        return sp.getBoolean(KEY_LIVE_BIND_MOBILE_NETWORK, true)
    }
}
