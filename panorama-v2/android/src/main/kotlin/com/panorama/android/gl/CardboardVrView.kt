package com.panorama.android.gl

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.Surface
import com.panorama.core.math.GazeState
import dev.romainguy.kotlin.math.Quaternion
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** 360 viewer backed by the Google Cardboard SDK native renderer. Renders either a full-screen mono
 *  view or split-screen stereo VR ([setMonoMode]); both share the same head tracker, so gyro
 *  sensitivity and the head pose ([gazeRef]) are consistent across modes.
 *
 *  This view owns a [GLSurfaceView] context and, on its GL thread, creates the OES
 *  [SurfaceTexture] the video decodes into and hands its [Surface] to the player via
 *  [onVideoSurfaceReady] — the SAME contract the mono/legacy GL views use. Each frame it pumps the
 *  newest decoded frame into the OES texture and calls into native ([nativeOnDrawFrame]), which
 *  renders the equirect sphere into two per-eye viewports and applies Cardboard lens distortion.
 *
 *  Head tracking is owned by the Cardboard SDK (its own IMU fusion), NOT by [OrientationEngine] —
 *  the off-screen arrow still reads OrientationEngine.gazeRef independently, so no gaze binding
 *  lives here. The render loop is tied to view visibility (continuous while resumed), so the gaze
 *  keeps tracking the head even while the video is paused.
 *
 *  All Cardboard rendering/tracking is native C in `libcardboard_jni.so` (which links the SDK's
 *  `libGfxPluginCardboard.so`); this class is the JNI + GL-thread + lifecycle host. */
class CardboardVrView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {

    /** Invoked on the GL thread once the OES [Surface] is ready; hand it to the player. */
    var onVideoSurfaceReady: ((Surface) -> Unit)? = null

    /** Invoked when the surface is torn down, so the caller can detach it from the player. */
    var onVideoSurfaceDestroyed: (() -> Unit)? = null

    private var nativeApp: Long = 0L
    private var surfaceTexture: SurfaceTexture? = null
    private val stMatrix = FloatArray(16)
    private val poseQuat = FloatArray(4)

    /** The Cardboard head-tracker orientation, refreshed every drawn frame on the GL thread. The
     *  arrow overlay reads this so its FOV test runs against the exact pose the view renders with
     *  (only the quaternion is meaningful; yaw/pitch/velocity are unused by the FOV math). */
    val gazeRef: AtomicReference<GazeState> =
        AtomicReference(GazeState(Quaternion(), 0f, 0f, 0f))

