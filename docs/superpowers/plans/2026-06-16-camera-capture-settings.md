# Camera Capture + Basic Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On the existing live screen, shoot photo/video on the connected Insta360 camera and adjust basic settings (ISO, shutter, EV, WB, photo/record resolution).

**Architecture:** Extend the existing `CameraConnection` (SDK wrapper) with capture actions + an `ICaptureStatusListener` state machine and a generic 6-setting get/set/support dispatch; surface them through `LiveViewModel`; add a shutter + mode toggle + settings panel to `LiveScreen`. No new screen, no new modules. The SDK renders preview in its own view; capture/settings are pure-manager calls.

**Tech Stack:** Kotlin, Compose, Hilt, Insta360 SDK (`com.arashivision.sdk:sdkcamera:1.8.1_build_06`), JUnit4 + MockK + Robolectric.

---

## Background facts (read before starting)

Project root note: the app now lives at the REPO ROOT (no `panorama-v2/` prefix). Build/test via
`./gradlew ...` from the repo root; use context-mode ctx_execute shell if gradle is hook-redirected.
`:app:testDebugUnitTest` is the unit-test task for `LiveViewModel`. (`:android:testDebugUnitTest` is
pre-existing-broken on an AAPT theme error — ignore it.)

Verified SDK API (`com.arashivision.sdkcamera.camera.InstaCameraManager`, singleton `getInstance()`):
- Capture: `startNormalCapture()`, `startNormalRecord()`, `stopNormalRecord()`.
- `isSdCardEnabled(): Boolean`, `isCameraWorking(): Boolean`.
- `setCaptureStatusListener(ICaptureStatusListener)` — listener (`...camera.callback.ICaptureStatusListener`):
  `onCaptureStarting()`, `onCaptureWorking()`, `onCaptureStopping()` (default);
  `onCaptureFinish(paths: Array<String>)`, `onCaptureError(code: Int)` (abstract);
  `onCaptureTimeChanged(t: Long)`, `onCaptureCountChanged(n: Int)` (default).
- Modes (`...camera.model.CaptureMode`): `CAPTURE_NORMAL`, `RECORD_NORMAL`.
- Settings enum (`...camera.model.CaptureSetting`): `ISO`, `SHUTTER`, `EV`, `WB`, `RECORD_RESOLUTION`, `PHOTO_RESOLUTION`.
- Per-setting calls (all take `CaptureMode`):
  - ISO: `getISO`, `setISO`, `getSupportISOList`
  - Shutter: `getShutter`, `setShutter`, `getSupportShutterList`
  - EV: `getEv`, `setEv`, `getSupportEVList`
  - WB: `getWB`, `setWB`, `getSupportWBList`
  - RecordResolution: `getRecordResolution`, `setRecordResolution`, `getSupportRecordResolutionList`
  - PhotoResolution: `getPhotoResolution`, `setPhotoResolution`, `getSupportPhotoResolutionList`
- Setting model types (`...camera.model.{ISO,Shutter,EV,WB,RecordResolution,PhotoResolution}`) are
  Java enums (`extends Enum`), e.g. `ISO.ISO_AUTO`, `WB.WB_AUTO`. Use `.name` for display.
- Constraints: settings can only be written while the preview stream is OPEN (our
  `ConnectionState.STREAMING`); recording requires `isSdCardEnabled()`.

Existing code:
- `android/src/main/kotlin/com/panorama/android/camera/CameraConnection.kt` — wraps the manager; has
  `state: StateFlow<ConnectionState>` (DISCONNECTED/CONNECTING/CONNECTED/STREAMING/ERROR),
  `register()`, `connect(transport)`, `startPreview()`, `disconnect()`. `register()` already wires
  `ICameraChangedCallback` + `IPreviewStatusListener`; `disconnect()` clears them.
- `app/src/main/kotlin/com/panorama/app/live/LiveViewModel.kt` — `@HiltViewModel`, mirrors
  `connection.state` into `LiveUiState`, injects `OrientationEngine`; has a `backgroundScope` test seam.
  Test: `app/src/test/kotlin/com/panorama/app/live/LiveViewModelTest.kt` (MockK CameraConnection +
  OrientationEngine).
- `app/src/main/kotlin/com/panorama/app/live/LiveScreen.kt` — hosts `InstaCapturePlayerView`; shows
  connect buttons until STREAMING; gyro loop; back button.

## File Structure

- **Modify** `CameraConnection.kt` — capture state machine + capture actions + generic settings dispatch.
- **Create** `android/src/main/kotlin/com/panorama/android/camera/CameraSetting.kt` — a small domain
  enum + option/value value-types decoupling UI from SDK enums.
