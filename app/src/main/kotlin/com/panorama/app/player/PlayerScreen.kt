package com.panorama.app.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.panorama.app.settings.SettingsScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.panorama.android.gl.CardboardVrView

/** The 360 player screen: a single [CardboardVrView] with the off-screen [ArrowOverlay] and the
 *  [PlayerControls] stacked on top.
 *
 *  This composable is the integration hub. It owns the wiring between the :android collaborators
 *  (held by [PlayerViewModel]):
 *   - mode: one [CardboardVrView] serves both modes; `setMonoMode(!vrEnabled)` toggles between
 *     full-screen mono and split-screen stereo.
 *   - gaze: the off-screen [ArrowOverlay] reads the Cardboard head pose via [CardboardVrView.gazeRef]
 *     (bound through [PlayerViewModel.useGazeSource]).
 *   - video: the renderer's output [android.view.Surface] is handed to the player through
 *     [PlayerViewModel.attachVideoSurface].
 *   - settings: a translucent overlay stacked on top.
 *   - sensor lifecycle: started/stopped from a [DisposableEffect] keyed to the screen's lifecycle.
 *
 *  [videoUri]/[sidecarUri] arrive as screen arguments; a [LaunchedEffect] opens them once. */
@Composable
fun PlayerScreen(
    videoUri: Uri?,
    sidecarUri: Uri? = null,
    onBack: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // Orientation is explicit, not auto-rotated. VR is always landscape (split-screen stereo). Mono
    // is locked to the user's portrait/landscape choice; a button toggles it. The renderer's pose
    // frame is matched to the same choice below so the picture never sits 90° off.
    DisposableEffect(state.vrEnabled, state.monoPortrait) {
        val activity = context as? Activity
        activity?.requestedOrientation = when {
            state.vrEnabled -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            state.monoPortrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Open the chosen media exactly once per (uri) change.
    LaunchedEffect(videoUri, sidecarUri) {
        if (videoUri != null) viewModel.selectMedia(videoUri, sidecarUri)
    }

    // Settings are shown as a translucent overlay on top of the live camera view (not a separate
    // screen), so the video keeps playing underneath while the user tunes.
    var showSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Handle to the live GL view so onResume/onPause can be forwarded — the Cardboard-backed
        // CardboardVrView registers its render thread only in those callbacks (VSYNC loop tied to
        // visibility).
        var cardboardRef by remember { mutableStateOf<CardboardVrView?>(null) }

        // One Cardboard-backed view for both modes; mono toggles the single-viewport path.
        val cardboardView = remember {
            { ctx: android.content.Context ->
                CardboardVrView(ctx).apply {
                    cardboardRef = this
                    onVideoSurfaceReady = { surface -> viewModel.attachVideoSurface(surface) }
                    onVideoSurfaceDestroyed = { viewModel.attachVideoSurface(null) }
                    setVrParams(viewModel.vrEyeScale, viewModel.vrEyeGap)
                    setSensitivity(viewModel.vrSensitivity)
                    setMonoMode(!viewModel.state.value.vrEnabled)
                    setMonoPortrait(viewModel.state.value.monoPortrait)
                }
            }
        }
        AndroidView(
            factory = cardboardView,
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.setMonoMode(!state.vrEnabled)
                view.setMonoPortrait(state.monoPortrait)
                view.onResume()
            },
            onRelease = { view ->
                view.onDestroy()
                cardboardRef = null
            },
        )

        // Sensor lifecycle: start on RESUME, stop on PAUSE; also stop on dispose. The GLSurfaceView's
        // own onPause/onResume are driven by AndroidView's lifecycle hooks.
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        viewModel.startSensor()
                        cardboardRef?.onResume()
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        cardboardRef?.onPause()
                        viewModel.stopSensor()
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                viewModel.stopSensor()
            }
        }

        // Both modes render with the Cardboard head tracker, so the arrow always reads its pose.
        val vrView = cardboardRef
        val arrowGazeRef = vrView?.gazeRef ?: viewModel.gazeRef
        DisposableEffect(vrView) {
            if (vrView != null) viewModel.useGazeSource { vrView.gazeRef.get() }
            onDispose { viewModel.useGazeSource(null) }
        }

        ArrowOverlay(
            arrow = state.arrow,
            vrEnabled = state.vrEnabled,
            gazeRef = arrowGazeRef,
            modifier = Modifier.fillMaxSize(),
        )

        PlayerControls(
            isPlaying = state.isPlaying,
            positionMs = state.playbackPosMs,
            durationMs = state.durationMs,
            vrEnabled = state.vrEnabled,
            onPlayPause = { if (state.isPlaying) viewModel.pause() else viewModel.play() },
            onSeek = viewModel::seek,
            onRecalibrate = viewModel::recalibrate,
            onToggleVr = viewModel::toggleVr,
            onToggleOrientation = viewModel::toggleMonoOrientation,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // Top-left: exit to the library. Top-right: open settings. Inset past the status bar.
        FilledTonalButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).systemBarsPadding().padding(8.dp),
        ) {
            Text("‹ Back")
        }
        FilledTonalButton(
            onClick = { showSettings = true },
            modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(8.dp),
        ) {
            Text("Settings")
        }

        // Translucent settings overlay over the live view. The scrim dims (but does not hide) the
        // camera and dismisses on tap-outside; the panel itself sits centered on top.
        if (showSettings) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { showSettings = false },
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .systemBarsPadding()
                        .padding(24.dp)
                        .background(Color.Black.copy(alpha = 0.55f))
                        // Swallow taps on the panel so they don't fall through to the dismiss scrim.
                        .clickable(enabled = false) {},
                ) {
                    // Local UI state mirrors the VM so the sliders recompose; each change is pushed
                    // to the VM (persists across opens) and applied to the live VR view immediately.
                    var sensitivity by remember { mutableStateOf(viewModel.vrSensitivity) }
                    var eyeScale by remember { mutableStateOf(viewModel.vrEyeScale) }
                    var eyeGap by remember { mutableStateOf(viewModel.vrEyeGap) }
                    SettingsScreen(
                        sensitivity = sensitivity,
                        onSensitivityChange = {
                            sensitivity = it
                            viewModel.setVrSensitivity(it)
                            cardboardRef?.setSensitivity(it)
                        },
                        eyeScale = eyeScale,
                        onEyeScaleChange = {
                            eyeScale = it
                            viewModel.setVrEyeScale(it)
                            cardboardRef?.setVrParams(it, eyeGap)
                        },
                        eyeGap = eyeGap,
                        onEyeGapChange = {
                            eyeGap = it
                            viewModel.setVrEyeGap(it)
                            cardboardRef?.setVrParams(eyeScale, it)
                        },
                        onBack = { showSettings = false },
                    )
                }
            }
        }
    }
}
