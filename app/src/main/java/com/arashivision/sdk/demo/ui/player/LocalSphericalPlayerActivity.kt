package com.arashivision.sdk.demo.ui.player

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.arashivision.sdk.demo.R
import com.arashivision.sdk.demo.base.BaseActivity
import com.arashivision.sdk.demo.base.BaseEvent
import com.arashivision.sdk.demo.databinding.ActivityLocalSphericalPlayerBinding
import com.arashivision.sdk.demo.ui.capture.GyroOrientationController
import com.arashivision.sdk.demo.ui.player.detection.VideoDetectionSidecarParser
import com.arashivision.sdk.demo.ui.player.detection.VideoDetectionTimeline
import com.arashivision.sdk.demo.ui.player.detection.VideoDetectedObject
import com.arashivision.sdk.demo.ui.player.panorama.EquirectangularProjection
import com.arashivision.sdk.demo.ui.player.panorama.PanoramaDirection
import com.arashivision.sdk.demo.ui.player.panorama.PanoramaFovMath
import com.elvishew.xlog.XLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
@OptIn(UnstableApi::class)
class LocalSphericalPlayerActivity :
    BaseActivity<ActivityLocalSphericalPlayerBinding, LocalSphericalPlayerViewModel>() {

    private val logger = XLog.tag(LocalSphericalPlayerActivity::class.java.simpleName).build()

    private var player: ExoPlayer? = null

    private lateinit var gyroController: GyroOrientationController
    private lateinit var vrManager: LocalVrManager

    private val uiHandler = Handler(Looper.getMainLooper())
    private val detectionParser = VideoDetectionSidecarParser()
    private var currentGazeDirection: PanoramaDirection = EquirectangularProjection.fromYawPitch(0.0, 0.0)
    private val detectionUpdateRunnable = object : Runnable {
        override fun run() {
            updateCurrentDetections()
            uiHandler.postDelayed(this, DETECTION_UPDATE_INTERVAL_MS)
        }
    }

    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            persistReadPermission(uri)
            viewModel.onVideoSelected(uri)
            binding.tvSelectedVideo.text = uri.toString()
            startPlayback(uri)
        }

    private val pickJsonLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            persistReadPermission(uri)
            loadDetectionJson(uri)
        }

    @RequiresApi(Build.VERSION_CODES.R)
    @androidx.annotation.OptIn(UnstableApi::class)
    override fun initView() {
        super.initView()

        binding.sphericalView.setDefaultStereoMode(C.STEREO_MODE_MONO)
        binding.sphericalView.setUseSensorRotation(viewModel.sensorRotationEnabled)

        gyroController = GyroOrientationController(
            context = this,
            getDisplayRotation = { display?.rotation ?: Surface.ROTATION_0 },
            applyOrientation = { yaw, pitch -> tryApplyOrientation(yaw, pitch) }
        )

        vrManager = LocalVrManager(
            activity = this,
            sourceView = binding.sphericalView,
            leftEyeImage = binding.vrLeftEye,
            overlaysToHide = listOf(binding.btnPlayPause, binding.ivCaptureSetting, binding.btnCalibrate)
        )
        syncPlayPauseButton()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @SuppressLint("ClickableViewAccessibility")
    override fun initListener() {
        super.initListener()
        binding.btnPickVideo.setOnClickListener { pickVideoLauncher.launch(arrayOf("video/*")) }
        binding.btnPickJson.setOnClickListener { pickJsonLauncher.launch(JSON_MIME_TYPES) }

        binding.btnPlayPause.setOnClickListener {
            val newState = !(player?.isPlaying ?: false)
            player?.playWhenReady = newState
            syncPlayPauseButton()
        }

        binding.btnCalibrate.setOnClickListener {
            gyroController.calibrate()
            toast(R.string.gyro_recentered)
        }

        binding.btnVrToggle.setOnClickListener { vrManager.toggleVrMode() }
        binding.btnVrToggle.setOnLongClickListener {
            vrManager.showVrSettingsDialog()
            true
        }

        binding.ivCaptureSetting.setOnClickListener { showPlaybackSettings() }

        // in VR mode we block touches on player so accidental drags don't desync head-tracking
        binding.sphericalView.setOnTouchListener { _, _ -> vrManager.isVrMode }
    }

    override fun onEvent(event: BaseEvent) = Unit

    override fun onStart() {
        super.onStart()
        if (player == null) {
            player = ExoPlayer.Builder(this).build().also { exo ->
                exo.repeatMode = Player.REPEAT_MODE_ALL
                exo.setVideoSurfaceView(binding.sphericalView)
                viewModel.currentVideoUri?.let { uri ->
                    exo.setMediaItem(MediaItem.fromUri(uri))
                    exo.prepare()
                    exo.playWhenReady = true
                }
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onResume() {
        super.onResume()
        binding.sphericalView.onResume()
        gyroController.start()
        vrManager.onResume()
        player?.playWhenReady = true
        syncPlayPauseButton()
        uiHandler.removeCallbacks(detectionUpdateRunnable)
        uiHandler.post(detectionUpdateRunnable)
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onPause() {
        vrManager.onPause()
        gyroController.stop()
        binding.sphericalView.onPause()
        uiHandler.removeCallbacks(detectionUpdateRunnable)
        player?.playWhenReady = false
        super.onPause()
    }

    override fun onStop() {
        uiHandler.removeCallbacks(detectionUpdateRunnable)
        player?.release()
        player = null
        super.onStop()
    }

    override fun onBackPressed() {
        if (vrManager.isVrMode) {
            vrManager.disableVrMode()
            return
        }
        super.onBackPressed()
    }

    private fun showPlaybackSettings() {
        val actions = mutableListOf(
            getString(R.string.pick_local_video),
            getString(R.string.add_detection_json),
            if (player?.isPlaying == true) getString(R.string.pause) else getString(R.string.play),
            if (viewModel.sensorRotationEnabled) getString(R.string.disable_gyro_control) else getString(R.string.enable_gyro_control),
            getString(R.string.recenter_view)
        )
        if (vrManager.isVrMode) {
            actions.add("VR: Adjust eyes")
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.capture_settings)
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    getString(R.string.pick_local_video) -> pickVideoLauncher.launch(arrayOf("video/*"))
                    getString(R.string.add_detection_json) -> pickJsonLauncher.launch(JSON_MIME_TYPES)
                    getString(R.string.pause), getString(R.string.play) -> {
                        val newState = !(player?.isPlaying ?: false)
                        player?.playWhenReady = newState
                        syncPlayPauseButton()
                    }
                    getString(R.string.disable_gyro_control), getString(R.string.enable_gyro_control) -> {
                        val enabled = viewModel.toggleSensorRotation()
                        binding.sphericalView.setUseSensorRotation(enabled)
                        gyroController.setzOrientationEnabled(enabled)
                    }
                    getString(R.string.recenter_view) -> {
                        gyroController.calibrate()
                        toast(R.string.gyro_recentered)
                    }
                    "VR: Adjust eyes" -> vrManager.showVrSettingsDialog()
                }
            }
            .show()
    }

    private fun loadDetectionJson(uri: Uri) {
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Unable to open JSON file")
                    VideoDetectionTimeline(detectionParser.parse(json))
                }
            }

            result.onSuccess { timeline ->
                viewModel.onJsonSelected(uri, timeline)
                binding.tvSelectedJson.text = uri.toString()
                toast(getString(R.string.detection_json_loaded, timeline.frameCount))
                updateCurrentDetections()
            }.onFailure { error ->
                logger.e("Detection JSON load failed: ${error.message}")
                toast(getString(R.string.detection_json_load_failed, error.message ?: "unknown"), true)
            }
        }
    }

    private fun persistReadPermission(uri: Uri) {
        kotlin.runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun startPlayback(uri: Uri) {
        val exo = player ?: return
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
        exo.playWhenReady = true
        syncPlayPauseButton()
    }

    private fun syncPlayPauseButton() {
        val playing = player?.isPlaying == true
        binding.btnPlayPause.text = if (playing) getString(R.string.pause) else getString(R.string.play)
    }

    private fun updateCurrentDetections() {
        val positionMs = player?.currentPosition ?: 0L
        val frame = viewModel.currentDetectionFrame(positionMs)

        binding.tvDetectionDebug.text = if (frame == null) {
            binding.directionArrowOverlay.hideArrow()
            getString(R.string.no_detection_json_selected)
        } else {
            val detections = frame.objects
            val trackIds = detections.joinToString { objectDetection ->
                "#${objectDetection.trackId}"
            }.ifEmpty { "—" }
            updateDirectionArrow(detections)
            getString(
                R.string.current_detection_frame,
                frame.frameIdx,
                frame.timeSec,
                detections.size,
                trackIds
            )
        }
    }

    private fun updateDirectionArrow(detections: List<VideoDetectedObject>) {
        val firstOutsideFov = detections.firstNotNullOfOrNull { detection ->
            val targetDirection = EquirectangularProjection.fromNormalized(
                x = detection.centerNorm.x.coerceIn(0.0, 1.0),
                y = detection.centerNorm.y.coerceIn(0.0, 1.0)
            )
            PanoramaFovMath.resolveTarget(
                gaze = currentGazeDirection,
                target = targetDirection,
                horizontalFovRad = HORIZONTAL_FOV_RAD,
                verticalFovRad = VERTICAL_FOV_RAD
            ).takeUnless { it.isInsideFov }
        }

        val arrowAngleRad = firstOutsideFov?.arrowAngleRad
        if (arrowAngleRad == null) {
            binding.directionArrowOverlay.hideArrow()
        } else {
            binding.directionArrowOverlay.showArrow(arrowAngleRad)
        }
    }

    private fun tryApplyOrientation(yawDeg: Float, pitchDeg: Float) {
        if (!viewModel.sensorRotationEnabled) return
        currentGazeDirection = EquirectangularProjection.fromYawPitch(
            yawRad = Math.toRadians(yawDeg.toDouble()),
            pitchRad = Math.toRadians(pitchDeg.coerceIn(-MAX_PITCH_DEG, MAX_PITCH_DEG).toDouble())
        )

        fun applyTo(obj: Any?, yaw: Float, pitch: Float) {
            if (obj == null) return
            try {
                val cls = obj.javaClass
                runCatching {
                    cls.getMethod("setYaw", Float::class.javaPrimitiveType).invoke(obj, yaw)
                }
                runCatching {
                    cls.getMethod("setPitch", Float::class.javaPrimitiveType).invoke(obj, pitch)
                }
            } catch (e: Exception) {
                logger.e("Orientation apply failed: ${e.message}")
            }
        }

        try {
            applyTo(binding.sphericalView, yawDeg, pitchDeg)
        } catch (e: Exception) {
            logger.e("tryApplyOrientation failed: ${e.message}")
        }
    }

    companion object {
        private val JSON_MIME_TYPES = arrayOf("application/json", "text/json", "text/plain", "application/octet-stream", "*/*")
        private const val DETECTION_UPDATE_INTERVAL_MS = 200L
        private const val MAX_PITCH_DEG = 90f
        private const val HORIZONTAL_FOV_RAD = 1.5707963267948966 // 90 degrees
        private const val VERTICAL_FOV_RAD = 1.5707963267948966 // 90 degrees
    }
}
