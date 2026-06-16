# Live Camera Preview — Design

**Goal:** From the library screen, a "Live" button opens a new screen where the user connects to an
Insta360 camera (Wi-Fi / USB / Bluetooth) and watches the live panoramic preview, steering the view
angle with the phone's gyroscope.

**Success criterion:** On a real Insta360 camera — connect → a live panoramic frame is visible →
turning the phone pans the view. (Device-only test; no camera in the dev environment, so the user
verifies on hardware.)

## Key constraint (from SDK reconnaissance)

The Insta360 SDK renders the preview into its own `InstaCapturePlayerView` (a `FrameLayout` with an
internal renderer). It exposes **no** API to push the stream into an external `Surface`/OES texture,
so the live preview **cannot** flow through the app's Cardboard/OES render pipeline. Therefore:

- Live preview is a **separate, self-contained screen** built on `InstaCapturePlayerView`, isolated
  from the file player (ExoPlayer) and the Cardboard renderer.
- View-angle control uses the SDK player's **native** `setYawPitchRoll(...)` (no reflection — unlike
  the old project, this API is public on `InstaCapturePlayerView`).
- VR split-screen and the off-screen detection arrow are **out of scope** for this feature (the
  arrow/detection pipeline targets file playback; VR over live would need the old PixelCopy
  `VrManager`, deferred).

## Dependencies

The Insta360 SDK is already present in the gradle cache (resolves offline):
`com.arashivision.sdkmedia:sdkmedia:1.8.1`, `...basecamera:1.8.1`, `...basemedia:1.8.1`.

- Add these to `:app` (the module that owns Activities/Compose), via the version catalog
  (`gradle/libs.versions.toml`) mirroring the names the old top-level project used (`insta.camera`,
  `insta.media`). Confirm exact coordinates from the cache during implementation.
- The SDK transitively needs `androidx.appcompat` — add it.
- SDK init: call `InstaCameraSDK.init(this)` in `PanoramaApp.onCreate()` (the old project did this in
  its Application). Confirm the exact init entry point from the SDK during implementation.

## Components (each one responsibility, testable boundary)

1. **`CameraConnection` (`:android`, new)** — thin wrapper over `InstaCameraManager`. Surface:
   - `connectWifi()`, `connectUsb()`, `connectBle()` (mirror the manager's connect types),
   - `disconnect()`,
   - `startPreview()` / `stopPreview()` (open/close the live preview stream),
   - a state callback/flow: `Disconnected → Connecting → Connected → Streaming` (+ `Error(msg)`).
   Isolates all SDK camera types from the rest of the app. JVM-untestable parts (real SDK calls)
   stay here; the state machine is kept simple enough to reason about.

2. **`LiveViewModel` (`:app`, new, Hilt)** — holds `LiveUiState(connection: ConnectionState,
   isStreaming: Boolean, error: String?)`; actions `connectWifi/Usb/Ble`, `disconnect`. Pumps
   `CameraConnection` state into the UI flow. Feeds the gyro angle to the player (see 4).

3. **`LiveScreen` (`:app`, new, Compose)** — `AndroidView { InstaCapturePlayerView }` full-bleed;
   overlay: connect buttons (Wi-Fi / USB / BLE) shown until connected, a status line, a back button
   (top-left, like the player). On `Connected`, calls `prepare(CaptureParamsBuilderV2 with
   LIVE_TYPE_PANORAMA).play()` on the view. Forwards lifecycle (`setLifecycle`, pause/destroy).

4. **Gyro → view angle** — reuse the existing `OrientationEngine` (already produces yaw/pitch via
   `gazeRef`). A small effect reads `gazeRef` on the frame clock and calls
   `capturePlayerView.setYawPitchRoll(yaw, pitch, 0f)`. Start/stop the engine with the screen
   lifecycle. (Same engine the file player uses; only one consumer is active at a time since Live is
   a separate screen.)

5. **Navigation** — add `Screen.Live` to `MainActivity`'s `when`, and a "Live" button to
   `LibraryScreen` (next to "Open 360 video"). Back returns to `Library`.

## Data flow

`LibraryScreen` "Live" → `Screen.Live` → `LiveScreen`.
User taps a connect button → `LiveViewModel.connectX()` → `CameraConnection.connectX()` →
`InstaCameraManager` connects → state `Connected` → `LiveScreen` calls `prepare(...).play()` on
`InstaCapturePlayerView` → live frame renders. `OrientationEngine.gazeRef` → `setYawPitchRoll`.

## Error handling

- Connection callbacks surface `Error(msg)` into `LiveUiState.error`; the screen shows it and keeps
  the connect buttons available to retry.
- Permissions: Wi-Fi scan needs location + nearby-wifi; USB needs the device-attach intent/host
  feature; BLE needs bluetooth + scan/connect. Request at runtime; degrade gracefully (a connect
  type whose permission is denied shows an explanatory toast, doesn't crash).
- Leaving the screen disconnects/stops preview and releases the player in `onDispose`.

## Testing

- `CameraConnection` real SDK calls: device-only (no JVM harness for the SDK).
- `LiveViewModel` state transitions: unit-testable with a faked `CameraConnection` (MockK), like
  `PlayerViewModelTest` fakes its collaborators.
- End-to-end (connect → live frame → gyro pan): manual on the real camera by the user.

## Phasing (single plan, staged tasks)

1. SDK dependency + `InstaCameraSDK.init` + `CameraConnection` wrapper + `LiveViewModel` (+ unit
   tests for the state machine).
2. `LiveScreen` + navigation + connect buttons + basic live preview (`prepare/play`).
3. Gyro → `setYawPitchRoll`; lifecycle; permissions polish.

## Out of scope (explicitly)

- VR split-screen over live preview (would need the old PixelCopy VrManager).
- The off-screen detection arrow over live (detection pipeline is file-oriented).
- Recording / capture / camera settings UI (this is preview-only).
