package com.arashivision.sdk.demo.view.picker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arashivision.sdk.demo.R
import com.arashivision.sdk.demo.databinding.DialogFragmentPlayerSettingBinding
import com.arashivision.sdk.demo.ext.dp


class PickerView : FrameLayout {
    companion object {
        private const val ANIMATION_DURATION: Long = 300L // 动画持续时间(毫秒)
        private const val DEFAULT_TRANSPARENT_DURATION: Long = 1000L // 立即生效时，整个View变成透明的时间
    }

    private val showTranslateAnimation: Animation by lazy {
        TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 1f,
            Animation.RELATIVE_TO_SELF, 0f
        ).apply {
            duration = ANIMATION_DURATION
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {
                    isAnimating = true
                }

                override fun onAnimationEnd(animation: Animation) {
                    isAnimating = false
                }

                override fun onAnimationRepeat(animation: Animation) {
                    // 不需要实现
                }
            })
        }
    }

    private val hideTranslateAnimation: Animation by lazy {
        TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,  // 从完全可见
            Animation.RELATIVE_TO_SELF, 1f// 到底部(完全不可见)
        ).apply {
            duration = ANIMATION_DURATION
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {
                    isAnimating = true
                }

                override fun onAnimationEnd(animation: Animation) {
                    isAnimating = false
                    setFullScreen(false)
                    visibility = GONE
                }

                override fun onAnimationRepeat(animation: Animation) {
                    // 不需要实现
                }
            })
        }
    }

    private var isAnimating = false

    private var immediateEffectiveTransparent = true

    private var itemListener: ((position: Int, data: Any) -> Unit)? = null

    private var applyListener: ((data: List<Any?>) -> Unit)? = null

    private var downY = 0f

    private var effectiveMode = EffectiveMode.IMMEDIATE

    private var applyData: MutableList<Any?> = mutableListOf()

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    private var binding: DialogFragmentPlayerSettingBinding =
        DialogFragmentPlayerSettingBinding.inflate(LayoutInflater.from(context))

    private val adapter = PickerAdapter()

    init {
        addView(binding.root)
        setFullScreen(false)
        initView()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initView() {
        binding.tvApply.visibility =
            if (effectiveMode == EffectiveMode.APPLY) View.VISIBLE else View.GONE

        binding.rvPlayerSetting.setAdapter(adapter)
        binding.rvPlayerSetting.setLayoutManager(
            LinearLayoutManager(
                context,
                RecyclerView.VERTICAL,
                false
            )
        )

        binding.rvPlayerSetting.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                super.getItemOffsets(outRect, view, parent, state)
                val position: Int = parent.getChildAdapterPosition(view)
                if (position != 0) outRect.top = 20f.dp
            }
        })

        setOnClickListener {
            if (isAnimating) return@setOnClickListener
            hide()
        }

        binding.root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                }

                MotionEvent.ACTION_UP -> {
                    if (event.rawY - downY > 50) {
                        setFullScreen(false)
                    }
                    if (event.rawY - downY < -50) {
                        setFullScreen(true)
                    }
                }
            }
            true
        }

        binding.ivClose.setOnClickListener {
            hide()
        }

        binding.tvApply.setOnClickListener {
            applyListener?.invoke(applyData)
            applyData.clear()
            hide()
        }

        adapter.setOnItemSelectListener { position, data ->
            when (effectiveMode) {
                EffectiveMode.APPLY -> {
                    applyData[position] = data
                }

                else -> {
                    if (immediateEffectiveTransparent) startAlphaAnimation()
                    itemListener?.invoke(position, data)
                }
            }
        }
    }

    private fun startAlphaAnimation() {
        val fadeOut = AlphaAnimation(1.0f, 0.2f)
        fadeOut.duration = ANIMATION_DURATION

        val fadeIn = AlphaAnimation(0.2f, 1.0f)
        fadeIn.duration = ANIMATION_DURATION

        fadeIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
                visibility = VISIBLE
                isAnimating = false
            }

            override fun onAnimationEnd(animation: Animation?) {

            }

            override fun onAnimationRepeat(animation: Animation?) {

            }

        })

        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                visibility = INVISIBLE
                postDelayed({ startAnimation(fadeIn) }, DEFAULT_TRANSPARENT_DURATION)
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })
        startAnimation(fadeOut)
        isAnimating = true
    }

    fun setOnItemClickListener(listener: (position: Int, data: Any) -> Unit) {
        itemListener = listener
    }

    fun setOnApplyClickListener(listener: (data: List<Any?>) -> Unit) {
        applyListener = listener
    }

    fun setData(data: List<PickData>) {
        applyData.clear()

        data.forEach {
            println("${it.title}   size=${it.options.size}  index=${it.currentPosition}")
            applyData.add(it.options.getOrNull(it.currentPosition)?.second)
        }
        adapter.setData(data.toMutableList())
    }

    fun setTitleText(title: String) {
        binding.tvTitle.text = title
    }

    fun setApplyText(apply: String) {
        binding.tvApply.text = apply
    }

    private fun isFullScreen(): Boolean {
        return binding.root.layoutParams.height == ViewGroup.LayoutParams.MATCH_PARENT
    }

    private fun setFullScreen(full: Boolean) {
        if (isFullScreen() == full) return
        val params = binding.root.layoutParams as LayoutParams
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = if (full) ViewGroup.LayoutParams.MATCH_PARENT else 560f.dp
        params.gravity = Gravity.BOTTOM
        binding.root.setLayoutParams(params)

        binding.ivClose.visibility = if (full) View.VISIBLE else View.GONE
        binding.tvSlideTip.text =
            context.getString(if (full) R.string.slide_down_full_screen else R.string.slide_up_full_screen)
    }

    fun show(isFullScreen: Boolean = false) {
        if (isVisible || isAnimating) {
            return
        }
        if (isFullScreen) setFullScreen(true)

        visibility = VISIBLE
        binding.root.startAnimation(showTranslateAnimation)
    }

    fun hide() {
        if (!isVisible || isAnimating) {
            return
        }
        binding.root.startAnimation(hideTranslateAnimation)
    }

    fun setEffectiveMode(effectiveMode: EffectiveMode) {
        this.effectiveMode = effectiveMode
        applyData.clear()
        binding.tvApply.visibility =
            if (effectiveMode == EffectiveMode.APPLY) View.VISIBLE else View.GONE
    }

    fun setImmediateEffectiveTransparent(enable: Boolean) {
        immediateEffectiveTransparent = enable
    }

}