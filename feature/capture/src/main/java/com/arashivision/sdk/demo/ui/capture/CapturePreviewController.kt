package com.arashivision.sdk.demo.ui.capture

import com.arashivision.sdk.demo.ext.instaCameraManager
import com.arashivision.sdkmedia.player.capture.CaptureParamsBuilderV2
import com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView
import com.arashivision.sdkmedia.player.listener.PlayerViewListener
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog

class CapturePreviewController(
    private val playerView: InstaCapturePlayerView,
    private val onCalibrateGyro: () -> Unit,
    private val onHideLoading: () -> Unit,
) {
    private val logger: Logger = XLog.tag("CapturePreviewController").build()

    fun displayPreviewStream(
        paramsBuilder: CaptureParamsBuilderV2,
        lifecycle: androidx.lifecycle.Lifecycle,
    ) {
        playerView.setLifecycle(lifecycle)
        playerView.setPlayerViewListener(
            object : PlayerViewListener {
                override fun onFirstFrameRender() {
                    onHideLoading()
                    onCalibrateGyro()
                    logger.d("Gyro: controller calibrated (requested)")
                }

                override fun onLoadingFinish() {
                    runCatching { instaCameraManager.setPipeline(playerView.pipeline) }
                }

                override fun onReleaseCameraPipeline() {
                    logger.d("Main player pipeline released (ignoring)")
                }
            },
        )

        playerView.prepare(paramsBuilder)
        playerView.play()
        playerView.keepScreenOn = true
    }

    fun replay(paramsBuilder: CaptureParamsBuilderV2) {
        playerView.prepare(paramsBuilder)
        playerView.play()
    }

    fun play() {
        playerView.play()
    }

    fun destroy() {
        playerView.destroy()
    }
}
