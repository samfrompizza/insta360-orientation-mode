package com.arashivision.sdk.demo.di

import android.content.Context
import com.arashivision.sdk.demo.pref.Pref
import com.arashivision.sdk.demo.usb.UsbMgr
import com.arashivision.sdk.demo.util.NetworkManager
import com.arashivision.sdk.demo.util.StorageUtils
import com.arashivision.sdk.demo.util.XLogUtils
import com.arashivision.sdkcamera.InstaCameraSDK
import com.arashivision.sdkcamera.log.LogManager
import com.arashivision.sdkmedia.InstaMediaSDK
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SdkInitializer
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
    ) {
        fun init() {
            val app = appContext as android.app.Application
            UsbMgr.init(app.applicationContext)
            InstaCameraSDK.init(app)
            InstaMediaSDK.init(app)
            XLogUtils.init(app)
            LogManager.instance.logRootPath = StorageUtils.logCacheDir
            if (Pref.getRealTimeCaptureLogs()) {
                LogManager.instance.startLogDumper()
            }
            NetworkManager.startNetworkListener()
        }
    }
