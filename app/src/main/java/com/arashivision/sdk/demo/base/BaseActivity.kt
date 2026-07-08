package com.arashivision.sdk.demo.base

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import com.arashivision.sdk.demo.InstaApp
import com.arashivision.sdk.demo.R
import com.arashivision.sdk.demo.base.BaseEvent.CameraBatteryUpdateEvent
import com.arashivision.sdk.demo.base.BaseEvent.CameraSDCardStateChangedEvent
import com.arashivision.sdk.demo.base.BaseEvent.CameraStorageChangedEvent
import com.arashivision.sdk.demo.view.LoadingView
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ImmersionBar
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.LinkedList

open class BaseActivity<T : ViewBinding, V : BaseViewModel>(
    private val bindingFactory: (LayoutInflater) -> T,
    private val viewModelClass: Class<V>,
) : AppCompatActivity() {
    companion object {
        private var isCharging: Boolean = false
        private const val MIN_LOADING_TIME = 100L
        private const val STORAGE_WARNING_THRESHOLD = 0.8f
        private const val LOADING_HIDE_WHAT = 1000
        private const val LOADING_SHOW_WHAT = 1001
    }

    private val logger: Logger = XLog.tag(BaseActivity::class.java.simpleName).build()

    protected val binding: T by lazy { bindingFactory(layoutInflater) }

    val viewModel: V by lazy { ViewModelProvider(this)[viewModelClass] }

    private var loading: LoadingView? = null

    private var startLoadingTime = 0L
    private val loadingTask = LinkedList<String>()

    protected open val handler by lazy {
        object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                if (msg.what == LOADING_HIDE_WHAT) {
                    hideLoading()
                } else if (msg.what == LOADING_SHOW_WHAT) {
                    val poll = loadingTask.poll()
                    if (poll != null) {
                        show(poll)
                    }
                    sendEmptyMessageDelayed(LOADING_SHOW_WHAT, MIN_LOADING_TIME)
                }
                onMessage(msg)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        logger.d("[lifecycle] " + javaClass.simpleName + " onCreate")
        super.onCreate(savedInstanceState)
        ImmersionBar.with(this).hideBar(BarHide.FLAG_HIDE_BAR).init()
        setContentView(binding.root)
        initView()
        initListener()
        lifecycleScope.launch {
            viewModel.event.collect { onEvent(it) }
        }
    }

    protected open fun initView() {
    }

    protected open fun initListener() {
    }

    protected open fun onMessage(msg: Message) {}

    protected open fun onEvent(event: BaseEvent) {
        logger.d(event::class.java.simpleName)

        when (event) {
            is BaseEvent.CameraBatteryLowEvent -> {
                toast(R.string.camera_battery_low)
            }

            is CameraStorageChangedEvent -> {
                if (event.freeSpace.toFloat() / event.totalSpace < STORAGE_WARNING_THRESHOLD) {
                    toast(R.string.camera_storage_full_soon)
                }
            }

            is CameraBatteryUpdateEvent -> {
                if (event.isCharging && !isCharging) {
                    isCharging = true
                    toast(getString(R.string.camera_battery_charging, event.batteryLevel))
                } else if (!event.isCharging && isCharging) {
                    isCharging = false
                    toast(getString(R.string.camera_battery_stop_charging))
                }
            }

            is CameraSDCardStateChangedEvent -> {
                toast(if (event.enabled) R.string.camera_sd_card_insert else R.string.camera_sd_card_extract)
            }

            is BaseEvent.CameraStatusChangedEvent -> {}
        }
    }

    override fun onStart() {
        super.onStart()
        logger.d("[lifecycle] " + javaClass.simpleName + " onStart")
    }

    override fun onPause() {
        super.onPause()
        logger.d("[lifecycle] " + javaClass.simpleName + " onPause")
    }

    override fun onRestart() {
        super.onRestart()
        logger.d("[lifecycle] " + javaClass.simpleName + " onRestart")
    }

    override fun onResume() {
        super.onResume()
        logger.d("[lifecycle] " + javaClass.simpleName + " onResume")
    }

    override fun onStop() {
        super.onStop()
        logger.d("[lifecycle] " + javaClass.simpleName + " onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        logger.d("[lifecycle] " + javaClass.simpleName + " onDestroy")
        lifecycleScope.cancel()
    }

    fun showLoading() {
        showLoading("")
    }

    fun showLoading(
        @StringRes id: Int,
    ) {
        showLoading(getString(id))
    }

    fun showLoading(message: String) {
        loadingTask.add(message)
        if (!handler.hasMessages(LOADING_SHOW_WHAT)) {
            handler.sendEmptyMessage(LOADING_SHOW_WHAT)
        }
    }

    private fun show(message: String) {
        val nowTime = System.currentTimeMillis()
        if (loading != null) {
            loading!!.setMessage(message)
        } else {
            loading = LoadingView(message)
            loading!!.show(supportFragmentManager, "tag_loading")
        }
        startLoadingTime = nowTime
    }

    fun hideLoading() {
        handler.post {
            handler.removeMessages(LOADING_SHOW_WHAT)
            loading?.let {
                if (it.isAdded) {
                    val nowTime = System.currentTimeMillis()
                    val loadingTime = nowTime - startLoadingTime
                    if (loadingTime < MIN_LOADING_TIME) {
                        handler.sendEmptyMessageDelayed(LOADING_HIDE_WHAT, MIN_LOADING_TIME - loadingTime)
                    } else {
                        loading!!.dismiss()
                        loading = null
                    }
                } else {
                    handler.sendEmptyMessageDelayed(LOADING_HIDE_WHAT, 100)
                }
            }
        }
    }

    fun toast(message: String?) {
        toast(message, false)
    }

    fun toast(
        @StringRes id: Int,
    ) {
        toast(id, false)
    }

    fun toast(
        @StringRes id: Int,
        longTime: Boolean,
    ) {
        if (longTime) {
            toast(getString(id), Toast.LENGTH_LONG)
        } else {
            toast(getString(id), Toast.LENGTH_SHORT)
        }
    }

    fun toast(
        message: String?,
        longTime: Boolean,
    ) {
        if (longTime) {
            toast(message, Toast.LENGTH_LONG)
        } else {
            toast(message, Toast.LENGTH_SHORT)
        }
    }

    private fun toast(
        message: String?,
        duration: Int,
    ) {
        val toast = Toast.makeText(this, message, duration)
        toast.setGravity(Gravity.TOP, 0, 0)
        toast.show()
    }

    fun lastToast(message: String?) {
        (InstaApp.instance.lastActivity as? BaseActivity<*, *>)?.toast(message, false)
    }

    fun lastToast(
        @StringRes id: Int,
    ) {
        (InstaApp.instance.lastActivity as? BaseActivity<*, *>)?.toast(id, false)
    }

    fun lastToast(
        @StringRes id: Int,
        longTime: Boolean,
    ) {
        (InstaApp.instance.lastActivity as? BaseActivity<*, *>)?.toast(getString(id), longTime)
    }

    fun lastToast(
        message: String,
        longTime: Boolean,
    ) {
        (InstaApp.instance.lastActivity as? BaseActivity<*, *>)?.toast(message, longTime)
    }
}
