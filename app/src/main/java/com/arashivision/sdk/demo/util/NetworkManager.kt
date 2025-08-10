package com.arashivision.sdk.demo.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.text.TextUtils
import com.arashivision.sdk.demo.InstaApp
import com.arashivision.sdk.demo.ext.connectivityManager
import com.arashivision.sdk.demo.ext.wifiManager
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog


object NetworkManager {

    private val logger: Logger = XLog.tag(NetworkManager::class.java.getSimpleName()).build()

    var mobileNet: Network? = null
        private set

    var wifiNet: Network?  = null

    val cameraNet: Network?
        get() {
            connectivityManager.allNetworks.forEach { network ->
                connectivityManager.getNetworkCapabilities(network)?.let {
                    if (!it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        return@forEach
                    }
                    if (it.transportInfo is WifiInfo) {
                        val wifiInfoAddress =
                            parseIpAddress((it.transportInfo as WifiInfo).ipAddress)
                        val connectionInfoAddress =
                            parseIpAddress(wifiManager.connectionInfo.ipAddress)
                        if (TextUtils.equals(wifiInfoAddress, connectionInfoAddress)) {
                            return network
                        }
                    }
                }
            }
            return null
        }

    private var mNetworkCallback: ConnectivityManager.NetworkCallback? = null

    fun startNetworkListener() {
        val connManager: ConnectivityManager =
            InstaApp.instance.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        mNetworkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                logger.d("onAvailable ==>" + network.networkHandle)
                connManager.getNetworkCapabilities(network)?.also {
                    if (it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        wifiNet = network
                    } else if (it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        mobileNet = network
                    }
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                logger.d("onLost ==>" + network.networkHandle)
                connManager.getNetworkCapabilities(network)?.also {
                    if (it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        mobileNet = null
                    } else if (it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        wifiNet = null
                    }
                }
            }
        }
        connManager.registerNetworkCallback(request, mNetworkCallback!!)
    }

    private fun parseIpAddress(ip: Int): String {
        return ((ip and 0xFF).toString() + "." +  // 提取第 4 字节（最低位）
                ((ip ushr 8) and 0xFF) + "." +  // 提取第 3 字节
                ((ip ushr 16) and 0xFF) + "." +  // 提取第 2 字节
                ((ip ushr 24) and 0xFF)) // 提取第 1 字节（最高位）
    }
}
