package com.arashivision.sdk.demo.ext

import android.app.Service
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.VibrationEffect
import android.os.Vibrator
import com.arashivision.sdk.demo.InstaApp

val connectedWiFiSsid: String
    get() {
        val wifiManager = InstaApp.instance.getSystemService(Context.WIFI_SERVICE) as WifiManager
        var ssid = wifiManager.connectionInfo.ssid
        if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length - 1)
        }
        return ssid
    }


fun vibrate(secMill: Long, amplitude: Int) {
    val vibrator = InstaApp.instance.getSystemService(Service.VIBRATOR_SERVICE) as Vibrator
    vibrator.vibrate(VibrationEffect.createOneShot(secMill, amplitude))
}

val connectivityManager: ConnectivityManager
    get() = InstaApp.instance.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager


val wifiManager: WifiManager
    get() = InstaApp.instance.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager