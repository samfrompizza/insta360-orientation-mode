package com.arashivision.sdk.demo.ui.connect

import android.annotation.SuppressLint
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiNetworkSpecifier
import android.text.TextUtils
import androidx.lifecycle.viewModelScope
import com.arashivision.insta360.basecamera.camera.CameraType
import com.arashivision.insta360.basecamera.camera.CameraWifiPrefix
import com.arashivision.sdk.demo.InstaApp
import com.arashivision.sdk.demo.base.BaseViewModel
import com.arashivision.sdk.demo.base.EventStatus
import com.arashivision.sdk.demo.ext.connectedWiFiSsid
import com.arashivision.sdk.demo.ext.connectivityManager
import com.arashivision.sdk.demo.ext.instaCameraManager
import com.arashivision.sdk.demo.ext.wifiManager
import com.arashivision.sdk.demo.service.ConnectService
import com.arashivision.sdk.demo.util.NetworkManager
import com.arashivision.sdk.demo.util.SPUtils
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.callback.ICameraOperateCallback
import com.arashivision.sdkcamera.camera.callback.IScanBleListener
import com.clj.fastble.data.BleDevice
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ConnectViewModel : BaseViewModel() {

    companion object {
        private const val SYSTEM_WIFI_CONNECT_FAILED = 6051
        private const val SYSTEM_WIFI_BIND_NETWORK_FAILED = 6052
        private const val ERROR_CODE_CAMERA_WIFI_NOT_CONNECTED = 11001
        private const val ERROR_CODE_CAMERA_NOT_SUPPORT = 11002

        private const val INSTA_SP_WIFI_INFO_SPLIT_TAG = "#insta_split_tag_#"
    }

    private val logger: Logger = XLog.tag(ConnectViewModel::class.java.simpleName).build()

    private var connectingBleDevice: BleDevice? = null

    var onlyConnectBle: Boolean = false
        private set

    private var isConnectingWiFi = false
    private var isConnectingUsb = false

    val isConnected: Boolean
        get() = instaCameraManager.cameraConnectedType == InstaCameraManager.CONNECT_TYPE_BLE ||
                instaCameraManager.cameraConnectedType == InstaCameraManager.CONNECT_TYPE_WIFI ||
                instaCameraManager.cameraConnectedType == InstaCameraManager.CONNECT_TYPE_USB


    init {
        instaCameraManager.setScanBleListener(object : IScanBleListener {
            override fun onScanStartSuccess() {
                logger.d("onScanStartSuccess")
                emitEvent(ConnectEvent.ScanDeviceEvent(EventStatus.START))
            }

            override fun onScanStartFail() {
                logger.d("onScanStartFail")
                emitEvent(ConnectEvent.ScanDeviceEvent(EventStatus.FAILED))
            }

            override fun onScanning(bleDevice: BleDevice) {
                logger.d("onScanning  -->" + bleDevice.name)
                emitEvent(ConnectEvent.ScanDeviceEvent(EventStatus.PROGRESS, bleDevice = bleDevice))
            }

            override fun onScanFinish(list: List<BleDevice>) {
                logger.d("onScanFinish  -->" + list.size)
                emitEvent(ConnectEvent.ScanDeviceEvent(EventStatus.SUCCESS, bleDeviceList = list))
                instaCameraManager.stopBleScan()
            }
        })
    }

    fun refreshMediaTime() {
        emitEvent(ConnectEvent.RefreshMediaTimeEvent(EventStatus.START))
        instaCameraManager.fetchCameraOptions(object : ICameraOperateCallback {
            override fun onSuccessful() {
                emitEvent(ConnectEvent.RefreshMediaTimeEvent(EventStatus.SUCCESS, instaCameraManager.mediaTime))
            }

            override fun onFailed() {
                emitEvent(ConnectEvent.RefreshMediaTimeEvent(EventStatus.FAILED))
            }

            override fun onCameraConnectError() {
                emitEvent(ConnectEvent.RefreshMediaTimeEvent(EventStatus.FAILED))
            }
        })
    }

    fun connectDeviceByUsb() {
        emitEvent(ConnectEvent.ConnectDeviceEvent(EventStatus.START, InstaCameraManager.CONNECT_TYPE_USB))
        isConnectingUsb = true
        instaCameraManager.openCamera(InstaCameraManager.CONNECT_TYPE_USB)
    }

    fun connectDeviceByWiFi() {
        emitEvent(ConnectEvent.ConnectDeviceEvent(EventStatus.START, InstaCameraManager.CONNECT_TYPE_WIFI))
        val cameraWifiPrefix = CameraWifiPrefix.getCameraWifiPrefixByName(connectedWiFiSsid)
        val cameraType: CameraType = cameraWifiPrefix.cameraTypeV2
        if (cameraType == CameraType.UNKNOWN) {
            emitEvent(ConnectEvent.ConnectDeviceEvent(EventStatus.FAILED, InstaCameraManager.CONNECT_TYPE_WIFI, ERROR_CODE_CAMERA_WIFI_NOT_CONNECTED))
            return
        }
        if (!instaCameraManager.supportCameraType.contains(cameraType)) {
            emitEvent(ConnectEvent.ConnectDeviceEvent(EventStatus.FAILED, InstaCameraManager.CONNECT_TYPE_WIFI, ERROR_CODE_CAMERA_NOT_SUPPORT))
            return
        }
        isConnectingWiFi = true
        instaCameraManager.openCamera(InstaCameraManager.CONNECT_TYPE_WIFI)
    }

    fun startBleScan() {
        instaCameraManager.startBleScan()
    }

    fun connectDeviceByBle(bleDevice: BleDevice, onlyBle: Boolean) {
        emitEvent(ConnectEvent.ConnectDeviceEvent(EventStatus.START, InstaCameraManager.CONNECT_TYPE_BLE))
        instaCameraManager.stopBleScan()
        onlyConnectBle = onlyBle
        // 如：X4 001XXX, X3 1G9JJM
        val wifiInfo: String = SPUtils.getString(bleDevice.name, "")
        if (wifiInfo.contains(INSTA_SP_WIFI_INFO_SPLIT_TAG) && !onlyBle) {
            emitEvent(ConnectEvent.ConnectDeviceEvent(EventStatus.SUCCESS, InstaCameraManager.CONNECT_TYPE_BLE))
            val split = wifiInfo.split(INSTA_SP_WIFI_INFO_SPLIT_TAG.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            connectDeviceByWifi(split[0], split[1])
        } else {
            connectBle(bleDevice)
        }
    }

    private fun connectBle(bleDevice: BleDevice) {
        connectingBleDevice = bleDevice
        instaCameraManager.connectBle(bleDevice)
    }

    private fun connectSystemWifi() {
        // 如：X4 001XXX.OSC, X3 1G9JJM.OSC
        val ssid: String = instaCameraManager.wifiInfo.ssid
        val pwd: String = instaCameraManager.wifiInfo.pwd
        val spKey = ssid.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
        val spValue = ssid + INSTA_SP_WIFI_INFO_SPLIT_TAG + pwd
        SPUtils.putString(spKey, spValue)
        connectDeviceByWifi(ssid, pwd)
    }

    private fun connectDeviceByWifi(ssid: String, pwd: String) {
        emitEvent(ConnectEvent.ConnectDeviceEvent(EventStatus.START, InstaCameraManager.CONNECT_TYPE_WIFI))
        viewModelScope.launch {
            val success = connectSystemWifi(ssid, pwd)
            if (!success) {
                emitEvent(ConnectEvent.ConnectDeviceEvent(EventStatus.FAILED, InstaCameraManager.CONNECT_TYPE_WIFI, SYSTEM_WIFI_CONNECT_FAILED))
                return@launch
            }
            val bindResult = bindNetwork()
            if (bindResult) {
                isConnectingWiFi = true
                instaCameraManager.openCamera(InstaCameraManager.CONNECT_TYPE_WIFI)
            } else {
                emitEvent(ConnectEvent.ConnectDeviceEvent(EventStatus.FAILED, InstaCameraManager.CONNECT_TYPE_WIFI, SYSTEM_WIFI_BIND_NETWORK_FAILED))
            }
        }
    }

    private fun bindNetwork(): Boolean = kotlin.runCatching {
        logger.d("bindNetwork")
        NetworkManager.cameraNet?.let {
            val result = connectivityManager.bindProcessToNetwork(it)
            instaCameraManager.setNetIdToCamera(it.networkHandle)
            logger.d("wiFiNetwork bind result $result")
            return@runCatching result
        } ?: throw IllegalStateException("WiFi network is null")
    }.onFailure {
        logger.d("wiFiNetwork bind exception")
    }.getOrDefault(false)


    private suspend fun connectSystemWifi(ssid: String, pwd: String): Boolean {
        logger.d("connectSystemWiFi  ssid=$ssid   password=$pwd")
        if (!wifiManager.isWifiEnabled) {
            logger.d("wifi disable")
            return false
        }
        if (systemIsConnected(ssid)) {
            logger.d("systemIsConnected true")
            return true
        }
        return suspendCancellableCoroutine {
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(pwd)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {

                private var isResumed = false

                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    logger.d("onAvailable")
                    if(!isResumed){
                        it.resume(true)
                        isResumed = true
                    }
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    logger.d("onUnavailable")
                    if (!isResumed) {
                        it.resume(false)
                        isResumed = true
                    }
                }
            }
            connectivityManager.requestNetwork(request, callback)
        }
    }

    private fun systemIsConnected(ssid: String): Boolean {
        var connectedSsid = wifiManager.connectionInfo.ssid

        // 从SSID中删除周围的引号
        if (connectedSsid != null && connectedSsid.startsWith("\"") && connectedSsid.endsWith("\"")) {
            connectedSsid = connectedSsid.substring(1, connectedSsid.length - 1)
        }
        return connectedSsid != null && connectedSsid.endsWith(ssid)
    }

    fun disconnectCamera() {
        instaCameraManager.closeCamera()
    }

    @SuppressLint("ImplicitSamInstance")
    override fun onCameraStatusChanged(enabled: Boolean, connectType: Int) {
        logger.d("onCameraStatusChanged    enabled=$enabled   cameraConnectedType=$connectType")
        when(connectType){
            InstaCameraManager.CONNECT_TYPE_BLE -> {
                if (enabled && connectingBleDevice != null) {
                    emitEvent(ConnectEvent.ConnectDeviceEvent(EventStatus.SUCCESS, InstaCameraManager.CONNECT_TYPE_BLE))
                    connectingBleDevice = null
                    if (!onlyConnectBle) { connectSystemWifi() }
                } else if (!enabled) {
                    // BLE断连
                    emitEvent(ConnectEvent.CameraDisconnectedEvent)
                }
            }
            InstaCameraManager.CONNECT_TYPE_WIFI, InstaCameraManager.CONNECT_TYPE_USB -> {
                if (enabled && (isConnectingUsb || isConnectingWiFi)) {
                    emitEvent(ConnectEvent.ConnectDeviceEvent(EventStatus.SUCCESS, connectType))
                    InstaApp.instance.startService(Intent(InstaApp.instance, ConnectService::class.java))
                    connectivityManager.bindProcessToNetwork(NetworkManager.mobileNet)
                    isConnectingUsb = false
                    isConnectingWiFi = false
                } else if (!enabled) {
                    // Wi-Fi或者USB断连
                    emitEvent(ConnectEvent.CameraDisconnectedEvent)
                    InstaApp.instance.stopService(Intent(InstaApp.instance, ConnectService::class.java))
                }
            }
        }
    }

    override fun onCameraConnectError(errorCode: Int) {
        if (connectingBleDevice != null) {
            emitEvent(ConnectEvent.ConnectDeviceEvent(EventStatus.FAILED, InstaCameraManager.CONNECT_TYPE_BLE, errorCode))
            connectingBleDevice = null
        } else if (isConnectingWiFi) {
            emitEvent(ConnectEvent.ConnectDeviceEvent(EventStatus.FAILED, InstaCameraManager.CONNECT_TYPE_WIFI, errorCode))
            isConnectingWiFi = false
        } else if (isConnectingUsb) {
            emitEvent(ConnectEvent.ConnectDeviceEvent(EventStatus.FAILED, InstaCameraManager.CONNECT_TYPE_USB, errorCode))
            isConnectingUsb = false
        }
    }


    override fun onCleared() {
        instaCameraManager.unregisterCameraChangedCallback(this)
        instaCameraManager.setScanBleListener(null)
        super.onCleared()
    }
}
