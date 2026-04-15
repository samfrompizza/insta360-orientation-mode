package com.arashivision.sdk.demo.ui.player

import android.content.Intent
import android.net.Uri
import android.view.ScaleGestureDetector
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.arashivision.sdk.demo.R
import com.arashivision.sdk.demo.base.BaseActivity
import com.arashivision.sdk.demo.base.BaseEvent
import com.arashivision.sdk.demo.databinding.ActivityLocalSphericalPlayerBinding

/**
 * Локальный режим: берет equirectangular-видео с телефона и рендерит его на сферу.
 * Поддержка:
 * - офлайн-воспроизведение без подключения к камере;
 * - head-tracking через useSensorRotation;
 * - VR split-screen без лишних оверлеев;
 * - loop playback;
 * - zoom (кнопки + pinch).
 */
class LocalSphericalPlayerActivity :
    BaseActivity<ActivityLocalSphericalPlayerBinding, LocalSphericalPlayerViewModel>() {

    private var mainPlayer: ExoPlayer? = null
    private var vrPlayer: ExoPlayer? = null

    private var sensorRotationEnabled = true
    private var isVrMode = false
    private var currentVideoUri: Uri? = null

    private var zoomFactor = 1.0f
    private val minZoom = 1.0f
    private val maxZoom = 2.5f

    private lateinit var scaleGestureDetector: ScaleGestureDetector

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
        setupSphericalViews()
        scaleGestureDetector = ScaleGestureDetector(this, ZoomGestureListener())
        binding.sphericalContainer.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            isVrMode
        }
        updateZoom()
        updateVrUi()
    }

    override fun initListener() {
        super.initListener()
        binding.btnPickVideo.setOnClickListener { pickVideoLauncher.launch(arrayOf("video/*")) }

        binding.btnPlayPause.setOnClickListener {
            val newPlayState = !(mainPlayer?.isPlaying ?: false)
            setPlayWhenReady(newPlayState)
            syncPlayPauseButton()
        }

        binding.btnToggleSensor.setOnClickListener {
            sensorRotationEnabled = !sensorRotationEnabled
            binding.sphericalView.setUseSensorRotation(sensorRotationEnabled)
            binding.sphericalViewSecondary.setUseSensorRotation(sensorRotationEnabled)
            binding.btnToggleSensor.text = if (sensorRotationEnabled) {
                getString(R.string.disable_gyro_control)
            } else {
                getString(R.string.enable_gyro_control)
            }
        }

        binding.btnCenterView.setOnClickListener {
            recenterSensorView()
            toast(R.string.gyro_recentered)
        }

        binding.btnToggleVr.setOnClickListener {
            isVrMode = !isVrMode
            updateVrUi()
        }

        binding.btnZoomIn.setOnClickListener {
            zoomFactor = (zoomFactor + 0.1f).coerceIn(minZoom, maxZoom)
            updateZoom()
        }

        binding.btnZoomOut.setOnClickListener {
            zoomFactor = (zoomFactor - 0.1f).coerceIn(minZoom, maxZoom)
            updateZoom()
        }
    }

    override fun onEvent(event: BaseEvent) = Unit

    override fun onStart() {
        super.onStart()
        ensurePlayers()
    }

    override fun onResume() {
        super.onResume()
        setPlayWhenReady(true)
        syncPlayPauseButton()
    }

    override fun onPause() {
        setPlayWhenReady(false)
        super.onPause()
    }

    override fun onStop() {
        releasePlayers()
        super.onStop()
    }

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
        val playing = mainPlayer?.isPlaying == true
        binding.btnPlayPause.text = if (playing) getString(R.string.pause) else getString(R.string.play)
    }

    private inner class ZoomGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomFactor = (zoomFactor * detector.scaleFactor).coerceIn(minZoom, maxZoom)
            updateZoom()
            return true
        }
    }
}