- **Modify** `LiveViewModel.kt` — capture + settings state/actions.
- **Modify** `app/src/test/kotlin/com/panorama/app/live/LiveViewModelTest.kt` — capture/settings tests.
- **Modify** `LiveScreen.kt` — shutter + mode toggle + settings panel.

---

### Task 1: Capture state machine + actions in CameraConnection

**Files:**
- Modify: `android/src/main/kotlin/com/panorama/android/camera/CameraConnection.kt`

- [ ] **Step 1: Add capture state types + flows + listener + actions**

Add to `CameraConnection.kt`. First, a new enum next to `ConnectionState`:
```kotlin
/** Whether a capture/record is in progress. */
enum class CaptureState { IDLE, BUSY, RECORDING }
```
Add imports at the top:
```kotlin
import com.arashivision.sdkcamera.camera.callback.ICaptureStatusListener
import com.arashivision.sdkcamera.camera.model.CaptureMode
```
Inside the class, add fields (next to `_state`):
```kotlin
    private val _captureState = MutableStateFlow(CaptureState.IDLE)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _lastCapturedPaths = MutableStateFlow<List<String>>(emptyList())
    val lastCapturedPaths: StateFlow<List<String>> = _lastCapturedPaths.asStateFlow()

    private var recording = false

    private val captureListener = object : ICaptureStatusListener {
        override fun onCaptureStarting() { _captureState.value = CaptureState.BUSY }
        override fun onCaptureWorking() {
            _captureState.value = if (recording) CaptureState.RECORDING else CaptureState.BUSY
        }
        override fun onCaptureStopping() { _captureState.value = CaptureState.BUSY }
        override fun onCaptureFinish(paths: Array<String>?) {
            recording = false
            _lastCapturedPaths.value = paths?.toList() ?: emptyList()
            _captureState.value = CaptureState.IDLE
        }
        override fun onCaptureError(code: Int) {
            recording = false
            _captureState.value = CaptureState.IDLE
        }
    }
```
In `register()`, after the existing listener wiring, add:
```kotlin
        manager.setCaptureStatusListener(captureListener)
```
In `disconnect()`, before `manager.closeCamera()`, add:
```kotlin
        manager.setCaptureStatusListener(null)
```
Add the action methods (next to `startPreview`):
```kotlin
    /** Whether the camera has a usable SD card (recording requires it). */
    fun isSdCardEnabled(): Boolean = manager.isSdCardEnabled

    /** Take a single photo (normal mode). */
    fun capturePhoto() {
        recording = false
        manager.startNormalCapture()
    }

    /** Start a normal video recording. Caller must check [isSdCardEnabled] first. */
    fun startRecord() {
        recording = true
        manager.startNormalRecord()
    }

    /** Stop the current recording. */
    fun stopRecord() {
        manager.stopNormalRecord()
    }
```

- [ ] **Step 2: Build**

Run: `./gradlew :android:compileDebugKotlin 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL. (If `setCaptureStatusListener(null)` is rejected as non-null, pass a
no-op listener field instead; inspect via javap and adapt — STOP and report if unclear.)

- [ ] **Step 3: Commit**

```bash
git add android/src/main/kotlin/com/panorama/android/camera/CameraConnection.kt
git commit -m "feat(android): capture state machine + photo/record actions in CameraConnection"
```

---

### Task 2: Domain setting types + generic settings dispatch

**Files:**
- Create: `android/src/main/kotlin/com/panorama/android/camera/CameraSetting.kt`
- Modify: `android/src/main/kotlin/com/panorama/android/camera/CameraConnection.kt`

- [ ] **Step 1: Create the domain setting model**

`android/src/main/kotlin/com/panorama/android/camera/CameraSetting.kt`:
```kotlin
package com.panorama.android.camera

/** The basic camera settings this app exposes (a curated subset of the SDK's CaptureSetting). */
enum class CameraSetting { ISO, SHUTTER, EV, WB, RECORD_RESOLUTION, PHOTO_RESOLUTION }

/** One selectable value of a [CameraSetting]: [label] for display, [token] is the SDK enum's name
 *  used to apply it back (decouples the UI from the SDK enum types). */
data class SettingOption(val label: String, val token: String)

