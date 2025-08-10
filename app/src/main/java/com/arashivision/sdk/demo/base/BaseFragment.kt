package com.arashivision.sdk.demo.base

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import com.arashivision.sdk.demo.util.ViewBindingUtils.createBinding
import com.arashivision.sdk.demo.util.ViewBindingUtils.createViewModel
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.gyf.immersionbar.ImmersionBar
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.launch

abstract class BaseFragment<T : ViewBinding, V : BaseViewModel> : Fragment() {

    private val logger: Logger = XLog.tag(BaseFragment::class.java.simpleName).build()

    protected open lateinit var binding: T
    protected open lateinit var viewModel: V
    protected open var disposable: Disposable? = null


    override fun onAttach(context: Context) {
        super.onAttach(context)
        logger.d("[lifecycle] " + javaClass.simpleName + " onAttach")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.d("[lifecycle] " + javaClass.simpleName + " onCreate")
        ImmersionBar.with(this).init()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        logger.d("[lifecycle] " + javaClass.simpleName + " onCreateView")
        this.binding = createBinding(javaClass, inflater, 0, container)
        this.viewModel = createViewModel(this, 1)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logger.d("[lifecycle] " + javaClass.simpleName + " onViewCreated")
        initView()
        initListener()
        // 绑定生命周期（避免内存泄漏）
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.event.collect { onEvent(it) }
        }
    }

    protected open fun initView() {
    }

    protected open fun initListener() {
    }

    protected open fun onEvent(event: BaseEvent?) {
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        logger.d("[lifecycle] " + javaClass.simpleName + " onViewStateRestored")
    }

    override fun onStart() {
        super.onStart()
        logger.d("[lifecycle] " + javaClass.simpleName + " onStart")
    }

    override fun onResume() {
        super.onResume()
        logger.d("[lifecycle] " + javaClass.simpleName + " onResume")
    }

    override fun onPause() {
        super.onPause()
        logger.d("[lifecycle] " + javaClass.simpleName + " onPause")
    }

    override fun onStop() {
        super.onStop()
        logger.d("[lifecycle] " + javaClass.simpleName + " onStop")
    }

    override fun onDestroyView() {
        if (this.disposable != null && !disposable!!.isDisposed) {
            disposable!!.dispose()
        }
        super.onDestroyView()
        logger.d("[lifecycle] " + javaClass.simpleName + " onDestroyView")
    }

    override fun onDestroy() {
        super.onDestroy()
        logger.d("[lifecycle] " + javaClass.simpleName + " onDestroy")
    }

    override fun onDetach() {
        super.onDetach()
        logger.d("[lifecycle] " + javaClass.simpleName + " onDetach")
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        logger.d("[lifecycle] " + javaClass.simpleName + " onHiddenChanged:" + hidden)
    }

    protected fun showLoading() {
        val fragmentActivity = requireActivity()
        if (fragmentActivity is BaseActivity<*, *>) {
            fragmentActivity.showLoading()
        }
    }


    protected fun showLoading(@StringRes id: Int) {
        val fragmentActivity = requireActivity()
        if (fragmentActivity is BaseActivity<*, *>) {
            fragmentActivity.showLoading(id)
        }
    }

    protected fun showLoading(message: String) {
        val fragmentActivity = requireActivity()
        if (fragmentActivity is BaseActivity<*, *>) {
            fragmentActivity.showLoading(message)
        }
    }

    protected fun hideLoading() {
        val fragmentActivity = requireActivity()
        if (fragmentActivity is BaseActivity<*, *>) {
            fragmentActivity.hideLoading()
        }
    }

    protected fun toast(@StringRes id: Int) {
        val fragmentActivity = requireActivity()
        if (fragmentActivity is BaseActivity<*, *>) {
            fragmentActivity.toast(id)
        }
    }

    protected fun toast(@StringRes id: Int, longTime: Boolean) {
        val fragmentActivity = requireActivity()
        if (fragmentActivity is BaseActivity<*, *>) {
            fragmentActivity.toast(id, longTime)
        }
    }

    protected fun toast(message: String?) {
        val fragmentActivity = requireActivity()
        if (fragmentActivity is BaseActivity<*, *>) {
            fragmentActivity.toast(message)
        }
    }

    protected fun toast(message: String?, longTime: Boolean) {
        val fragmentActivity = requireActivity()
        if (fragmentActivity is BaseActivity<*, *>) {
            fragmentActivity.toast(message, longTime)
        }
    }
}
