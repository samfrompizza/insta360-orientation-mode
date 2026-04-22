package com.arashivision.sdk.demo.ui.player

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.Surface
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
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

@UnstableApi
@OptIn(UnstableApi::class)
class LocalSphericalPlayerActivity :
    BaseActivity<ActivityLocalSphericalPlayerBinding, LocalSphericalPlayerViewModel>() {

    private val logger = XLog.tag(LocalSphericalPlayerActivity::class.java.simpleName).build()

    private var player: ExoPlayer? = null

    private lateinit var gyroController: GyroOrientationController
    private lateinit var vrManager: LocalVrManager

    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            kotlin.runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.onVideoSelected(uri)
            binding.tvSelectedVideo.text = uri.toString()
            startPlayback(uri)
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
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onPause() {
        vrManager.onPause()
        gyroController.stop()
        binding.sphericalView.onPause()
        player?.playWhenReady = false
        super.onPause()
    }

    override fun onStop() {
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

    private fun tryApplyOrientation(yawDeg: Float, pitchDeg: Float) {
        if (!viewModel.sensorRotationEnabled) return

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