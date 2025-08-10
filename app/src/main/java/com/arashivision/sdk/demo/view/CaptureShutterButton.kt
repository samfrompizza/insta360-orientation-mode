package com.arashivision.sdk.demo.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.arashivision.sdk.demo.R

/**
 * 拍摄页快门按钮
 */
class CaptureShutterButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = -1
) :
    FrameLayout(context, attrs, defStyleAttr) {
    enum class State {
        RECORD_IDLE, RECORDING, CAPTURE_IDLE, CAPTURING
    }

    enum class LongTouchState {
        LONG_TOUCHING, LONG_TOUCH_IDLE
    }

    private var mCurrentState = State.RECORD_IDLE

    private var mCaptureSavingLottieView: LottieAnimationView? = null
    private var mShutterLottieView: LottieAnimationView? = null
    private var mIsNeedLoadingAnim = true
    private var longTouchStateListener: LongTouchStateListener? = null
    private var isLongTouching = false
    private val longTouchRunnable = Runnable {
        isLongTouching = true
        if (longTouchStateListener != null) {
            longTouchStateListener!!.onLongTouchStateListener(LongTouchState.LONG_TOUCHING)
        }
    }

    init {
        init()
    }

    public override fun dispatchSetPressed(pressed: Boolean) {
        super.dispatchSetPressed(pressed)
        if (pressed) {
            postDelayed(longTouchRunnable, LONG_TOUCH_TIME)
        } else {
            removeCallbacks(longTouchRunnable)
            if (isLongTouching) {
                isLongTouching = false
                if (longTouchStateListener != null) {
                    longTouchStateListener!!.onLongTouchStateListener(LongTouchState.LONG_TOUCH_IDLE)
                }
            }
        }
    }

    private fun init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        mShutterLottieView = LottieAnimationView(context)
        mShutterLottieView!!.setBackgroundResource(R.drawable.ic_capture_shutter_shadow)
        mShutterLottieView!!.setImageResource(R.drawable.ic_capture_shutter_record_idle)
        mShutterLottieView!!.imageAssetsFolder = "capture/"
        addView(mShutterLottieView)

        mCaptureSavingLottieView = LottieAnimationView(context)
        mCaptureSavingLottieView!!.setAnimation("capture/capture_capturing_loading.json")
        mCaptureSavingLottieView!!.repeatCount = LottieDrawable.INFINITE
        addView(mCaptureSavingLottieView)
        mCaptureSavingLottieView!!.visibility = INVISIBLE
        post {
            val layoutParams =
                mCaptureSavingLottieView!!.layoutParams as LayoutParams
            layoutParams.gravity = Gravity.CENTER
            mCaptureSavingLottieView!!.layoutParams = layoutParams
        }
    }

    fun setIsNeedLoadingAnim(isNeed: Boolean) {
        mIsNeedLoadingAnim = isNeed
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mShutterLottieView!!.cancelAnimation()
        mCaptureSavingLottieView!!.cancelAnimation()
    }

    private fun updateUI(animate: Boolean, customResId: Int) {
        mShutterLottieView!!.cancelAnimation()
        mCaptureSavingLottieView!!.cancelAnimation()
        when (mCurrentState) {
            State.CAPTURE_IDLE -> {
                mShutterLottieView!!.setImageResource(R.drawable.ic_capture_shutter_capture_idle)
                mCaptureSavingLottieView!!.visibility = INVISIBLE
            }

            State.CAPTURING -> {
                mShutterLottieView!!.setImageResource(R.drawable.ic_capture_shutter_capture_idle)
                if (mIsNeedLoadingAnim) {
                    mCaptureSavingLottieView!!.visibility = VISIBLE
                    mCaptureSavingLottieView!!.playAnimation()
                }
            }

            State.RECORD_IDLE -> {
                if (animate) {
                    mShutterLottieView!!.setAnimation("capture/record_stop.json")
                    mShutterLottieView!!.playAnimation()
                } else {
                    if (customResId == -1) {
                        mShutterLottieView!!.setImageResource(R.drawable.ic_capture_shutter_record_idle)
                    } else {
                        mShutterLottieView!!.setImageResource(customResId)
                    }
                }
                mCaptureSavingLottieView!!.visibility = INVISIBLE
            }

            State.RECORDING -> {
                if (animate) {
                    mShutterLottieView!!.setAnimation("capture/recording.json")
                    mShutterLottieView!!.playAnimation()
                } else {
                    mShutterLottieView!!.setImageResource(R.drawable.ic_capture_shutter_record_working)
                }
                mCaptureSavingLottieView!!.visibility = INVISIBLE
            }
        }
    }

    fun setState(state: State, customResId: Int) {
        if (mCurrentState != state || customResId != -1) {
            val animate = ((mCurrentState == State.RECORD_IDLE && state == State.RECORDING)
                    || (mCurrentState == State.RECORDING && state == State.RECORD_IDLE))
                    && customResId == -1
            mCurrentState = state
            updateUI(animate, customResId)
        }
    }

    fun setState(state: State) {
        setState(state, -1)
    }

    fun setOnLongTouchStateListener(longTouchStateListener: LongTouchStateListener?) {
        this.longTouchStateListener = longTouchStateListener
    }

    interface LongTouchStateListener {
        fun onLongTouchStateListener(state: LongTouchState?)
    }

    companion object {
        const val LONG_TOUCH_TIME: Long = 500L
    }
}