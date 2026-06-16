# Camera Capture + Basic Settings — Design

**Goal:** On the existing live screen, let the user shoot a photo and record a video on the connected
Insta360 camera, and adjust the basic camera settings (ISO, shutter, EV, white balance, photo/record
resolution).

**Success criterion (camera-only):** On a real camera — switch photo/video mode, tap shutter →
photo file path reported (`onCaptureFinish`); start/stop a recording; change ISO and see it applied.

## Scope (YAGNI)

- **Capture modes:** photo `CAPTURE_NORMAL` + video `RECORD_NORMAL` only. The other ~23 SDK modes
  (timelapse, bullettime, slow-motion, interval, starlapse, HDR variants, …) are OUT of scope.
- **Settings:** ISO, SHUTTER, EV, WB, RECORD_RESOLUTION, PHOTO_RESOLUTION only. The other ~17
  CaptureSetting types are OUT of scope.
- Lives on the existing `LiveScreen` (no new screen). RTMP live-push, camera gallery (Shot), and log
  export are OUT of scope.

## Reuse from the deleted demo (in git at `5a95ef0~1`)

The old `ext/InstaCameraManagerExt.kt` is the pure-manager dispatch layer — directly reusable (no
UI). The relevant calls:
- Photo: `InstaCameraManager.getInstance().startNormalCapture()`.
- Video: `startNormalRecord()` / `stopNormalRecord()` (record requires `isSdCardEnabled`).
- Capture status: `setCaptureStatusListener(ICaptureStatusListener)` with
  `onCaptureStarting/onCaptureWorking/onCaptureStopping/onCaptureFinish(paths: Array<String>?)/`
  `onCaptureError(i)/onCaptureTimeChanged(t)`.
- Settings: `getSupportCaptureSettingList(mode): List<CaptureSetting>`, plus per-setting get/set
  (`getISO/setISO`, `getShutter/setShutter`, `getEv/setEv`, `setWB`/WB support list,
  `getRecordResolution`, photo resolution) and support lists (`getSupportExposureList` etc.). Model
  types in `com.arashivision.sdkcamera.camera.model` (ISO, Shutter, EV, WB, RecordResolution,
  PhotoResolution) carry `.nativeValue` for display/sort. Sentinels: `ISO.ISO_AUTO`,
  `Shutter.SHUTTER_AUTO`.
- Settings can only be written while the preview stream is OPEN (our `ConnectionState.STREAMING`).
The reflection-based `setYaw/setPitch` from the old CaptureActivity is NOT carried over (panorama-v2
uses `setYawPitchRoll`). The XML/Fragment/ViewBinding UI is rebuilt in Compose.

## Components (extend existing, no new screen)

1. **`CameraConnection` (extend)** — add:
   - `enum class CaptureState { IDLE, BUSY, RECORDING }` and a `captureState: StateFlow<CaptureState>`,
     plus a `lastCapturedPaths: StateFlow<List<String>>` (from `onCaptureFinish`).
   - `capturePhoto()` → `startNormalCapture()`; `startRecord()` → `startNormalRecord()`;
     `stopRecord()` → `stopNormalRecord()`. Register an `ICaptureStatusListener` in `register()`,
     clear it in `disconnect()`, mapping callbacks → `captureState` / `lastCapturedPaths`.
   - `isSdCardEnabled(): Boolean` passthrough (gate recording).
   - Settings: `supportedSettings(mode): List<CaptureSetting>`, `getSetting(mode, setting): Any?`,
     `setSetting(mode, setting, value)`. Implement by lifting the 6-setting get/set/support dispatch
     from the old `InstaCameraManagerExt.kt`. Keep it pure-manager (no Android UI types).

2. **`CaptureSettingsModel` (`:core` or `:android`)** — a small mapping helper: setting → display
   name, and value → display string (port the 6 relevant branches of the old
   `CaptureConst.getCaptureSettingValueText`/name maps, using `.nativeValue`). Keeps UI string logic
   testable and out of the Composable. (If it needs no Android types, put it in `:core`; otherwise
   `:android`.)

3. **`LiveViewModel` (extend)** — expose `captureState`, `lastCapturedPaths`, a `photoMode: Boolean`
   (true = photo, false = video) toggle, the current settings (per type: value + options); actions
   `setPhotoMode(Boolean)`, `onShutter()` (photo → capturePhoto; video → toggle record),
   `applySetting(setting, value)`. Reads the supported-settings list for the active mode when
   STREAMING.

4. **`LiveScreen` (extend)** — when `STREAMING`, overlay:
   - a photo/video mode toggle + a shutter button (records show elapsed/REC indicator);
   - a "camera settings" button opening a panel (same translucent-overlay pattern as the VR settings)
     listing ISO / Shutter / EV / WB / Resolution, each a picker over its options;
   - an SD-card-missing message when recording is attempted without a card.

## Data flow

LiveScreen shutter → `LiveViewModel.onShutter()` → `CameraConnection.capturePhoto()/startRecord()/
stopRecord()` → `InstaCameraManager` → `ICaptureStatusListener` → `captureState`/`lastCapturedPaths`
→ UI. Settings: panel picker → `LiveViewModel.applySetting` → `CameraConnection.setSetting` →
`InstaCameraManager`. Supported options read once per mode/STREAMING via `supportedSettings` + per-type
support lists.

## Error handling

- Record without SD card → `captureState` stays IDLE, UI shows "Insert an SD card to record".
- `onCaptureError(i)` → reset `captureState` to IDLE, show a short message.
- Settings writes only attempted while STREAMING; the settings button is disabled otherwise.
- Photo H264/H265 re-encode quirk (old flow reopened the preview after a photo): watch for a broken
  preview after a photo on-device; if it occurs, reopen the preview stream. Verify on hardware.

## Testing

- `LiveViewModel` capture state machine (photo/video toggle, shutter dispatch, record start/stop,
  SD-disabled path): unit-testable with a faked `CameraConnection` (MockK), like the existing
  `LiveViewModelTest`.
- `CaptureSettingsModel` value/name formatting: pure unit tests.
- Real capture + settings apply: manual on the camera (evening).

## Phasing (single plan, staged)

1. `CameraConnection`: capture actions + `ICaptureStatusListener` + `CaptureState`/paths + SD gate.
2. `CameraConnection`: the 6-setting get/set/support dispatch (lifted from old ext) + `CaptureSettingsModel`.
3. `LiveViewModel`: capture + settings state/actions (+ unit tests).
4. `LiveScreen`: shutter + mode toggle + settings panel UI.
5. On-device verification.

## Out of scope (explicitly)

- Modes other than normal photo/video; settings other than ISO/Shutter/EV/WB/resolution.
- RTMP live-push, camera gallery (Shot screen), camera log export.
- The reflection yaw/pitch path; XML/Fragment UI.