/** A setting's current value + its available options, as read from the camera. */
data class SettingState(
    val setting: CameraSetting,
    val current: SettingOption?,
    val options: List<SettingOption>,
)
```

- [ ] **Step 2: Add the generic dispatch to CameraConnection**

Add imports:
```kotlin
import com.arashivision.sdkcamera.camera.model.CaptureSetting
import com.arashivision.sdkcamera.camera.model.ISO
import com.arashivision.sdkcamera.camera.model.Shutter
import com.arashivision.sdkcamera.camera.model.EV
import com.arashivision.sdkcamera.camera.model.WB
import com.arashivision.sdkcamera.camera.model.RecordResolution
import com.arashivision.sdkcamera.camera.model.PhotoResolution
```
Add a helper that maps `CameraSetting` + mode to the read/options/apply calls. The model types are
Java enums, so `(value as Enum<*>).name` gives the token and `enumValueOf` re-resolves it:
```kotlin
    /** Read a setting's current value + options for the given mode (RECORD_RESOLUTION uses the video
     *  mode, PHOTO_RESOLUTION the photo mode; the rest read against the active [mode]). */
    fun readSetting(setting: CameraSetting, photo: Boolean): SettingState {
        val mode = if (photo) CaptureMode.CAPTURE_NORMAL else CaptureMode.RECORD_NORMAL
        fun opt(e: Enum<*>?) = e?.let { SettingOption(it.name, it.name) }
        fun opts(list: List<Enum<*>>) = list.map { SettingOption(it.name, it.name) }
        return when (setting) {
            CameraSetting.ISO ->
                SettingState(setting, opt(manager.getISO(mode)), opts(manager.getSupportISOList(mode)))
            CameraSetting.SHUTTER ->
                SettingState(setting, opt(manager.getShutter(mode)), opts(manager.getSupportShutterList(mode)))
            CameraSetting.EV ->
                SettingState(setting, opt(manager.getEv(mode)), opts(manager.getSupportEVList(mode)))
            CameraSetting.WB ->
                SettingState(setting, opt(manager.getWB(mode)), opts(manager.getSupportWBList(mode)))
            CameraSetting.RECORD_RESOLUTION ->
                SettingState(setting, opt(manager.getRecordResolution(CaptureMode.RECORD_NORMAL)),
                    opts(manager.getSupportRecordResolutionList(CaptureMode.RECORD_NORMAL)))
            CameraSetting.PHOTO_RESOLUTION ->
                SettingState(setting, opt(manager.getPhotoResolution(CaptureMode.CAPTURE_NORMAL)),
                    opts(manager.getSupportPhotoResolutionList(CaptureMode.CAPTURE_NORMAL)))
        }
    }

    /** Apply a setting value (by its token = SDK enum name) for the active mode. STREAMING only. */
    fun applySetting(setting: CameraSetting, token: String, photo: Boolean) {
        val mode = if (photo) CaptureMode.CAPTURE_NORMAL else CaptureMode.RECORD_NORMAL
        when (setting) {
            CameraSetting.ISO -> manager.setISO(mode, enumValueOf<ISO>(token))
            CameraSetting.SHUTTER -> manager.setShutter(mode, enumValueOf<Shutter>(token))
            CameraSetting.EV -> manager.setEv(mode, enumValueOf<EV>(token))
            CameraSetting.WB -> manager.setWB(mode, enumValueOf<WB>(token))
            CameraSetting.RECORD_RESOLUTION ->
                manager.setRecordResolution(CaptureMode.RECORD_NORMAL, enumValueOf<RecordResolution>(token))
            CameraSetting.PHOTO_RESOLUTION ->
                manager.setPhotoResolution(CaptureMode.CAPTURE_NORMAL, enumValueOf<PhotoResolution>(token))
        }
    }
```

- [ ] **Step 3: Build**

Run: `./gradlew :android:compileDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL. If `manager.getISO(mode)` etc. infer `Enum<*>` poorly (Kotlin may need an
explicit cast `as Enum<*>`), add the cast in the `opt`/`opts` calls. If `setRecordResolution` does not
exist (only the getter), drop RECORD_RESOLUTION from the apply branch and report it. STOP and report
any genuinely missing method.

- [ ] **Step 4: Commit**

```bash
git add android/src/main/kotlin/com/panorama/android/camera/CameraSetting.kt android/src/main/kotlin/com/panorama/android/camera/CameraConnection.kt
git commit -m "feat(android): generic basic-settings read/apply dispatch in CameraConnection"
```

---

### Task 3: LiveViewModel — capture + settings (TDD)

**Files:**
- Modify: `app/src/main/kotlin/com/panorama/app/live/LiveUiState.kt`
- Modify: `app/src/main/kotlin/com/panorama/app/live/LiveViewModel.kt`
- Modify: `app/src/test/kotlin/com/panorama/app/live/LiveViewModelTest.kt`

