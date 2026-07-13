package com.arashivision.sdk.demo.ui.capture

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
        secondPlayerView?.destroy()
        secondPlayerView = null
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
                    runCatching {
                        instaCameraManager.setPipeline(null)
                    }
                }
            },
        )
        runCatching {
            val params = activity.viewModel.getCaptureParams()
            secondPlayerView?.prepare(params)
        }
        secondPlayerView?.play()
    }

    companion object {
        private const val VR_IPD_YAW_DEG = 3.0f
        private const val VR_LENS_OFFSET_DEG = 180.0f
    }
}
