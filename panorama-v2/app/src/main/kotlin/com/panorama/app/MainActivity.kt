package com.panorama.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.panorama.app.library.LibraryScreen
import com.panorama.app.library.copyToCache
import com.panorama.app.player.PlayerScreen
import dagger.hilt.android.AndroidEntryPoint

/** The single launcher Activity. It hosts a tiny hand-rolled screen switch instead of
 *  Navigation-Compose.
 *
 *  Why no NavHost: the SAF document picker (OpenDocument) is a separate Activity, so while it is up
 *  ours drops below RESUMED. Its result callback fires while we are still only CREATED/STARTED, and
 *  NavController.navigate() is silently dropped when the host is below RESUMED — the long-standing
 *  "video opens only on the second tap" bug. A plain [Screen] state assignment has no such gate: it
 *  recomposes whenever it is written, in any lifecycle state.
 *
 *  Navigation is just `screen = ...`. Back from a sub-screen returns to [Screen.Library]; the system
 *  back button on Library exits the app (default Activity behavior). An external ACTION_VIEW video
 *  (e.g. "Open with" from a file manager) sets the initial screen straight to the player. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewUri = if (intent?.action == Intent.ACTION_VIEW) intent?.data else null
        setContent {
            MaterialTheme {
                Surface {
                    AppRoot(viewUri)
                }
            }
        }
    }
}

/** The viewer's destinations. [Player] carries the chosen video and optional sidecar URIs; settings
 *  are a translucent overlay inside the player, not a destination of their own. */
private sealed interface Screen {
    data object Library : Screen
    data object Live : Screen
    data class Player(val videoUri: Uri, val sidecarUri: Uri?) : Screen
}

@Composable
private fun AppRoot(viewUri: Uri?) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }

    // Honor an external "open this video" intent once, before the user touches the UI. The picked
    // content:// grant is copied to cache here (still on the main thread) so the file:// URI survives
    // the hop into ExoPlayer; switching `screen` works regardless of lifecycle state.
    LaunchedEffect(viewUri) {
        if (viewUri != null) {
            val playable = copyToCache(context, viewUri, "video") ?: viewUri
            screen = Screen.Player(playable, null)
        }
    }

    when (val s = screen) {
        Screen.Library -> LibraryScreen(
            onPlay = { videoUri, sidecarUri -> screen = Screen.Player(videoUri, sidecarUri) },
            onOpenLive = { screen = Screen.Live },
        )
        Screen.Live -> com.panorama.app.live.LiveScreen(onBack = { screen = Screen.Library })
        // key(videoUri) so picking a different clip rebuilds PlayerScreen (fresh LaunchedEffect ->
        // selectMedia) instead of reusing the previous composition. Settings are an in-place
        // translucent overlay PlayerScreen owns, so there is no separate settings destination.
        is Screen.Player -> key(s.videoUri) {
            PlayerScreen(
                videoUri = s.videoUri,
                sidecarUri = s.sidecarUri,
                onBack = { screen = Screen.Library },
            )
        }
    }
}
