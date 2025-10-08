package com.arashivision.sdk.demo.ui.capture
import android.app.Activity
import android.graphics.Bitmap as AndroidBitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.view.drawToBitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.arashivision.sdk.demo.ext.instaCameraManager
import com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView
import com.arashivision.sdkmedia.player.listener.PlayerViewListener
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
/**
VrManager — отдельный класс, который включает/выключает VR-режим.

Примечание: этот класс использует копирование битмапов из правого плеера в ImageView слева.
 */
class VrManager(
    private val activity: Activity,
    private val rootContainer: ViewGroup?,
    private val capturePlayerView: InstaCapturePlayerView,
    private val svCaptureMode: View,
    private val ivCaptureSetting: View,
    private val btnCalibrate: View,
    private val calibrateGyro: () -> Unit = {}
) {
    private val logger: Logger = XLog.tag("VrManager").build()
    var isVrMode: Boolean = false
        private set
    private var vrContainer: ViewGroup? = null
    private var leftVrImage: ImageView? = null
    private var rightVrPlayer: InstaCapturePlayerView? = null
    private var reusableBitmap: AndroidBitmap? = null
    private var compositeBitmap: AndroidBitmap? = null  // для композитинга (opaque)
    private val handler = Handler(Looper.getMainLooper())
    private var copyRunnable: Runnable? = null
    private var copying = false
    private val copyIntervalMs: Long = 33L // ~30 fps
    private val vrIpdYawDeg: Float = 3.0f
    init {
        logger.i("VrManager created")
    }
    fun toggleVrMode() {
        if (isVrMode) disableVrMode() else enableVrMode()
    }
    fun enableVrMode() {
        if (isVrMode) {
            logger.w("enableVrMode called but already in VR mode")
            return
        }
        logger.i("enableVrMode: starting")
        isVrMode = true
// hide main player
        try {
            capturePlayerView.visibility = View.INVISIBLE
            logger.d("Main capturePlayerView hidden")
        } catch (e: Exception) {
            logger.e("Failed to hide main capturePlayerView: ${e.message}")
        }
// create container in rootContainer
        if (rootContainer == null) {
            logger.e("enableVrMode: rootContainer is null! cannot add vr views")
            return
        }
        try {
            vrContainer = android.widget.LinearLayout(activity).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            rootContainer.addView(vrContainer)
            logger.d("VR container added to root")
        } catch (e: Exception) {
            logger.e("Failed to create/add vrContainer: ${e.message}")
            return
        }
// Left: ImageView (will show bitmap copy of right)
        try {
            leftVrImage = ImageView(activity).apply {
                val lp = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                layoutParams = lp
                scaleType = ImageView.ScaleType.FIT_CENTER   // ← безопаснее для отладки
// НЕ ставьте цвет фона или ставьте чёрный, если нужно
                setBackgroundColor(Color.BLACK)
                isClickable = false
                isFocusable = false
            }
            vrContainer?.addView(leftVrImage)
            logger.d("Left ImageView added")
        } catch (e: Exception) {
            logger.e("Failed to create leftVrImage: ${e.message}")
        }
// Right: real player
        try {
            rightVrPlayer = InstaCapturePlayerView(activity).apply {
                setLifecycle((activity as? androidx.fragment.app.FragmentActivity)?.lifecycle)
                val lp = android.widget.LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                )
                layoutParams = lp
                keepScreenOn = true
            }
            vrContainer?.addView(rightVrPlayer)
            rightVrPlayer?.setPlayerViewListener(object : PlayerViewListener {
                override fun onFirstFrameRender() {
                    logger.i("rightVrPlayer:onFirstFrameRender")
// Start copy loop on first frame
                    startCopyLoop()
                }
                override fun onLoadingFinish() {
                    logger.i("rightVrPlayer:onLoadingFinish - setting pipeline to right player")
                    try {
                        instaCameraManager.setPipeline(rightVrPlayer!!.pipeline)
                    } catch (e: Exception) {
                        logger.e("Failed to set pipeline to right player: ${e.message}")
                    }
                }
                override fun onReleaseCameraPipeline() {
                    logger.i("rightVrPlayer:onReleaseCameraPipeline")
                    try {
                        instaCameraManager.setPipeline(null)
                    } catch (e: Exception) {
                        logger.e("Failed to release pipeline: ${e.message}")
                    }
                }
            })
// Prepare & play right player with same params as main
            try {
                val params = (activity as? CaptureActivity)?.viewModel?.getCaptureParams()
                rightVrPlayer?.prepare(params)
            } catch (e: Exception) {
                logger.w("Unable to obtain capture params to prepare right player: ${e.message}")
            }
            rightVrPlayer?.play()
            logger.d("Right player prepared and play() called")
        } catch (e: Exception) {
            logger.e("Failed to create or start rightVrPlayer: ${e.message}")
        }
// hide other UI elements explicitly and disable touch on mode selector to avoid accidental swipes
        try {
            ivCaptureSetting.visibility = View.GONE
            btnCalibrate.visibility = View.GONE
            svCaptureMode.visibility = View.GONE
            svCaptureMode.isEnabled = false
            svCaptureMode.setOnTouchListener { _, _ -> true } // consume touches
            logger.d("UI elements hidden and svCaptureMode touch consumed")
        } catch (e: Exception) {
            logger.e("Failed to hide UI elements: ${e.message}")
        }
        calibrateGyro()
        logger.i("enableVrMode: finished")
    }
    fun disableVrMode() {
        if (!isVrMode) {
            logger.w("disableVrMode called but not in VR mode")
            return
        }
        logger.i("disableVrMode: starting")
        isVrMode = false
// stop copy loop
        stopCopyLoop()
// destroy right player
        try {
            rightVrPlayer?.destroy()
            logger.d("rightVrPlayer destroyed")
        } catch (e: Exception) {
            logger.e("Failed to destroy rightVrPlayer: ${e.message}")
        }
// clear left image
        try {
            leftVrImage?.setImageBitmap(null)
        } catch (e: Exception) {
            logger.e("Failed to clear left image bitmap: ${e.message}")
        }
// recycle bitmaps
        reusableBitmap?.let {
            it.recycle()
            reusableBitmap = null
        }
        compositeBitmap?.let {
            it.recycle()
            compositeBitmap = null
        }
// remove vr container
        try {
            vrContainer?.removeAllViews()
            rootContainer?.removeView(vrContainer)
            logger.d("vrContainer removed from root")
        } catch (e: Exception) {
            logger.e("Failed to remove vrContainer: ${e.message}")
        } finally {
            vrContainer = null
            leftVrImage = null
            rightVrPlayer = null
        }
// restore UI
        try {
            ivCaptureSetting.visibility = View.VISIBLE
            btnCalibrate.visibility = View.VISIBLE
            svCaptureMode.visibility = View.VISIBLE
            svCaptureMode.isEnabled = true
            svCaptureMode.setOnTouchListener(null)
            logger.d("UI elements restored")
        } catch (e: Exception) {
            logger.e("Failed to restore UI elements: ${e.message}")
        }
// show main player again
        try {
            capturePlayerView.visibility = View.VISIBLE
            capturePlayerView.play()
            logger.d("Main capturePlayerView shown and play() called")
        } catch (e: Exception) {
            logger.e("Failed to restore main capturePlayerView: ${e.message}")
        }
        logger.i("disableVrMode: finished")
    }
    fun applyOrientation(yawDeg: Float, pitchDeg: Float) {
// Apply to right player (real)
        try {
            rightVrPlayer?.let { obj ->
                val cls = obj.javaClass
                try {
                    val mYaw = cls.getMethod("setYaw", Float::class.javaPrimitiveType)
                    mYaw.invoke(obj, yawDeg + vrIpdYawDeg)
                } catch (_: NoSuchMethodException) {
                }
                try {
                    val mPitch = cls.getMethod("setPitch", Float::class.javaPrimitiveType)
                    mPitch.invoke(obj, pitchDeg)
                } catch (_: NoSuchMethodException) {
                }
            }
        } catch (e: Exception) {
            logger.e("applyOrientation -> right player error: ${e.message}")
        }
// Left is ImageView — no orientation API; log for debug
        try {
            if (leftVrImage != null) {
//logger.v("Left is ImageView; cannot apply yaw/pitch directly. (yaw-=$vrIpdYawDeg)")
            }
        } catch (e: Exception) {
            logger.e("applyOrientation -> left image logging failed: ${e.message}")
        }
    }
    fun onResume() {
        logger.d("VrManager.onResume called")
        rightVrPlayer?.play()
        if (rightVrPlayer != null && !copying) {
// if onFirstFrameRender already fired previously, restart copying
            startCopyLoop()
        }
    }
    fun onPause() {
        logger.d("VrManager.onPause called")
        stopCopyLoop()
// rightVrPlayer?.pause() // leave commented to avoid breaking pipeline state
    }
    fun destroy() {
        logger.i("VrManager.destroy called")
        stopCopyLoop()
        try {
            rightVrPlayer?.destroy()
        } catch (_: Exception) {
        }
    }
    // --------- copy loop (bitmap from right player -> left ImageView) ---------
    private var pixelCopyInProgress = false
    private fun findSurfaceView(v: View): SurfaceView? {
        if (v is SurfaceView) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                val found = findSurfaceView(v.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }
    private fun findTextureView(v: View): TextureView? {
        if (v is TextureView) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                val found = findTextureView(v.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }
    private fun startCopyLoop() {
        if (copying) {
            logger.d("startCopyLoop: already copying")
            return
        }
        val src = rightVrPlayer
        val dst = leftVrImage
        if (src == null || dst == null) {
            logger.w("startCopyLoop: src or dst is null (src=${src == null}, dst=${dst == null})")
            return
        }
        val width = src.width
        val height = src.height
        if (width <= 0 || height <= 0) {
            logger.w("Invalid src dimensions: ${width}x${height} - aborting copy loop")
            return
        }
        reusableBitmap = AndroidBitmap.createBitmap(width, height, AndroidBitmap.Config.ARGB_8888)
        copying = true
        copyRunnable = object : Runnable {
            override fun run() {
                try {
                    val renderView = findSurfaceView(src) ?: findTextureView(src)
                    if (renderView == null) {
                        logger.e("No SurfaceView or TextureView found in src - falling back to drawToBitmap")
                        var bmp: AndroidBitmap? = null
                        try {
                            bmp = src.drawToBitmap()
                            if (bmp!!.width > 0 && bmp!!.height > 0) {
                                logger.d("drawToBitmap produced bitmap ${bmp?.width}x${bmp?.height}")
                            } else {
                                logger.w("drawToBitmap returned empty bitmap w=${bmp?.width} h=${bmp?.height}")
                                bmp?.recycle()
                                bmp = null
                            }
                        } catch (e: Exception) {
                            logger.w("drawToBitmap exception: ${e.message}")
                        }
                        processAndSetBitmap(bmp, dst)
                        if (bmp != null) bmp.recycle() // since new from drawToBitmap
                    } else {
                        var bmp: AndroidBitmap? = null
                        if (renderView is SurfaceView) {
                            val surface = renderView.holder.surface
                            if (surface == null || !surface.isValid) {
                                logger.e("Invalid surface for PixelCopy")
                            } else {
                                pixelCopyInProgress = true
                                PixelCopy.request(surface, reusableBitmap!!, { result ->
                                    pixelCopyInProgress = false
                                    if (result == PixelCopy.SUCCESS) {
                                        bmp = reusableBitmap
                                        logger.d("PixelCopy success ${bmp!!.width}x${bmp?.height}")
                                        processAndSetBitmap(bmp, dst)
                                    } else {
                                        logger.e("PixelCopy failed with code: $result")
                                    }
                                    if (copying) handler.postDelayed(this, copyIntervalMs)
                                }, handler)
                                return  // async, return now
                            }
                        } else if (renderView is TextureView) {
                            try {
                                bmp = renderView.getBitmap(width, height)
                                logger.d("TextureView getBitmap produced ${bmp?.width}x${bmp?.height}")
                            } catch (e: Exception) {
                                logger.e("TextureView getBitmap failed: ${e.message}")
                            }
                        }
                        processAndSetBitmap(bmp, dst)
                        bmp?.recycle() // if from getBitmap, new bitmap
                    }
                } catch (t: Throwable) {
                    logger.e("copy loop throwable: ${t.message}")
                } finally {
                    if (copying && !pixelCopyInProgress) {
                        handler.postDelayed(this, copyIntervalMs)
                    }
                }
            }
        }
        handler.post(copyRunnable!!)
        logger.i("Copy loop started (interval=${copyIntervalMs}ms)")
    }
    private fun processAndSetBitmap(bmp: AndroidBitmap?, dst: ImageView) {
        if (bmp == null) return
        try {
            val finalBmp = if (bmp.hasAlpha()) {
                if (compositeBitmap == null || compositeBitmap!!.width != bmp.width || compositeBitmap!!.height != bmp.height) {
                    compositeBitmap?.recycle()
                    compositeBitmap = AndroidBitmap.createBitmap(bmp.width, bmp.height, AndroidBitmap.Config.ARGB_8888)
                }
                val canvas = Canvas(compositeBitmap!!)
                canvas.drawColor(Color.BLACK)
                canvas.drawBitmap(bmp, 0f, 0f, null)
                logger.d("Composited opaque bitmap (original had alpha)")
                compositeBitmap
            } else {
                logger.d("Using original bitmap (opaque)")
                bmp
            }
            dst.setImageBitmap(finalBmp)
            dst.invalidate()
            logger.i("setImageBitmap OK. dst.visible=${dst.visibility == View.VISIBLE} dst.alpha=${dst.alpha} bmp.size=${finalBmp?.width}x${finalBmp?.height}")
        } catch (e: Exception) {
            logger.e("processAndSetBitmap failed: ${e.message}")
        }
    }
    private fun stopCopyLoop() {
        if (!copying) return
        copying = false
        try {
            copyRunnable?.let { handler.removeCallbacks(it) }
            copyRunnable = null
            reusableBitmap?.recycle()
            reusableBitmap = null
// compositeBitmap recycled in disableVrMode after set null
            logger.i("Copy loop stopped")
        } catch (e: Exception) {
            logger.e("stopCopyLoop failed: ${e.message}")
        }
    }
}