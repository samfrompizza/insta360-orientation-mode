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
import com.arashivision.sdk.demo.base.R as BaseR

open class BaseActivity<T : ViewBinding, V : BaseViewModel>(
    private val bindingFactory: (LayoutInflater) -> T,
    private val viewModelClass: Class<V>,
) : AppCompatActivity() {
    companion object {
        private var isCharging: Boolean = false
        private const val MIN_LOADING_TIME = 100L
        private const val STORAGE_WARNING_THRESHOLD = 0.8f
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
                if (msg.what == BaseR.integer.loading_hide_what) {
                    hideLoading()
                } else if (msg.what == BaseR.integer.loading_show_what) {
                    val poll = loadingTask.poll()
                    if (poll != null) {
                        show(poll)
                    }
                    sendEmptyMessageDelayed(BaseR.integer.loading_show_what, MIN_LOADING_TIME)
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
                toast(BaseR.string.camera_battery_low)
            }

            is CameraStorageChangedEvent -> {
                if (event.freeSpace.toFloat() / event.totalSpace < STORAGE_WARNING_THRESHOLD) {
                    toast(BaseR.string.camera_storage_full_soon)
                }
            }

            is CameraBatteryUpdateEvent -> {
                if (event.isCharging && !isCharging) {
                    isCharging = true
                    toast(getString(BaseR.string.camera_battery_charging, event.batteryLevel))
                } else if (!event.isCharging && isCharging) {
                    isCharging = false
                    toast(BaseR.string.camera_battery_stop_charging)
                }
            }

            is CameraSDCardStateChangedEvent -> {
                toast(if (event.enabled) BaseR.string.camera_sd_card_insert else BaseR.string.camera_sd_card_extract)
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
        loading?.let {
            runCatching { it.dismiss() }
        }
        loading = null
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
        if (!handler.hasMessages(BaseR.integer.loading_show_what)) {
            handler.sendEmptyMessage(BaseR.integer.loading_show_what)
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
            handler.removeMessages(BaseR.integer.loading_show_what)
            loading?.let {
                if (it.isAdded) {
                    val nowTime = System.currentTimeMillis()
                    val loadingTime = nowTime - startLoadingTime
                    if (loadingTime < MIN_LOADING_TIME) {
                        handler.sendEmptyMessageDelayed(BaseR.integer.loading_hide_what, MIN_LOADING_TIME - loadingTime)
                    } else {
                        loading!!.dismiss()
                        loading = null
                    }
                } else {
                    handler.sendEmptyMessageDelayed(BaseR.integer.loading_hide_what, 100)
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
        application?.let {
            val toast = Toast.makeText(it, message, Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.TOP, 0, 0)
            toast.show()
        }
    }

    fun lastToast(
        @StringRes id: Int,
    ) {
        lastToast(getString(id))
    }

    fun lastToast(
        @StringRes id: Int,
        longTime: Boolean,
    ) {
        lastToast(getString(id), longTime)
    }

    fun lastToast(
        message: String,
        longTime: Boolean,
    ) {
        application?.let {
            val toast = Toast.makeText(it, message, if (longTime) Toast.LENGTH_LONG else Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.TOP, 0, 0)
            toast.show()
        }
    }
}
