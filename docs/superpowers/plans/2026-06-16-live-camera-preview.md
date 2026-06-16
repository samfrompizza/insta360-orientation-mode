# Live Camera Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Live" screen that connects to an Insta360 camera (Wi-Fi/USB/BLE) and shows the live panoramic preview, steered by the phone gyroscope.

**Architecture:** A self-contained screen built on the Insta360 SDK's `InstaCapturePlayerView` (the SDK renders into its own FrameLayout — it cannot feed the app's Cardboard/OES pipeline). A `CameraConnection` wrapper isolates `InstaCameraManager`; `LiveViewModel` exposes a connection state machine; `LiveScreen` hosts the SDK player and connect buttons; the existing `OrientationEngine` drives the SDK player's native `setYawPitchRoll`.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Insta360 SDK (`com.arashivision.sdk:sdkcamera:1.8.1_build_06`, `:sdkmedia:1.8.1_build_06` — present in the gradle cache), JUnit4 + Robolectric + MockK.

---

## Background facts (read before starting)

- SDK coordinates (verified in `~/.gradle/caches/modules-2/.../com.arashivision.sdk/...`):
  `com.arashivision.sdk:sdkcamera:1.8.1_build_06`, `com.arashivision.sdk:sdkmedia:1.8.1_build_06`.
  They resolve offline from the cache. They transitively pull `androidx.appcompat`.
