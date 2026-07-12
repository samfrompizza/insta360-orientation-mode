package com.arashivision.sdk.demo.ext

import android.app.Service
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.VibrationEffect
import android.os.Vibrator
import com.arashivision.sdk.demo.base.AppContext

val connectedWiFiSsid: String
    get() {
        val wifiManager = AppContext.application.getSystemService(Context.WIFI_SERVICE) as WifiManager
        var ssid: String = wifiManager.connectionInfo?.ssid ?: WifiManager.UNKNOWN_SSID
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length - 1)
        }
        return ssid
    }

fun vibrate(
    secMill: Long,
    amplitude: Int,
) {
    val vibrator = AppContext.application.getSystemService(Service.VIBRATOR_SERVICE) as Vibrator
    vibrator.vibrate(VibrationEffect.createOneShot(secMill, amplitude))
}

val connectivityManager: ConnectivityManager
    get() = AppContext.application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

val wifiManager: WifiManager
    get() = AppContext.application.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
