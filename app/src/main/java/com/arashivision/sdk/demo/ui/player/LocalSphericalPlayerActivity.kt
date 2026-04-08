package com.arashivision.sdk.demo.ui.player

import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.arashivision.sdk.demo.R
import com.arashivision.sdk.demo.base.BaseActivity
import com.arashivision.sdk.demo.base.BaseEvent
import com.arashivision.sdk.demo.databinding.ActivityLocalSphericalPlayerBinding
import com.arashivision.sdk.demo.ui.capture.GyroOrientationController

/**
 * Локальный режим: берет equirectangular-видео с телефона и рендерит его на сферу,
 * чтобы управлять обзором гироскопом.
 */
class LocalSphericalPlayerActivity :
    BaseActivity<ActivityLocalSphericalPlayerBinding, LocalSphericalPlayerViewModel>() {

    private var player: ExoPlayer? = null
    private lateinit var gyroController: GyroOrientationController
    private var sensorRotationEnabled: Boolean = true

    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            kotlin.runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            binding.tvSelectedVideo.text = uri.toString()
            startPlayback(uri)
        }

    override fun initView() {
        super.initView()
        binding.sphericalView.setDefaultStereoMode(C.STEREO_MODE_MONO)
        sensorRotationEnabled = true
        binding.sphericalView.setUseSensorRotation(sensorRotationEnabled)

        gyroController = GyroOrientationController(
            context = this,
            getDisplayRotation = { windowManager.defaultDisplay.rotation },
            applyOrientation = { _, _ -> }
        )
    }

    override fun initListener() {
        super.initListener()
        binding.btnPickVideo.setOnClickListener { pickVideoLauncher.launch(arrayOf("video/*")) }

        binding.btnToggleSensor.setOnClickListener {
            sensorRotationEnabled = !sensorRotationEnabled
            binding.sphericalView.setUseSensorRotation(sensorRotationEnabled)
            binding.btnToggleSensor.text = if (sensorRotationEnabled) getString(R.string.disable_gyro_control) else getString(R.string.enable_gyro_control)
        }

        binding.btnCenterView.setOnClickListener {
            gyroController.calibrate()
            toast(R.string.gyro_recentered)
        }

        binding.btnPlayPause.setOnClickListener {
            player?.let {
                it.playWhenReady = !it.playWhenReady
                syncPlayPauseButton()
            }
        }
    }

    override fun onEvent(event: BaseEvent) = Unit

    override fun onStart() {
        super.onStart()
        if (player == null) {
            player = ExoPlayer.Builder(this).build().also { exo ->
                exo.setVideoSurfaceView(binding.sphericalView)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        gyroController.start()
        player?.playWhenReady = true
        syncPlayPauseButton()
    }

    override fun onPause() {
        gyroController.stop()
        player?.playWhenReady = false
        super.onPause()
    }

    override fun onStop() {
        player?.release()
        player = null
        super.onStop()
    }

    private fun startPlayback(uri: Uri) {
        val exo = player ?: return
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
        exo.playWhenReady = true
        syncPlayPauseButton()
    }

    private fun syncPlayPauseButton() {
        val playing = player?.playWhenReady == true
        binding.btnPlayPause.text = if (playing) getString(R.string.pause) else getString(R.string.play)
    }
}