- [ ] **Step 1: Extend LiveUiState**

In `LiveUiState.kt`:
```kotlin
import com.panorama.android.camera.CaptureState

data class LiveUiState(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val capture: CaptureState = CaptureState.IDLE,
    val photoMode: Boolean = true,
    val sdMissing: Boolean = false,
)
```
(keep the existing `ConnectionState` import.)

- [ ] **Step 2: Write the failing tests**

Add to `LiveViewModelTest.kt` (the file already mocks `CameraConnection` + `OrientationEngine`):
```kotlin
    @Test
    fun `onShutter takes a photo in photo mode`() = runTest {
        val flow = MutableStateFlow(ConnectionState.STREAMING)
        val conn = fakeConnection(flow)
        every { conn.captureState } returns MutableStateFlow(com.panorama.android.camera.CaptureState.IDLE)
        val vm = LiveViewModel(conn, engine, backgroundScope = backgroundScope)
        vm.onShutter()
        verify { conn.capturePhoto() }
    }

    @Test
    fun `onShutter without sd card in video mode sets sdMissing, no record`() = runTest {
        val flow = MutableStateFlow(ConnectionState.STREAMING)
        val conn = fakeConnection(flow)
        every { conn.captureState } returns MutableStateFlow(com.panorama.android.camera.CaptureState.IDLE)
        every { conn.isSdCardEnabled() } returns false
        val vm = LiveViewModel(conn, engine, backgroundScope = backgroundScope)
        vm.setPhotoMode(false)
        vm.onShutter()
        verify(exactly = 0) { conn.startRecord() }
        assertTrue(vm.state.value.sdMissing)
    }
```
Add `import org.junit.Assert.assertTrue` and (if missing) `import io.mockk.every`. Update
`fakeConnection` so the new `captureState` flow is stubbed there once instead of per-test if cleaner.

- [ ] **Step 3: Run, verify FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests '*LiveViewModelTest*' 2>&1 | tail -20`
Expected: FAIL (no `onShutter`/`setPhotoMode`).

- [ ] **Step 4: Implement in LiveViewModel**

Add to `LiveViewModel.kt`:
```kotlin
    init {
        // (existing connection.state mirror stays) — also mirror capture state:
        scope.launch {
            connection.captureState.collect { c -> _state.update { it.copy(capture = c) } }
        }
    }
```
NOTE: there is already an `init { ... scope.launch { connection.state.collect ... } }`. Put the new
`captureState` collector in the SAME init block (after the existing collector), using the same
`scope` val. Then add actions:
```kotlin
    fun setPhotoMode(photo: Boolean) = _state.update { it.copy(photoMode = photo, sdMissing = false) }

    fun onShutter() {
        if (_state.value.photoMode) {
            connection.capturePhoto()
            return
        }
        // video
        when (_state.value.capture) {
            com.panorama.android.camera.CaptureState.RECORDING -> connection.stopRecord()
            else -> {
                if (!connection.isSdCardEnabled()) {
                    _state.update { it.copy(sdMissing = true) }
                } else {
                    connection.startRecord()
                }
            }
        }
    }

    fun settingsFor(): List<com.panorama.android.camera.SettingState> =
        com.panorama.android.camera.CameraSetting.entries.map {
            connection.readSetting(it, _state.value.photoMode)
        }

    fun applySetting(setting: com.panorama.android.camera.CameraSetting, token: String) =
        connection.applySetting(setting, token, _state.value.photoMode)
```

- [ ] **Step 5: Run, verify PASS**

Run: `./gradlew :app:testDebugUnitTest --tests '*LiveViewModelTest*' 2>&1 | tail -20`
Expected: PASS (existing 2 + new 2).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/panorama/app/live/LiveUiState.kt app/src/main/kotlin/com/panorama/app/live/LiveViewModel.kt app/src/test/kotlin/com/panorama/app/live/LiveViewModelTest.kt
git commit -m "feat(app): capture + settings state/actions in LiveViewModel (tested)"
```

---

### Task 4: LiveScreen — shutter, mode toggle, settings panel

**Files:**
- Modify: `app/src/main/kotlin/com/panorama/app/live/LiveScreen.kt`

- [ ] **Step 1: Add capture controls (visible only when STREAMING)**