- SDK init (old project's Application did this): `InstaCameraSDK.init(this)` and `InstaMediaSDK.init(this)` in `Application.onCreate()`. Imports: `com.arashivision.sdkcamera.InstaCameraSDK`, `com.arashivision.sdkmedia.InstaMediaSDK`.
- `InstaCameraManager` (`com.arashivision.sdkcamera.camera.InstaCameraManager`):
  - `InstaCameraManager.getInstance()` — singleton.
  - Constants: `CONNECT_TYPE_USB`, `CONNECT_TYPE_WIFI`, `CONNECT_TYPE_BLE`, `CONNECT_TYPE_NONE`; `PREVIEW_TYPE_NORMAL`.
  - `openCamera(int connectType)`, `closeCamera()`.
  - `registerCameraChangedCallback(ICameraChangedCallback)`, `unregisterCameraChangedCallback(...)`.
  - `startPreviewStream(int previewType)`, `closePreviewStream()`, `setPreviewStatusChangedListener(IPreviewStatusListener)`.
- `ICameraChangedCallback` (`...camera.callback.ICameraChangedCallback`, all methods are `default`):
  `onCameraStatusChanged(boolean enabled, int connectType)`, `onCameraConnectError(int errorCode)`.
- `IPreviewStatusListener` (`...camera.callback.IPreviewStatusListener`, all `default`):
  `onOpening()`, `onOpened()`, `onIdle()`, `onError()`.
- `InstaCapturePlayerView` (`com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView extends FrameLayout`):
  `setLifecycle(Lifecycle)`, `prepare(CaptureParamsBuilderV2)`, `play()`, `destroy()`,
  `setYawPitchRoll(float, float, float)`, `setLifecycle`, plus a `PlayerViewListener`. The live params
  builder is `com.arashivision.sdkmedia.player.capture.CaptureParamsBuilderV2`.
- `OrientationEngine` (`com.panorama.android.sensor.OrientationEngine`): `start()`, `stop()`,
  `currentGaze(): GazeState`, `gazeRef: AtomicReference<GazeState>`. `GazeState` has `yawDeg`, `pitchDeg`.
- Navigation: `app/.../MainActivity.kt` has `private sealed interface Screen { Library; Player(...) }`
  and an `AppRoot` `when (screen)`. `LibraryScreen(onPlay=...)` is the library.
- Hilt: app uses `@HiltViewModel`; `PanoramaApp` is the `@HiltAndroidApp` Application (find it:
  `grep -rl HiltAndroidApp app/src/main`).
- Build/run via context-mode `ctx_execute(language:"shell", ...)`, NOT raw Bash (gradle hook). Tests:
  `:app:testDebugUnitTest`, `:android:testDebugUnitTest`. Note `:android:testDebugUnitTest` currently
  FAILS on a pre-existing AAPT error (Cardboard AAR theme) unrelated to this work — run
  `:app:testDebugUnitTest` for the unit tests added here (LiveViewModel lives in `:app`).
- This whole feature is verifiable ONLY on a real camera; unit tests cover the state machine only.

## File Structure

- **Modify** `gradle/libs.versions.toml` — add `insta-camera`, `insta-media`, `androidx-appcompat` library entries + version.
- **Modify** `app/build.gradle.kts` — depend on the three.
- **Modify** the `@HiltAndroidApp` Application — call `InstaCameraSDK.init` / `InstaMediaSDK.init`.
- **Create** `android/src/main/kotlin/com/panorama/android/camera/CameraConnection.kt` — `InstaCameraManager` wrapper + `ConnectionState`.
- **Create** `app/src/main/kotlin/com/panorama/app/live/LiveUiState.kt` — UI state.
- **Create** `app/src/main/kotlin/com/panorama/app/live/LiveViewModel.kt` — state machine.
- **Create** `app/src/test/kotlin/com/panorama/app/live/LiveViewModelTest.kt` — state-machine tests.
- **Create** `app/src/main/kotlin/com/panorama/app/live/LiveScreen.kt` — Compose screen with `InstaCapturePlayerView`.
- **Modify** `app/.../MainActivity.kt` — `Screen.Live` + route.
- **Modify** `app/.../library/LibraryScreen.kt` — "Live" button.
- **Modify** `app/src/main/AndroidManifest.xml` — permissions.
- **Modify** `app/src/main/kotlin/com/panorama/app/di/AppModule.kt` — provide `CameraConnection`.

---

### Task 1: Add the Insta360 SDK dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add version + libraries to the catalog**

In `gradle/libs.versions.toml`, under `[versions]` add:
```toml
insta = "1.8.1_build_06"
appcompat = "1.7.0"
```
Under `[libraries]` add:
```toml
insta-camera = { group = "com.arashivision.sdk", name = "sdkcamera", version.ref = "insta" }
insta-media = { group = "com.arashivision.sdk", name = "sdkmedia", version.ref = "insta" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
```

- [ ] **Step 2: Depend on them in :app**

In `app/build.gradle.kts` `dependencies { }` add:
```kotlin
    implementation(libs.insta.camera)
    implementation(libs.insta.media)
    implementation(libs.androidx.appcompat)
```

- [ ] **Step 3: Build to verify resolution (offline cache)**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (artifacts come from the cache; no network needed). If it fails to
resolve, STOP and report — the coordinates/version may differ from the cache.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build(app): add Insta360 sdkcamera/sdkmedia + appcompat for live preview"
```

---

### Task 2: Initialize the SDK in the Application

**Files:**
- Modify: the `@HiltAndroidApp` Application class (find with `grep -rl HiltAndroidApp app/src/main`)

- [ ] **Step 1: Add SDK init**

In the Application's `onCreate()`, after `super.onCreate()`, add:
```kotlin
        com.arashivision.sdkcamera.InstaCameraSDK.init(this)
        com.arashivision.sdkmedia.InstaMediaSDK.init(this)
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main
git commit -m "feat(app): initialize the Insta360 camera/media SDK on app start"
```

---

### Task 3: CameraConnection wrapper + ConnectionState

**Files:**
- Create: `android/src/main/kotlin/com/panorama/android/camera/CameraConnection.kt`

- [ ] **Step 1: Write CameraConnection**

```kotlin
package com.panorama.android.camera

import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.callback.ICameraChangedCallback
import com.arashivision.sdkcamera.camera.callback.IPreviewStatusListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Connection lifecycle of the Insta360 camera, derived from the SDK callbacks. */
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, STREAMING, ERROR }

/** The connect transports the SDK supports. */
enum class ConnectTransport(val sdkType: Int) {
    WIFI(InstaCameraManager.CONNECT_TYPE_WIFI),
    USB(InstaCameraManager.CONNECT_TYPE_USB),
    BLE(InstaCameraManager.CONNECT_TYPE_BLE),
}

/** Thin wrapper over [InstaCameraManager] that exposes a single [state] flow and open/close/preview
 *  actions, hiding all SDK callback wiring from the rest of the app. The SDK renders the preview
 *  into an [com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView] separately; this class
 *  only manages connection + preview-stream lifecycle and reports state. */
