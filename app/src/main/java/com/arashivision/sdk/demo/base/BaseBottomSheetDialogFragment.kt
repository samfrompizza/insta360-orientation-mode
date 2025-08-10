package com.arashivision.sdk.demo.base

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Outline
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.FragmentManager
import androidx.viewbinding.ViewBinding
import com.arashivision.sdk.demo.R
import com.arashivision.sdk.demo.ext.dp
import com.arashivision.sdk.demo.ext.screenWidth
import com.arashivision.sdk.demo.util.ViewBindingUtils
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ImmersionBar

open class BaseBottomSheetDialogFragment<T : ViewBinding?> : BottomSheetDialogFragment() {

    private val logger: Logger = XLog.tag(BaseBottomSheetDialogFragment::class.java.simpleName).build()

    protected open var binding: T? = null

    /**
     * 是否准备添加到FragmentManager, 调用show时尽管还未真正添加到FragmentManager，但也会设置为true
     */
    private var isReadyToAdd = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog: Dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.setType(WindowManager.LayoutParams.FIRST_SUB_WINDOW)
        return dialog
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        logger.d("[lifecycle] " + javaClass.simpleName + " onAttach")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.d("[lifecycle] " + javaClass.simpleName + " onCreate")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        logger.d("[lifecycle] " + javaClass.simpleName + " onCreateView")
        this.binding = ViewBindingUtils.createBinding<T>(javaClass, inflater, 0, container)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logger.d("[lifecycle] " + javaClass.simpleName + " onViewCreated")
        ImmersionBar.with(this).hideBar(BarHide.FLAG_HIDE_BAR).init()
        initView()
        initListener()
    }

    protected open fun initView() {
    }

    protected open fun initListener() {
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        logger.d("[lifecycle] " + javaClass.simpleName + " onViewStateRestored")
    }

    override fun onStart() {
        logger.d("[lifecycle] " + javaClass.simpleName + " onStart")
        // 如果 activity 为 null 或者 activity 已经销毁，走到这一步不管是否调用super.onStart都会crash，所以try crash一下上报一些信息方便排查
        try {
            super.onStart()
            initRootView()
        } catch (t: Throwable) {
            logger.d("start exception:" + t.message)
        }
        // 设置点击背景时不关闭弹窗
        dialog?.setCanceledOnTouchOutside(false)
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
        this.binding = null
        super.onDestroyView()
        logger.d("[lifecycle] " + javaClass.simpleName + " onDestroyView")
    }

    override fun onDetach() {
        super.onDetach()
        logger.d("[lifecycle] " + javaClass.simpleName + " onDetach")
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        logger.d("[lifecycle] " + javaClass.simpleName + " onHiddenChanged:" + hidden)
    }


    @SuppressLint("RestrictedApi")
    private fun initRootView() {
        view?.parent?.let {
            (it as View).post {
                it.setBackgroundResource(R.color.transparent)
                it.clipToOutline = true
                it.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view1: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view1.width, view1.height * 2, 12f.dp.toFloat())
                    }
                }
                val lp: CoordinatorLayout.LayoutParams = it.layoutParams as CoordinatorLayout.LayoutParams
                val behavior: BottomSheetBehavior<*> = lp.behavior as BottomSheetBehavior<*>
                lp.height = view?.height ?: 0
                behavior.peekHeight = view?.height ?: 0
                lp.width = screenWidth
                behavior.setHideable(false)
            }
        }
    }

    override fun show(manager: FragmentManager, tag: String?) {
        try {
            for (declaredField in this.javaClass.declaredFields) {
                if (declaredField.name == "mDismissed") {
                    declaredField.isAccessible = true
                    declaredField[this] = false
                }
                if (declaredField.name == "mShownByMe") {
                    declaredField.isAccessible = true
                    declaredField[this] = true
                }
            }
        } catch (e: Exception) {
            logger.d("show exception:" + e.message)
            return
        }
        isReadyToAdd = true
        val fragmentTransaction = manager.beginTransaction()
        fragmentTransaction.add(this, tag)
        fragmentTransaction.commitAllowingStateLoss()
    }

    /**
     * 是否准备添加到FragmentManager, 调用show后便会设置为true, 已经被添加到FragmentManager的也会返回true
     *
     * @return Boolean
     */
    fun isReadyToAdd(): Boolean {
        return isReadyToAdd || isAdded
    }

    override fun dismiss() {
        safeFragmentManager?.let {
            isReadyToAdd = false
            super.dismiss()
        }
    }

    private val safeFragmentManager: FragmentManager?
        get() {
            var fragmentManager: FragmentManager? = null
            try {
                fragmentManager = requireFragmentManager()
            } catch (e: IllegalStateException) {
                logger.d("getSafeFragmentManager exception:" + e.message)
            }
            return fragmentManager
        }

    override fun dismissAllowingStateLoss() {
        safeFragmentManager?.let {
            isReadyToAdd = false
            super.dismissAllowingStateLoss()
        }
    }

    override fun onDestroy() {
        isReadyToAdd = false
        logger.d("[lifecycle] " + javaClass.simpleName + " onDestroy")
        super.onDestroy()
    }
}