In `LiveScreen.kt`, inside the `Box`, after the existing content, add controls gated on
`state.connection == ConnectionState.STREAMING`. Add imports as needed (`Row`, `Column`,
`FilledTonalButton`, `Button`, `Text`, `Alignment`, `Modifier.align`, `padding`, `dp`).
```kotlin
        if (state.connection == ConnectionState.STREAMING) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { viewModel.setPhotoMode(true) }) {
                        Text(if (state.photoMode) "[Photo]" else "Photo")
                    }
                    FilledTonalButton(onClick = { viewModel.setPhotoMode(false) }) {
                        Text(if (!state.photoMode) "[Video]" else "Video")
                    }
                }
                Button(onClick = { viewModel.onShutter() }) {
                    Text(
                        when {
                            state.photoMode -> "Shoot"
                            state.capture == com.panorama.android.camera.CaptureState.RECORDING -> "Stop ●"
                            else -> "Record"
                        },
                    )
                }
                if (state.sdMissing) Text("Insert an SD card to record")
            }
        }
```

- [ ] **Step 2: Add the settings panel**

Add a "Camera settings" button (top-end, like the player's settings) and a translucent overlay panel
listing the 6 settings, each a row of option chips. Add the imports (`background`, `clickable`,
`Color`, `fillMaxSize`). Use a local `var showSettings by remember { mutableStateOf(false) }`:
```kotlin
        if (state.connection == ConnectionState.STREAMING) {
            FilledTonalButton(
                onClick = { showSettings = true },
                modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(8.dp),
            ) { Text("Settings") }
        }
        if (showSettings) {
            val settings = remember(state.photoMode) { viewModel.settingsFor() }
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { showSettings = false },
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center).systemBarsPadding().padding(24.dp)
                        .background(Color.Black.copy(alpha = 0.55f)).padding(16.dp)
                        .clickable(enabled = false) {},
                ) {
                    settings.forEach { s ->
                        Text("${s.setting.name}: ${s.current?.label ?: "-"}", color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            s.options.take(8).forEach { opt ->
                                FilledTonalButton(onClick = { viewModel.applySetting(s.setting, opt.token) }) {
                                    Text(opt.label)
                                }
                            }
                        }
                    }
                    Button(onClick = { showSettings = false }) { Text("Close") }
                }
            }
        }
```
(`take(8)` caps very long option lists for the first cut; note this is a deliberate cap, not all
options — fine for the basic settings here.)

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/panorama/app/live/LiveScreen.kt
git commit -m "feat(app): shutter, photo/video toggle, and settings panel on the live screen"
```

---

### Task 5: On-device verification (requires the camera)

**Files:** none (manual).

- [ ] **Step 1: Install + open Live, connect a camera, reach STREAMING.**

```
./gradlew :app:installDebug
adb shell am start -n com.panorama.app/.MainActivity
```
Library → Live camera → connect → wait for streaming.

- [ ] **Step 2: Photo.** In Photo mode tap Shoot; confirm a photo is taken (capture state cycles
  BUSY→IDLE; if a path toast/log is added, a file path appears).

- [ ] **Step 3: Video.** Switch to Video; tap Record → "Stop ●" appears; tap Stop → recording ends.
  With no SD card, confirm "Insert an SD card to record" shows and no recording starts.

- [ ] **Step 4: Settings.** Open Settings; change ISO / Shutter / EV / WB / Resolution; confirm each
  apply does not error and the displayed current value updates after reopening the panel.

- [ ] **Step 5: Teardown.** Leave the screen; confirm clean disconnect, no crash.

- [ ] **Step 6: Commit any fixes** (e.g. a setting whose SDK call needs an IDependChecker, or the
  photo H264/H265 preview-reopen quirk if it appears):

```bash
git add -A && git commit -m "fix(app): live capture/settings on-device adjustments"
```

---

## Notes / risks

- **Camera-only verification:** Tasks 1-4 build green without a camera; Task 5 needs hardware.
- **`setRecordResolution`/IDependChecker:** some setters have an `IDependChecker` overload the old app
  used; the simple 2-arg overloads exist and are used here. If the camera rejects a value needing a
  dependency check, add the checker on-device (Task 5).
- **Option-list cap:** the settings panel shows up to 8 options per setting for the first cut; widen
  or make scrollable later if a camera exposes more (e.g. many shutter speeds).
- **Photo preview quirk:** the old flow reopened the preview after a photo (H264/H265 re-encode).
  Watch for a frozen preview after a photo on-device; if it happens, call `closePreviewStream()` +
  `startPreviewStream(...)` again (Task 5 fix).
- **`:android:testDebugUnitTest`** is pre-existing-broken (Cardboard AAR AAPT theme); LiveViewModel
  tests live in `:app` — run `:app:testDebugUnitTest`.
