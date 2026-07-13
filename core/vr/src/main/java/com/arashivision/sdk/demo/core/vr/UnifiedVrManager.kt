package com.arashivision.sdk.demo.core.vr

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.arashivision.sdk.demo.data.sensor.GyroOrientationController
import com.elvishew.xlog.XLog

class UnifiedVrManager(
    private val activity: Activity,
    private val rootContainer: ViewGroup,
    private val vrSource: VrSourceView,
    private val overlaysToHide: List<View>,
    private val onCalibrateGyro: () -> Unit = {},
) {
    private val logger = XLog.tag("UnifiedVrManager").build()

    var isVrMode: Boolean = false
        private set(value) {
            if (field != value) {
                field = value
                onVrModeChanged?.invoke(value)
            }
        }

    var onVrModeChanged: ((Boolean) -> Unit)? = null

    private var vrContainer: ViewGroup? = null
    private var leftEyeImage: ImageView? = null
    private var reusableBitmap: Bitmap? = null
    private var compositeBitmap: Bitmap? = null
    private val handler = Handler(Looper.getMainLooper())
    private var copyRunnable: Runnable? = null
    private var copying = false
    private var pixelCopyInProgress = false

    private val copyIntervalMs: Long = 33L
    private var eyeScale: Float = 0.7f
    private var eyeSpacingPx: Int = -400
    private var vrSettingsButton: ImageButton? = null

    fun toggleVrMode() {
        if (isVrMode) exitVrModeAndRestart() else enableVrMode()
    }

    fun exitVrModeAndRestart() {
        if (!isVrMode) return
        logger.i("exitVrModeAndRestart: restarting activity")
        isVrMode = false
        copying = false
        copyRunnable?.let { handler.removeCallbacks(it) }
        copyRunnable = null
        activity.recreate()
    }

    fun enableVrMode() {
        if (isVrMode) return
        logger.i("enableVrMode: starting")
        isVrMode = true

        vrSource.onVrEnabled()

        vrContainer =
            FrameLayout(activity).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }
        rootContainer.addView(vrContainer)

        val contentLinear =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
            }
        vrContainer?.addView(contentLinear)

        leftEyeImage =
            ImageView(activity).apply {
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                lp.marginEnd = 0
                lp.marginStart = 0
                layoutParams = lp
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.BLACK)
                isClickable = false
                isFocusable = false
            }
        contentLinear.addView(leftEyeImage)

        val rightView = vrSource.contentView
        (rightView.parent as? ViewGroup)?.removeView(rightView)
        val rightLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        rightLp.marginStart = 0
        rightLp.marginEnd = 0
        rightView.layoutParams = rightLp
        contentLinear.addView(rightView)

        val sizeDp = 44
        val sizePx =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                sizeDp.toFloat(),
                activity.resources.displayMetrics,
            ).toInt()
        val marginDp = 12
        val marginPx =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                marginDp.toFloat(),
                activity.resources.displayMetrics,
            ).toInt()
        val settingsBtn =
            ImageButton(activity).apply {
                setImageResource(android.R.drawable.ic_menu_manage)
                setBackgroundResource(android.R.color.transparent)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val flp = FrameLayout.LayoutParams(sizePx, sizePx)
                flp.gravity = Gravity.END or Gravity.TOP
                flp.setMargins(marginPx, marginPx, marginPx, marginPx)
                layoutParams = flp
                alpha = 0.85f
                setOnClickListener { showVrSettingsDialog() }
            }
        vrContainer?.addView(settingsBtn)
        vrSettingsButton = settingsBtn

        overlaysToHide.forEach { it.visibility = View.GONE }

        contentLinear.post {
            if (!isVrMode) return@post
            applyVrAdjustments()
            handler.postDelayed({ startCopyLoop() }, VR_COPY_START_DELAY_MS)
        }
        logger.i("enableVrMode: finished")
    }

    fun disableVrMode() {
        if (!isVrMode) return
        logger.i("disableVrMode: starting")
        isVrMode = false

        stopCopyLoop()
        vrSource.onVrDisabled()

        leftEyeImage?.setImageBitmap(null)
        // Defer bitmap cleanup to avoid racing with in-flight PixelCopy
        handler.postDelayed(
            {
                reusableBitmap?.recycle()
                reusableBitmap = null
                compositeBitmap?.recycle()
                compositeBitmap = null
            },
            VR_CLEANUP_DELAY_MS,
        )

        vrContainer?.removeAllViews()
        rootContainer.removeView(vrContainer)
        vrContainer = null
        leftEyeImage = null
        vrSettingsButton = null

        overlaysToHide.forEach { it.visibility = View.VISIBLE }
        logger.i("disableVrMode: finished")
    }

    fun applyOrientation(
        yawDeg: Float,
        pitchDeg: Float,
    ) {
        vrSource.applyOrientation(yawDeg, pitchDeg)
    }

    fun onResume() {
        if (isVrMode && !copying) startCopyLoop()
    }

    fun onPause() {
        stopCopyLoop()
    }

    fun destroy() {
        stopCopyLoop()
        if (isVrMode) disableVrMode()
    }

    fun showVrSettingsDialog() {
        if (!isVrMode) return

        val dialogRoot =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                val pad =
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        12f,
                        activity.resources.displayMetrics,
                    ).toInt()
                setPadding(pad, pad, pad, pad)
            }

        val scaleLabel =
            TextView(activity).apply {
                text = "Scale (size): ${"%.2f".format(eyeScale)}"
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
            }
        val scaleSeek =
            SeekBar(activity).apply {
                max = 100
                progress = ((eyeScale - 0.5f) * 100).toInt().coerceIn(0, 100)
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
            }

        val spacingLabel =
            TextView(activity).apply {
                text = "Spacing (px): $eyeSpacingPx"
                val lp =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                lp.topMargin =
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 8f, activity.resources.displayMetrics,
                    ).toInt()
                layoutParams = lp
            }
        val spacingSeek =
            SeekBar(activity).apply {
                val maxDp = 200
                val maxPx =
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        maxDp.toFloat(),
                        activity.resources.displayMetrics,
                    ).toInt()
                max = maxPx * 2
                progress = (eyeSpacingPx + maxPx).coerceIn(0, max)
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
            }

        val sensLabel =
            TextView(activity).apply {
                text = "Sensitivity: ${"%.2f".format(GyroOrientationController.sensivity)}"
                val lp =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                lp.topMargin =
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 8f, activity.resources.displayMetrics,
                    ).toInt()
                layoutParams = lp
            }
        val sensSeek =
            SeekBar(activity).apply {
                max = 200
                progress = (GyroOrientationController.sensivity * 100f).toInt().coerceIn(0, max)
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
            }

        dialogRoot.addView(scaleLabel)
        dialogRoot.addView(scaleSeek)
        dialogRoot.addView(spacingLabel)
        dialogRoot.addView(spacingSeek)
        dialogRoot.addView(sensLabel)
        dialogRoot.addView(sensSeek)

        val dialog =
            AlertDialog.Builder(activity)
                .setTitle("VR: Adjust eyes")
                .setView(dialogRoot)
                .setPositiveButton("OK", null)
                .create()

        scaleSeek.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    sb: SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    eyeScale = 0.5f + progress.toFloat() / 100f
                    scaleLabel.text = "Scale (size): ${"%.2f".format(eyeScale)}"
                    applyVrAdjustments()
                }

                override fun onStartTrackingTouch(sb: SeekBar?) = Unit

                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            },
        )

        spacingSeek.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    sb: SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    val maxDp = 200
                    val maxPx =
                        TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            maxDp.toFloat(),
                            activity.resources.displayMetrics,
                        ).toInt()
                    eyeSpacingPx = progress - maxPx
                    spacingLabel.text = "Spacing (px): $eyeSpacingPx"
                    applyVrAdjustments()
                }

                override fun onStartTrackingTouch(sb: SeekBar?) = Unit

                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            },
        )

        sensSeek.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    sb: SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    val newSens = progress.toFloat() / 100f
                    GyroOrientationController.sensivity = newSens
                    sensLabel.text = "Sensitivity: ${"%.2f".format(newSens)}"
                }

                override fun onStartTrackingTouch(sb: SeekBar?) = Unit

                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            },
        )

        dialog.show()
    }

    private fun applyVrAdjustments() {
        val left = leftEyeImage ?: return
        val right = vrSource.contentView

        left.scaleX = eyeScale
        left.scaleY = eyeScale
        right.scaleX = eyeScale
        right.scaleY = eyeScale

        val parentLinear = vrContainer?.getChildAt(0) as? LinearLayout ?: return
        if (parentLinear.childCount < 2) return

        val leftChild = parentLinear.getChildAt(0)
        val rightChild = parentLinear.getChildAt(1)
        val half = eyeSpacingPx / 2

        fun setMarginStartEnd(
            view: View,
            start: Int? = null,
            end: Int? = null,
        ) {
            val lp = view.layoutParams
            when (lp) {
                is LinearLayout.LayoutParams -> {
                    if (start != null) lp.marginStart = start
                    if (end != null) lp.marginEnd = end
                    view.layoutParams = lp
                }
                is ViewGroup.MarginLayoutParams -> {
                    if (start != null) lp.marginStart = start
                    if (end != null) lp.marginEnd = end
                    view.layoutParams = lp
                }
            }
        }

        setMarginStartEnd(leftChild, end = half)
        setMarginStartEnd(rightChild, start = half)
        parentLinear.requestLayout()
    }

    private fun startCopyLoop() {
        if (copying) return
        val src = vrSource.contentView
        val dst = leftEyeImage ?: return
        val width = src.width
        val height = src.height
        if (width <= 0 || height <= 0) return

        reusableBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        copying = true

        copyRunnable =
            object : Runnable {
                override fun run() {
                    try {
                        val renderView = findSurfaceView(src) ?: findTextureView(src)
                        var bmp: Bitmap? = null

                        if (renderView is SurfaceView) {
                            val surface = renderView.holder.surface
                            if (surface != null && surface.isValid) {
                                pixelCopyInProgress = true
                                PixelCopy.request(surface, reusableBitmap!!, { result ->
                                    pixelCopyInProgress = false
                                    if (result == PixelCopy.SUCCESS && copying) {
                                        processAndSetBitmap(reusableBitmap, dst)
                                    }
                                    if (copying) handler.postDelayed(this, copyIntervalMs)
                                }, handler)
                                return
                            }
                        } else if (renderView is TextureView) {
                            bmp = renderView.getBitmap(width, height)
                        }

                        processAndSetBitmap(bmp, dst)
                        bmp?.recycle()
                    } catch (t: Throwable) {
                        logger.e("Copy loop error: ${t.message}")
                    } finally {
                        if (copying && !pixelCopyInProgress) {
                            handler.postDelayed(this, copyIntervalMs)
                        }
                    }
                }
            }
        handler.post(copyRunnable!!)
    }

    private fun processAndSetBitmap(
        bmp: Bitmap?,
        dst: ImageView,
    ) {
        bmp ?: return
        val finalBmp =
            if (bmp.hasAlpha()) {
                if (compositeBitmap == null ||
                    compositeBitmap!!.width != bmp.width ||
                    compositeBitmap!!.height != bmp.height
                ) {
                    compositeBitmap?.recycle()
                    compositeBitmap = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                }
                val canvas = Canvas(compositeBitmap!!)
                canvas.drawColor(Color.BLACK)
                canvas.drawBitmap(bmp, 0f, 0f, null)
                compositeBitmap
            } else {
                bmp
            }
        dst.setImageBitmap(finalBmp)
        dst.invalidate()
    }

    private fun stopCopyLoop() {
        if (!copying) return
        copying = false
        copyRunnable?.let { handler.removeCallbacks(it) }
        copyRunnable = null
    }

    private fun findSurfaceView(v: View): SurfaceView? {
        if (v is SurfaceView) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                findSurfaceView(v.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun findTextureView(v: View): TextureView? {
        if (v is TextureView) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                findTextureView(v.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    companion object {
        private const val VR_COPY_START_DELAY_MS = 200L
        private const val VR_CLEANUP_DELAY_MS = 200L
    }
}
