package com.arashivision.sdk.demo.ui.capture

import android.os.Handler
import android.os.Looper
import android.view.View
import com.arashivision.sdk.demo.core.vr.VrSourceView
import com.arashivision.sdk.demo.ext.instaCameraManager
import com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView
import com.arashivision.sdkmedia.player.listener.PlayerViewListener
import com.elvishew.xlog.XLog

class CapturePlayerVrSource(
    private val activity: CaptureActivity,
    private val mainPlayerView: InstaCapturePlayerView,
) : VrSourceView {
    private val logger = XLog.tag("CapturePlayerVrSource").build()
    private var secondPlayerView: InstaCapturePlayerView? = null

    override val contentView: View
        get() = secondPlayerView ?: mainPlayerView

    override fun onVrEnabled() {
        createSecondPlayer()
    }

    override fun onVrDisabled() {
        mainPlayerView.visibility = View.VISIBLE
        mainPlayerView.play()
        runCatching {
            instaCameraManager.setPipeline(mainPlayerView.pipeline)
        }
        val viewToDestroy = secondPlayerView
        secondPlayerView = null
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { viewToDestroy?.destroy() }
        }, VR_POST_DESTROY_DELAY_MS)
    }

    override fun applyOrientation(
        yawDeg: Float,
        pitchDeg: Float,
    ) {
        val view = secondPlayerView ?: return
        try {
            val cls = view.javaClass
            try {
                val mYaw = cls.getMethod("setYaw", Float::class.javaPrimitiveType)
                mYaw.invoke(view, yawDeg + VR_IPD_YAW_DEG + VR_LENS_OFFSET_DEG)
            } catch (_: NoSuchMethodException) {
            }
            try {
                val mPitch = cls.getMethod("setPitch", Float::class.javaPrimitiveType)
                mPitch.invoke(view, pitchDeg)
            } catch (_: NoSuchMethodException) {
            }
        } catch (e: Exception) {
            logger.e("applyOrientation error: ${e.message}")
        }
    }

    private fun createSecondPlayer() {
        mainPlayerView.visibility = View.INVISIBLE

        secondPlayerView =
            InstaCapturePlayerView(activity).apply {
                setLifecycle((activity as? androidx.fragment.app.FragmentActivity)?.lifecycle)
                keepScreenOn = true
            }
        secondPlayerView?.setPlayerViewListener(
            object : PlayerViewListener {
                override fun onFirstFrameRender() {
                    logger.i("Second player first frame rendered")
                }

                override fun onLoadingFinish() {
                    runCatching {
                        instaCameraManager.setPipeline(secondPlayerView?.pipeline)
                    }
                }

                override fun onReleaseCameraPipeline() {
                    logger.d("Second player pipeline released (ignoring)")
                }
            },
        )
        runCatching {
            val params = activity.viewModel.getCaptureParams()
            secondPlayerView?.prepare(params)
        }
        secondPlayerView?.play()
        copyPlayerZoom()
    }

    private fun copyPlayerZoom() {
        val src = mainPlayerView
        val dst = secondPlayerView ?: return
        val zoomMethods = listOf("getZoom" to "setZoom", "getFov" to "setFov")
        for ((getter, setter) in zoomMethods) {
            try {
                val mGet = src.javaClass.getMethod(getter)
                val mSet = dst.javaClass.getMethod(setter, Float::class.javaPrimitiveType)
                val value = mGet.invoke(src) as? Float ?: continue
                mSet.invoke(dst, value)
                logger.i("Copied $getter=${value} to second player")
                return
            } catch (_: NoSuchMethodException) {
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val VR_IPD_YAW_DEG = 3.0f
        private const val VR_LENS_OFFSET_DEG = 180.0f
        private const val VR_POST_DESTROY_DELAY_MS = 500L
    }
}