class CameraConnection(
    private val manager: InstaCameraManager = InstaCameraManager.getInstance(),
) {
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val cameraCallback = object : ICameraChangedCallback {
        override fun onCameraStatusChanged(enabled: Boolean, connectType: Int) {
            _state.value = if (enabled) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
        }
        override fun onCameraConnectError(errorCode: Int) {
            _state.value = ConnectionState.ERROR
        }
    }

    private val previewListener = object : IPreviewStatusListener {
        override fun onOpening() { _state.value = ConnectionState.CONNECTING }
        override fun onOpened() { _state.value = ConnectionState.STREAMING }
        override fun onIdle() { _state.value = ConnectionState.CONNECTED }
        override fun onError() { _state.value = ConnectionState.ERROR }
    }

    /** Register callbacks. Call once when the live screen starts. */
    fun register() {
        manager.registerCameraChangedCallback(cameraCallback)
        manager.setPreviewStatusChangedListener(previewListener)
    }

    /** Open a connection over the given transport. State advances via the camera callback. */
    fun connect(transport: ConnectTransport) {
        _state.value = ConnectionState.CONNECTING
        manager.openCamera(transport.sdkType)
    }

    /** Begin the live preview stream (after CONNECTED). */
    fun startPreview() {
        manager.startPreviewStream(InstaCameraManager.PREVIEW_TYPE_NORMAL)
    }

    /** Stop preview + close the camera + unregister. Call from the screen's teardown. */
    fun disconnect() {
        manager.closePreviewStream()
        manager.setPreviewStatusChangedListener(null)
        manager.unregisterCameraChangedCallback(cameraCallback)
        manager.closeCamera()
        _state.value = ConnectionState.DISCONNECTED
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :android:assembleDebug 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/src/main/kotlin/com/panorama/android/camera/CameraConnection.kt
git commit -m "feat(android): CameraConnection wrapper over InstaCameraManager"
```

---

### Task 4: LiveUiState + LiveViewModel (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/panorama/app/live/LiveUiState.kt`
- Create: `app/src/main/kotlin/com/panorama/app/live/LiveViewModel.kt`
- Test: `app/src/test/kotlin/com/panorama/app/live/LiveViewModelTest.kt`

- [ ] **Step 1: Write LiveUiState**

```kotlin
package com.panorama.app.live

import com.panorama.android.camera.ConnectionState

/** Slow UI snapshot for the live screen. */
data class LiveUiState(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
)
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.panorama.app.live

import com.panorama.android.camera.CameraConnection
import com.panorama.android.camera.ConnectTransport
import com.panorama.android.camera.ConnectionState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveViewModelTest {

    private fun fakeConnection(state: MutableStateFlow<ConnectionState>): CameraConnection {
        val conn = mockk<CameraConnection>(relaxed = true)
        every { conn.state } returns state
        return conn
    }

    @Test
    fun `mirrors connection state into ui state`() = runTest {
        val flow = MutableStateFlow(ConnectionState.DISCONNECTED)
        val vm = LiveViewModel(fakeConnection(flow), backgroundScope = backgroundScope)
        flow.value = ConnectionState.STREAMING
        // let the mirror collector run
        kotlinx.coroutines.test.advanceTimeBy(10)
        assertEquals(ConnectionState.STREAMING, vm.state.value.connection)
    }

    @Test
    fun `connectWifi delegates to the connection`() = runTest {
        val flow = MutableStateFlow(ConnectionState.DISCONNECTED)
        val conn = fakeConnection(flow)
        val vm = LiveViewModel(conn, backgroundScope = backgroundScope)
        vm.connect(ConnectTransport.WIFI)
        verify { conn.connect(ConnectTransport.WIFI) }
    }
}
```

- [ ] **Step 3: Run the test, verify it FAILS (LiveViewModel doesn't exist)**

Run: `./gradlew :app:testDebugUnitTest --tests '*LiveViewModelTest*' 2>&1 | tail -20`
Expected: compile failure / FAIL (no `LiveViewModel`).

- [ ] **Step 4: Write LiveViewModel**

```kotlin
package com.panorama.app.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panorama.android.camera.CameraConnection
import com.panorama.android.camera.ConnectTransport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Drives the live screen: mirrors [CameraConnection.state] into [state] and forwards connect /
 *  disconnect / preview actions. The endless mirror collector runs in [backgroundScope] (default
 *  [viewModelScope]) so a test can hand in runTest's auto-cancelled scope. */
@HiltViewModel
class LiveViewModel(
    private val connection: CameraConnection,
    backgroundScope: CoroutineScope? = null,
) : ViewModel() {

    @Inject
    constructor(connection: CameraConnection) : this(connection, backgroundScope = null)

    private val _state = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    init {
        val scope = backgroundScope ?: viewModelScope
        scope.launch {
            connection.state.collect { c -> _state.update { it.copy(connection = c) } }
        }
    }

    fun register() = connection.register()
    fun connect(transport: ConnectTransport) = connection.connect(transport)
    fun startPreview() = connection.startPreview()
    fun disconnect() = connection.disconnect()
}
```

- [ ] **Step 5: Run the test, verify it PASSES**

Run: `./gradlew :app:testDebugUnitTest --tests '*LiveViewModelTest*' 2>&1 | tail -20`
Expected: PASS (2 tests).

- [ ] **Step 6: Provide CameraConnection in Hilt**

In `app/src/main/kotlin/com/panorama/app/di/AppModule.kt`, add a provider (match the file's existing
`@Provides @Singleton` style):
```kotlin
    @Provides
    @Singleton
    fun provideCameraConnection(): com.panorama.android.camera.CameraConnection =
        com.panorama.android.camera.CameraConnection()
```

- [ ] **Step 7: Build + commit**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest 2>&1 | tail -15` → BUILD SUCCESSFUL.
```bash
git add app/src/main/kotlin/com/panorama/app/live app/src/test/kotlin/com/panorama/app/live app/src/main/kotlin/com/panorama/app/di/AppModule.kt
git commit -m "feat(app): LiveViewModel + connection state machine (tested)"
```

---

### Task 5: LiveScreen with the SDK preview player

**Files:**
- Create: `app/src/main/kotlin/com/panorama/app/live/LiveScreen.kt`

- [ ] **Step 1: Write LiveScreen**

```kotlin
package com.panorama.app.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    DisposableEffect(Unit) {
        viewModel.register()
        onDispose { viewModel.disconnect() }
    }

    // When the camera reaches CONNECTED, start the stream + prepare/play the SDK view.
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

        if (state.connection != ConnectionState.STREAMING) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = { viewModel.connect(ConnectTransport.WIFI) }) { Text("Wi-Fi") }
                FilledTonalButton(onClick = { viewModel.connect(ConnectTransport.USB) }) { Text("USB") }
                FilledTonalButton(onClick = { viewModel.connect(ConnectTransport.BLE) }) { Text("BLE") }
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
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL. If `CaptureParamsBuilderV2()` needs required args, check the class with
`javap -classpath <sdkmedia classes.jar> com.arashivision.sdkmedia.player.capture.CaptureParamsBuilderV2`
and use the no-arg/default builder; STOP and report if the API differs.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/panorama/app/live/LiveScreen.kt
git commit -m "feat(app): LiveScreen hosting the Insta360 preview player"
```

---

### Task 6: Navigation — Library button + Screen.Live route

**Files:**
- Modify: `app/src/main/kotlin/com/panorama/app/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/panorama/app/library/LibraryScreen.kt`

- [ ] **Step 1: Add the route**

In `MainActivity.kt`, add to the `Screen` sealed interface:
```kotlin
    data object Live : Screen
```
In the `AppRoot` `when (val s = screen)`, add a branch:
```kotlin
        Screen.Live -> com.panorama.app.live.LiveScreen(onBack = { screen = Screen.Library })
```
And pass an `onOpenLive` lambda to `LibraryScreen`:
```kotlin
        Screen.Library -> LibraryScreen(
            onPlay = { videoUri, sidecarUri -> screen = Screen.Player(videoUri, sidecarUri) },
            onOpenLive = { screen = Screen.Live },
        )
```

- [ ] **Step 2: Add the Library button**

In `LibraryScreen.kt`, add the param:
```kotlin
fun LibraryScreen(
    onPlay: (videoUri: Uri, sidecarUri: Uri?) -> Unit,
    onOpenLive: () -> Unit,
) {
```
And a button in the `Column` (after the existing buttons):
```kotlin
        androidx.compose.material3.Button(onClick = onOpenLive) {
            androidx.compose.material3.Text("Live camera")
        }
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/panorama/app/MainActivity.kt app/src/main/kotlin/com/panorama/app/library/LibraryScreen.kt
git commit -m "feat(app): navigate to the live camera screen from the library"
```

---

### Task 7: Gyro → view angle

**Files:**
- Modify: `app/src/main/kotlin/com/panorama/app/live/LiveScreen.kt`
- Modify: `app/src/main/kotlin/com/panorama/app/live/LiveViewModel.kt`

- [ ] **Step 1: Expose the engine gaze from the ViewModel**

`LiveViewModel` needs the `OrientationEngine`. Add it to the constructor and expose its gaze. Update
both constructors:
```kotlin
@HiltViewModel
class LiveViewModel(
    private val connection: CameraConnection,
    private val orientationEngine: com.panorama.android.sensor.OrientationEngine,
    backgroundScope: CoroutineScope? = null,
) : ViewModel() {

    @Inject
    constructor(
        connection: CameraConnection,
        orientationEngine: com.panorama.android.sensor.OrientationEngine,
    ) : this(connection, orientationEngine, backgroundScope = null)
```
Add:
```kotlin
    fun startSensor() = orientationEngine.start()
    fun stopSensor() = orientationEngine.stop()
    fun currentGaze() = orientationEngine.currentGaze()
```
Update `LiveViewModelTest` constructor calls to pass a `mockk<OrientationEngine>(relaxed = true)`.

- [ ] **Step 2: Drive setYawPitchRoll from the gaze on the frame clock**

In `LiveScreen.kt`, add inside the `Box` (after the AndroidView), a sensor lifecycle + per-frame
angle push:
```kotlin
        DisposableEffect(Unit) {
            viewModel.startSensor()
            onDispose { viewModel.stopSensor() }
        }
        androidx.compose.runtime.LaunchedEffect(playerRef) {
            val view = playerRef ?: return@LaunchedEffect
            while (true) {
                androidx.compose.runtime.withFrameNanos {
                    val g = viewModel.currentGaze()
                    view.setYawPitchRoll(g.yawDeg, g.pitchDeg, 0f)
                }
            }
        }
```

- [ ] **Step 3: Build + tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL, LiveViewModelTest passes (with the added OrientationEngine mock).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/panorama/app/live
git commit -m "feat(app): steer the live preview angle with the gyroscope"
```

---

### Task 8: Permissions

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add permissions + USB host feature**

In `AndroidManifest.xml`, inside `<manifest>` (before `<application>`), add any not already present:
```xml
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-feature android:name="android.hardware.usb.host" android:required="false" />
```

- [ ] **Step 2: Request runtime permissions before connecting**

In `LiveScreen.kt`, gate the connect buttons behind a runtime permission request using
`rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions())`. On a
connect tap, launch the request for `ACCESS_FINE_LOCATION`, `NEARBY_WIFI_DEVICES` (API 33+),
`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`; in the result callback, if granted, call
`viewModel.connect(transport)`; if denied, set a local `var permError by remember { mutableStateOf(false) }`
and show `Text("Grant Wi-Fi/Bluetooth permissions to connect")` instead of crashing. Keep the buttons
visible to retry. (USB uses the attach intent, no runtime permission.)

```kotlin
        val context = androidx.compose.ui.platform.LocalContext.current
        var pendingTransport by remember { mutableStateOf<ConnectTransport?>(null) }
        val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            val t = pendingTransport
            if (t != null && result.values.all { it }) viewModel.connect(t)
            pendingTransport = null
        }
        fun requestConnect(t: ConnectTransport) {
            pendingTransport = t
            permLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                ),
            )
        }
```
Wire the three connect buttons to `requestConnect(...)` instead of `viewModel.connect(...)` directly.

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/kotlin/com/panorama/app/live/LiveScreen.kt
git commit -m "feat(app): runtime permissions for camera Wi-Fi/BLE connect"
```

---

### Task 9: On-device verification (requires the camera)

**Files:** none (manual).

- [ ] **Step 1: Install + launch**

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.panorama.app/.MainActivity
```

- [ ] **Step 2: Connect**

With the user: tap "Live camera" → grant permissions → power the camera, tap Wi-Fi (or USB/BLE).
Confirm the status line moves DISCONNECTED → CONNECTING → CONNECTED → STREAMING.

- [ ] **Step 3: Live frame**

Confirm a live panoramic frame renders in the SDK view.

- [ ] **Step 4: Gyro pan**

Confirm turning the phone pans the live view (setYawPitchRoll). Note if axes are inverted/swapped;
if so, adjust signs in the `setYawPitchRoll(g.yawDeg, g.pitchDeg, 0f)` call and rebuild.

- [ ] **Step 5: Teardown**

Leave the screen (Back); confirm no crash and the camera disconnects cleanly.

- [ ] **Step 6: Commit any axis tuning**

```bash
git add app/src/main/kotlin/com/panorama/app/live/LiveScreen.kt
git commit -m "fix(app): correct live gyro axis mapping"
```

---

## Notes / risks

- **Camera-only verification:** Tasks 1-8 build green without a camera; Task 9 needs the hardware (user, evening).
- **`CaptureParamsBuilderV2` API:** the no-arg constructor is assumed; if the SDK requires a stitch type / live type argument, inspect the class (javap) and set it (the old project built it in `getCaptureParams()`); STOP and report rather than guessing.
- **Gyro axis convention** for `setYawPitchRoll` is the SDK's, likely different from `OrientationEngine` — Task 9 step 4/6 tunes the signs on device.
- **`:android:testDebugUnitTest`** is pre-existing-broken (Cardboard AAR AAPT theme); LiveViewModel tests live in `:app`, run `:app:testDebugUnitTest`.
- **SDK init** must run before any `InstaCameraManager.getInstance()` use; Task 2 puts it in the Application, which constructs before any screen.
