package com.panorama.app.live

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arashivision.sdkmedia.player.capture.CaptureParamsBuilderV2
import com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView
import com.panorama.android.camera.ConnectTransport
import com.panorama.android.camera.ConnectionState

/** Live preview screen: hosts the Insta360 [InstaCapturePlayerView]; once the camera is CONNECTED
 *  it begins the preview stream and prepares the player. Connect buttons stay visible until
 *  streaming. Back returns to the library. The SDK renders into its own view, so none of the app's
 *  Cardboard/ExoPlayer pipeline is involved. */
@Composable
fun LiveScreen(
    onBack: () -> Unit = {},
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var playerRef by remember { mutableStateOf<InstaCapturePlayerView?>(null) }

    var pendingTransport by remember { mutableStateOf<ConnectTransport?>(null) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val t = pendingTransport
        if (t != null && result.values.all { it }) viewModel.connect(t)
        pendingTransport = null
    }
    fun requestConnect(t: ConnectTransport) {
        pendingTransport = t
        // NEARBY_WIFI_DEVICES is the API 33+ replacement that the SDK's Wi-Fi connect needs; on
        // older releases it doesn't exist and FINE_LOCATION covers the Wi-Fi scan instead.
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        permLauncher.launch(perms.toTypedArray())
    }

    DisposableEffect(Unit) {
        viewModel.register()
        onDispose { viewModel.disconnect() }
    }

    DisposableEffect(state.connection, playerRef) {
        val view = playerRef
        if (state.connection == ConnectionState.CONNECTED && view != null) {
            viewModel.startPreview()
            view.prepare(CaptureParamsBuilderV2())
            view.play()
        }
        onDispose { }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                InstaCapturePlayerView(ctx).apply {
                    setLifecycle(lifecycleOwner.lifecycle)
                    playerRef = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { view -> view.destroy(); playerRef = null },
        )

        DisposableEffect(Unit) {
            viewModel.startSensor()
            onDispose { viewModel.stopSensor() }
        }
        LaunchedEffect(playerRef) {
            val view = playerRef ?: return@LaunchedEffect
            while (true) {
                withFrameNanos {
                    val g = viewModel.currentGaze()
                    view.setYawPitchRoll(g.yawDeg, g.pitchDeg, 0f)
                }
            }
        }

        if (state.connection != ConnectionState.STREAMING) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = { requestConnect(ConnectTransport.WIFI) }) { Text("Wi-Fi") }
                FilledTonalButton(onClick = { viewModel.connect(ConnectTransport.USB) }) { Text("USB") }
                FilledTonalButton(onClick = { requestConnect(ConnectTransport.BLE) }) { Text("BLE") }
            }
        }

        Text(
            text = "Camera: ${state.connection.name.lowercase()}",
            modifier = Modifier.align(Alignment.TopCenter).systemBarsPadding().padding(8.dp),
        )

        FilledTonalButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).systemBarsPadding().padding(8.dp),
        ) { Text("‹ Back") }
    }
}
