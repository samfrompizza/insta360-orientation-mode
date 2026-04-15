package com.arashivision.sdk.demo.ui.player

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ScaleGestureDetector
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
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
import com.elvishew.xlog.XLog

@OptIn(UnstableApi::class)
class LocalSphericalPlayerActivity :
    BaseActivity<ActivityLocalSphericalPlayerBinding, LocalSphericalPlayerViewModel>() {

    private val logger = XLog.tag(LocalSphericalPlayerActivity::class.java.simpleName).build()

    private var player: ExoPlayer? = null
    private var currentVideoUri: Uri? = null

    private lateinit var gyroController: GyroOrientationController
    private lateinit var vrManager: LocalVrManager

    private var sensorRotationEnabled = true

    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            kotlin.runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            binding.tvSelectedVideo.text = uri.toString()
            currentVideoUri = uri
            startPlayback(uri)
        }

    override fun initView() {
        super.initView()

        binding.sphericalView.setDefaultStereoMode(C.STEREO_MODE_MONO)
        binding.sphericalView.setUseSensorRotation(sensorRotationEnabled)

        gyroController = GyroOrientationController(
            context = this,
            getDisplayRotation = { display?.rotation ?: Surface.ROTATION_0 },
            applyOrientation = { yaw, pitch -> tryApplyOrientation(yaw, pitch) }
        )

        vrManager = LocalVrManager(
            activity = this,
            sourceView = binding.sphericalView,
            leftEyeImage = binding.vrLeftEye,
            controlsContainer = binding.controlsContainer,
            btnVrToggle = binding.btnToggleVr
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun initListener() {
        super.initListener()
        binding.btnPickVideo.setOnClickListener { pickVideoLauncher.launch(arrayOf("video/*")) }

        binding.btnPlayPause.setOnClickListener {
            val newState = !(player?.isPlaying ?: false)
            player?.playWhenReady = newState
            syncPlayPauseButton()
        }

        binding.btnToggleSensor.setOnClickListener {
            sensorRotationEnabled = !sensorRotationEnabled
            binding.sphericalView.setUseSensorRotation(sensorRotationEnabled)
            binding.btnToggleSensor.text = if (sensorRotationEnabled) {
                getString(R.string.disable_gyro_control)
            } else {
                getString(R.string.enable_gyro_control)
            }
            gyroController.setzOrientationEnabled(sensorRotationEnabled)
        }

        binding.btnCenterView.setOnClickListener {
            recenterSensorView()
            toast(R.string.gyro_recentered)
        }

        binding.btnToggleVr.setOnClickListener {
            vrManager.toggleVrMode()
        }

        binding.btnToggleVr.setOnLongClickListener {
            vrManager.showVrSettingsDialog()
            true
        }

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
                currentVideoUri?.let { uri ->
                    exo.setMediaItem(MediaItem.fromUri(uri))
                    exo.prepare()
                    exo.playWhenReady = true
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.sphericalView.onResume()
        gyroController.start()
        vrManager.onResume()
        player?.playWhenReady = true
        syncPlayPauseButton()
    }

    override fun onPause() {
        vrManager.onPause()
        gyroController.stop()
        binding.sphericalView.onPause()
        player?.playWhenReady = false
        super.onPause()
    }

    override fun onStop() {
        releasePlayers()
        super.onStop()
    }

    @OptIn(UnstableApi::class)
    private fun setupSphericalViews() {
        binding.sphericalView.setDefaultStereoMode(C.STEREO_MODE_MONO)
        binding.sphericalViewSecondary.setDefaultStereoMode(C.STEREO_MODE_MONO)
        binding.sphericalView.setUseSensorRotation(sensorRotationEnabled)
        binding.sphericalViewSecondary.setUseSensorRotation(sensorRotationEnabled)
    }

    private fun ensurePlayers() {
        if (mainPlayer == null) {
            mainPlayer = ExoPlayer.Builder(this).build().also { exo ->
                exo.repeatMode = Player.REPEAT_MODE_ALL
                exo.setVideoSurfaceView(binding.sphericalView)
            }
        }

        if (vrPlayer == null) {
            vrPlayer = ExoPlayer.Builder(this).build().also { exo ->
                exo.repeatMode = Player.REPEAT_MODE_ALL
                exo.volume = 0f
                exo.setVideoSurfaceView(binding.sphericalViewSecondary)
            }
        }

        currentVideoUri?.let { startPlayback(it) }
    }

    private fun releasePlayers() {
        mainPlayer?.release()
        vrPlayer?.release()
        mainPlayer = null
        vrPlayer = null
    }

    private fun startPlayback(uri: Uri) {
        val main = mainPlayer ?: return
        val vr = vrPlayer ?: return

        val mediaItem = MediaItem.fromUri(uri)
        main.setMediaItem(mediaItem)
        vr.setMediaItem(mediaItem)
        main.prepare()
        vr.prepare()

        setPlayWhenReady(true)
        syncPlayPauseButton()
    }

    private fun setPlayWhenReady(playWhenReady: Boolean) {
        mainPlayer?.playWhenReady = playWhenReady
        vrPlayer?.playWhenReady = playWhenReady
    }

    @OptIn(UnstableApi::class)
    private fun recenterSensorView() {
        if (!sensorRotationEnabled) return
        binding.sphericalView.setUseSensorRotation(false)
        binding.sphericalViewSecondary.setUseSensorRotation(false)
        binding.sphericalView.post {
            binding.sphericalView.setUseSensorRotation(true)
            binding.sphericalViewSecondary.setUseSensorRotation(true)
        }
    }

    private fun updateVrUi() {
        binding.sphericalViewSecondary.visibility = if (isVrMode) View.VISIBLE else View.GONE
        binding.controlsContainer.visibility = if (isVrMode) View.GONE else View.VISIBLE
        binding.btnToggleVr.text = if (isVrMode) {
            getString(R.string.exit_vr_mode)
        } else {
            getString(R.string.enter_vr_mode)
        }
    }

    private fun updateZoom() {
        binding.sphericalView.scaleX = zoomFactor
        binding.sphericalView.scaleY = zoomFactor
        binding.sphericalViewSecondary.scaleX = zoomFactor
        binding.sphericalViewSecondary.scaleY = zoomFactor
    }

    private fun syncPlayPauseButton() {
        val playing = player?.isPlaying == true
        binding.btnPlayPause.text = if (playing) getString(R.string.pause) else getString(R.string.play)
    }

    private fun tryApplyOrientation(yawDeg: Float, pitchDeg: Float) {
        if (!sensorRotationEnabled) return

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
}
