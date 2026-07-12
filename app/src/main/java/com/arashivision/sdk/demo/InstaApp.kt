package com.arashivision.sdk.demo

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.os.Bundle
import com.arashivision.sdk.demo.base.AppContext
import com.arashivision.sdk.demo.di.SdkInitializer
import dagger.hilt.android.HiltAndroidApp
import java.util.function.Function
import javax.inject.Inject

@HiltAndroidApp
class InstaApp :
    Application(),
    ActivityLifecycleCallbacks {
    @Inject lateinit var sdkInitializer: SdkInitializer

    var topActivity: Activity? = null
        private set
    var lastActivity: Activity? = null
        private set

    private val resumeTaskList: MutableList<Function<Activity, Boolean>> = ArrayList()
    private val pauseTaskList: MutableList<Function<Activity, Boolean>> = ArrayList()

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var _instance: InstaApp? = null

        val instance: InstaApp
            get() = _instance ?: error("InstaApp has not been initialized")
    }

    override fun onCreate() {
        super.onCreate()
        _instance = this
        AppContext.init(this)
        registerActivityLifecycleCallbacks(this)
        sdkInitializer.init()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        _instance = this
    }

    override fun onTerminate() {
        super.onTerminate()
        unregisterActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
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

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) {
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
