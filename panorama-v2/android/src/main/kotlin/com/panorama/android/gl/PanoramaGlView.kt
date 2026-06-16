package com.panorama.android.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import com.panorama.core.calibration.AxisConvention
import com.panorama.core.math.GazeState
import com.panorama.core.math.yawPitchOf
import com.panorama.core.projection.EquirectProjection
import com.panorama.core.projection.ProjectionModel
import dev.romainguy.kotlin.math.Quaternion
import java.util.concurrent.atomic.AtomicReference

/** [GLSurfaceView] that renders the panorama via [PanoramaRenderer].
 *
 *  In VR the gaze must follow the head every VSYNC even while the video is paused, so the redraw
 *  loop is tied to view *visibility*, not playback: [onResume] (and surface-ready) switch the view
 *  to [RENDERMODE_CONTINUOUSLY] so the GL thread repaints every VSYNC on its own, and [onPause]
 *  switches back to [RENDERMODE_WHEN_DIRTY] to stop burning GPU off-screen. Each continuous frame
 *  re-reads the gaze, so a paused panorama still tracks the head. Video play/pause is irrelevant —
 *  a paused decoder simply stops delivering new frames and the last frame stays in the OES texture.
 *
 *  Continuous mode is used (rather than a Choreographer-driven WHEN_DIRTY nudge) because a
 *  requestRender() that races an in-flight draw is coalesced, halving the effective rate to ~30 fps;
 *  letting GLSurfaceView pace itself gives a steady display-clock 60.
 *
 *  @param projectionModel sphere mesh provider (defaults to equirect).
 *  @param axisConvention Site-A signs threaded to the renderer. */
class PanoramaGlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    projectionModel: ProjectionModel = EquirectProjection(),
    axisConvention: AxisConvention = AxisConvention(),
) : GLSurfaceView(context, attrs) {

    /** Lock-free orientation snapshot the renderer reads each frame; the sensor pipeline writes it.
     *  Seeded with an identity gaze so the first frames before any sensor sample render forward. */
    val gazeRef: AtomicReference<GazeState> = AtomicReference(identityGaze())

    /** Invoked on the GL thread once the video [Surface] is ready to hand to the player. */
    var onVideoSurfaceReady: ((Surface) -> Unit)? = null

    private val renderer: PanoramaRenderer
    private var surfaceTexture: SurfaceTexture? = null

    init {
        setEGLContextClientVersion(2)
        renderer = PanoramaRenderer(
            projectionModel = projectionModel,
            gazeRef = gazeRef,
            axisConvention = axisConvention,
            onSurfaceTextureReady = ::onSurfaceTextureReady,
        )
        setRenderer(renderer)
        // WHEN_DIRTY until the view is resumed/visible; onResume flips to CONTINUOUSLY (see class doc).
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    /** Point the renderer at an external orientation snapshot — typically the sensor engine's own
     *  AtomicReference — so the GL thread reads exactly the gaze the engine writes, with no copy
     *  step. The reference swap is volatile inside the renderer and is picked up on the next frame. */
    fun bindGazeRef(ref: AtomicReference<GazeState>) {
        renderer.gazeRef = ref
        requestRender()
    }

    /** Enable / disable split-screen VR. Takes effect on the next drawn frame. */
    fun setVrEnabled(enabled: Boolean) {
        renderer.vrEnabled = enabled
        requestRender()
    }

    /** Resume continuous VSYNC rendering so the gaze keeps tracking the head while the view is on
     *  screen. Safe to call before the surface exists — the GL thread only draws once it has one. */
    override fun onResume() {
        super.onResume()
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onPause() {
        renderMode = RENDERMODE_WHEN_DIRTY
        super.onPause()
    }

    private fun onSurfaceTextureReady(st: SurfaceTexture) {
        surfaceTexture = st
        // If onResume() ran before the surface existed, re-assert continuous mode now that there is
        // content to draw (onResume's switch happened with no surface attached).
        renderMode = RENDERMODE_CONTINUOUSLY
        Log.i(TAG, "onSurfaceTextureReady: video surface ready, wiring frame listener")
        // Continuous mode already redraws every VSYNC, so a new decoded frame only needs to mark
        // itself pending; onDrawFrame will pick it up on the next tick.
        st.setOnFrameAvailableListener { renderer.pendingFrame = true }
        onVideoSurfaceReady?.invoke(Surface(st))
    }

    private companion object {
        private const val TAG = "PanoramaGlView"

        fun identityGaze(): GazeState {
            val q = Quaternion()
            val (yaw, pitch) = yawPitchOf(q)
            return GazeState(q, yaw, pitch, angularVelocityDegPerSec = 0f)
        }
    }
}
