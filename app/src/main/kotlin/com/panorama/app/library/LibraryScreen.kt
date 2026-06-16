package com.panorama.app.library

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Entry screen: pick a 360 video (and, optionally, its detection sidecar) via the Storage Access
 *  Framework, then open the player.
 *
 *  Two separate [ActivityResultContracts.OpenDocument] launchers are used so the (optional) sidecar
 *  can be chosen independently of the video. Picking the video calls [onPlay] straight from the
 *  result callback; the sidecar, when picked first, is remembered and carried along on the next
 *  video pick.
 *
 *  [onPlay] only flips a parent [Screen] state (see MainActivity), so it is safe to call directly
 *  from the picker callback even though the Activity is still below RESUMED at that point — unlike
 *  NavController.navigate(), a state write is never gated on lifecycle. */
@Composable
fun LibraryScreen(
    onPlay: (videoUri: Uri, sidecarUri: Uri?) -> Unit,
    onOpenLive: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickedSidecar by remember { mutableStateOf<Uri?>(null) }
    // True while a picked file is being copied into cache off the main thread (blocks re-entry and
    // shows progress). The SAF read grant stays valid because we remain on this screen until done.
    var copying by remember { mutableStateOf(false) }

    // The SAF read grant from OpenDocument is tied to THIS Activity result and is lost once we leave
    // this screen, and is not reliably persistable from a DocumentsProvider. So while still on this
    // screen we copy the picked content:// stream into a cache file and carry a file:// Uri forward
    // (file:// needs no grant, so ExoPlayer/SidecarLoader read it on a background thread later).
    // The copy runs on Dispatchers.IO — 360 clips can be GBs, and copying on the main thread ANRs.
    val sidecarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            copying = true
            scope.launch {
                val cached = withContext(Dispatchers.IO) { copyToCache(context, uri, "sidecar") }
                pickedSidecar = cached ?: uri
                copying = false
            }
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            copying = true
            scope.launch {
                val playable = withContext(Dispatchers.IO) { copyToCache(context, uri, "video") } ?: uri
                copying = false
                onPlay(playable, pickedSidecar)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (copying) {
            CircularProgressIndicator()
            Text("Importing…")
        } else {
            Button(onClick = { videoLauncher.launch(arrayOf("video/*")) }) {
                Text("Open 360 video")
            }
            OutlinedButton(onClick = { sidecarLauncher.launch(arrayOf("application/json")) }) {
                Text(if (pickedSidecar != null) "Sidecar selected (optional)" else "Pick detections sidecar (optional)")
            }
            Button(onClick = onOpenLive) {
                Text("Live camera")
            }
        }
    }
}

/** Copies the [source] content:// stream into the app cache and returns a file:// [Uri] for it, or
 *  null if the copy failed (the caller then falls back to the original Uri). Call it off the main
 *  thread (Dispatchers.IO) — clips can be GBs; the SAF read grant stays valid as long as the caller
 *  is still on the screen. The resulting file needs no grant, so it survives the screen switch and a
 *  background-thread open. */
internal fun copyToCache(context: Context, source: Uri, prefix: String): Uri? = runCatching {
    val dest = File(context.cacheDir, "$prefix-${System.currentTimeMillis()}")
    context.contentResolver.openInputStream(source)?.use { input ->
        dest.outputStream().use { output -> input.copyTo(output) }
    } ?: return null
    Uri.fromFile(dest)
}.getOrNull()