    init {
        // Cardboard needs an Activity context (its QR/profile UI launches an Activity).
        nativeApp = nativeOnCreate(context.findActivity())
        setEGLContextClientVersion(2)
        setRenderer(VrRenderer())
        // WHEN_DIRTY until resumed; onResume flips to CONTINUOUSLY so the head pose drives a redraw
        // every VSYNC (the sphere tracks the head even while video is paused).
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    private inner class VrRenderer : Renderer {
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            if (nativeApp == 0L) return
            nativeOnSurfaceCreated(nativeApp)
            val textureId = createOesTexture()
            nativeSetOesTextureId(nativeApp, textureId)
            val st = SurfaceTexture(textureId)
            // Continuous mode already redraws each VSYNC; this is belt-and-suspenders.
            st.setOnFrameAvailableListener { requestRender() }
            surfaceTexture = st
            onVideoSurfaceReady?.invoke(Surface(st))
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            if (nativeApp != 0L) nativeSetScreenParams(nativeApp, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            if (nativeApp == 0L) return
            surfaceTexture?.let { st ->
                st.updateTexImage()
                st.getTransformMatrix(stMatrix)
                nativeSetStMatrix(nativeApp, stMatrix)
            }
            // Publish the same head pose the frame was rendered with so the arrow stays in sync.
            nativeGetPose(nativeApp, poseQuat)
            gazeRef.set(
                GazeState(
                    Quaternion(poseQuat[0], poseQuat[1], poseQuat[2], poseQuat[3]),
                    yawDeg = 0f,
                    pitchDeg = 0f,
                    angularVelocityDegPerSec = 0f,
                ),
            )
            nativeOnDrawFrame(nativeApp)
        }
    }

    override fun onResume() {
        super.onResume()
        renderMode = RENDERMODE_CONTINUOUSLY
        if (nativeApp != 0L) queueEvent { if (nativeApp != 0L) nativeOnResume(nativeApp) }
    }

    override fun onPause() {
        renderMode = RENDERMODE_WHEN_DIRTY
        if (nativeApp != 0L) queueEvent { if (nativeApp != 0L) nativeOnPause(nativeApp) }
        super.onPause()
    }

    /** Release the native renderer and the OES surface. Call from the host's onRelease/onDispose. */
    fun onDestroy() {
        onVideoSurfaceDestroyed?.invoke()
        queueEvent {
            if (nativeApp != 0L) {
                nativeOnDestroy(nativeApp)
                nativeApp = 0L
            }
            surfaceTexture?.release()
            surfaceTexture = null
        }
    }

    /** Tune the per-eye viewport: [eyeScale] in (0,1] sizes each eye within its half, [eyeGap] in
     *  [0,1) pushes the two halves apart. Applied on the GL thread. */
    fun setVrParams(eyeScale: Float, eyeGap: Float) {
        if (nativeApp != 0L) queueEvent { if (nativeApp != 0L) nativeSetVrParams(nativeApp, eyeScale, eyeGap) }
    }

    /** Head-turn gain about the VR entry pose ([s] > 1 amplifies, < 1 damps). Applied on GL thread. */
    fun setSensitivity(s: Float) {
        if (nativeApp != 0L) queueEvent { if (nativeApp != 0L) nativeSetSensitivity(nativeApp, s) }
    }

    /** Switch between full-screen mono (true) and split-screen stereo VR (false). GL thread. */
    fun setMonoMode(mono: Boolean) {
        if (nativeApp != 0L) queueEvent { if (nativeApp != 0L) nativeSetMonoMode(nativeApp, mono) }
    }

    /** Mono screen orientation (true = portrait, false = landscape). Match the locked screen. */
    fun setMonoPortrait(portrait: Boolean) {
        if (nativeApp != 0L) queueEvent { if (nativeApp != 0L) nativeSetMonoPortrait(nativeApp, portrait) }
    }

    private fun createOesTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val id = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id)
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE,
        )
        return id
    }

    // --- native bridge (libcardboard_jni.so) ---
    private external fun nativeOnCreate(context: Context): Long
    private external fun nativeOnDestroy(nativeApp: Long)
    private external fun nativeOnSurfaceCreated(nativeApp: Long)
    private external fun nativeSetScreenParams(nativeApp: Long, width: Int, height: Int)
    private external fun nativeSetOesTextureId(nativeApp: Long, textureId: Int)
    private external fun nativeSetStMatrix(nativeApp: Long, matrix: FloatArray)
    private external fun nativeOnDrawFrame(nativeApp: Long)
    private external fun nativeOnPause(nativeApp: Long)
    private external fun nativeOnResume(nativeApp: Long)
    private external fun nativeGetPose(nativeApp: Long, out: FloatArray)
    private external fun nativeSetVrParams(nativeApp: Long, eyeScale: Float, eyeGap: Float)
    private external fun nativeSetSensitivity(nativeApp: Long, s: Float)
    private external fun nativeSetMonoMode(nativeApp: Long, mono: Boolean)
    private external fun nativeSetMonoPortrait(nativeApp: Long, portrait: Boolean)

    private companion object {
        init {
            System.loadLibrary("cardboard_jni")
        }
    }
}

/** Unwraps a (possibly Compose-wrapped) [Context] to its hosting [Activity]. */
private fun Context.findActivity(): Context {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return this
}
