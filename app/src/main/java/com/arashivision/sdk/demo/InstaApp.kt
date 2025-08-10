package com.arashivision.sdk.demo

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.os.Bundle
import com.arashivision.sdk.demo.pref.Pref
import com.arashivision.sdk.demo.usb.UsbMgr
import com.arashivision.sdk.demo.util.NetworkManager
import com.arashivision.sdk.demo.util.StorageUtils
import com.arashivision.sdk.demo.util.XLogUtils
import com.arashivision.sdkcamera.InstaCameraSDK
import com.arashivision.sdkcamera.camera.model.RecordResolution
import com.arashivision.sdkcamera.log.LogManager
import com.arashivision.sdkmedia.InstaMediaSDK
import java.util.function.Function

class InstaApp : Application(), ActivityLifecycleCallbacks {

    var topActivity: Activity? = null
        private set
    var lastActivity: Activity? = null
        private set

    private val resumeTaskList: MutableList<Function<Activity, Boolean>> = ArrayList()
    private val pauseTaskList: MutableList<Function<Activity, Boolean>> = ArrayList()

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var instance: InstaApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerActivityLifecycleCallbacks(this)
        UsbMgr.init(this.applicationContext)
        InstaCameraSDK.init(this)
        InstaMediaSDK.init(this)
        XLogUtils.init(this)

        // 设置日志缓存路径，如不设置，则不缓存
        RecordResolution.CAPTURE_3840_1920_100FPS
        LogManager.instance.logRootPath = StorageUtils.logCacheDir

        // 开启日志实时抓取
        if (Pref.getRealTimeCaptureLogs()) LogManager.instance.startLogDumper()

        // 开启Network监听
        NetworkManager.startNetworkListener()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        instance = this
    }

    override fun onTerminate() {
        super.onTerminate()
        unregisterActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
        topActivity = activity
        val temp: MutableList<Function<Activity, Boolean>> = ArrayList()
        for (task in resumeTaskList) {
            val apply = task.apply(activity)
            if (!apply) temp.add(task)
        }
        resumeTaskList.clear()
        resumeTaskList.addAll(temp)
    }

    override fun onActivityPaused(activity: Activity) {
        lastActivity = activity
        topActivity = null
        val temp: MutableList<Function<Activity, Boolean>> = ArrayList()
        for (task in pauseTaskList) {
            val apply = task.apply(activity)
            if (!apply) temp.add(task)
        }
        pauseTaskList.clear()
        pauseTaskList.addAll(temp)
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (lastActivity === activity) lastActivity = null
    }

    fun addActivityResumedTask(task: Function<Activity, Boolean>) {
        if (topActivity != null) task.apply(topActivity!!)
        synchronized(this) {
            if (!resumeTaskList.contains(task)) {
                resumeTaskList.add(task)
            }
        }
    }

    fun addActivityPausedTask(task: Function<Activity, Boolean>) {
        synchronized(this) {
            if (!pauseTaskList.contains(task)) {
                pauseTaskList.add(task)
            }
        }
    }

    fun removeActivityResumedTask(task: Function<Activity, Boolean>) {
        synchronized(this) {
            if (resumeTaskList.contains(task)) {
                resumeTaskList.remove(task)
            }
        }
    }

    fun removeActivityPausedTask(task: Function<Activity, Boolean>) {
        synchronized(this) {
            if (pauseTaskList.contains(task)) {
                pauseTaskList.remove(task)
            }
        }
    }
}