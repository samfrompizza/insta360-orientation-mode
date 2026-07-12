# DEBUG.md — Bug Analysis & Fix Plans

## Bug 1: VR Mode Calibration Reset (180° Flip)

### Symptoms
When switching from mono-online mode to VR-online mode, the gaze calibration is lost. The view appears to flip 180 degrees.

### Root Cause Hypothesis
`UnifiedVrManager.enableVrMode()` triggers `CapturePlayerVrSource.createSecondPlayer()`, which creates a new `InstaCapturePlayerView` for the right eye. During this process, one of the following happens:

1. **Calibration quaternion reset:** `GyroOrientationController.calibrate()` is called somewhere in the VR init flow, overwriting the existing calibration with the current orientation (which may already be offset). The `SensorFusionEngine.calibrate()` stores `q_calibration = q_current`, then all subsequent readings use `q_relative = q_current * conjugate(q_calibration)`. If `calibrate()` is called when the view is already offset, the new calibration embeds the offset.

2. **Dual player orientation mismatch:** `CapturePlayerVrSource.applyOrientation()` applies `yawDeg + VR_IPD_YAW_DEG` (3°) to the second player while `CaptureActivity.tryApplyOrientationToPlayer()` applies raw yaw to the main player. When switching modes, the old player is hidden (INVISIBLE) and the new player gets a fresh orientation state — but the gyro is still tracking from the old calibration, so the view jumps.

3. **Coordinate system remap inconsistency:** `GyroOrientationController.updateOrientation()` remaps the rotation matrix per display rotation using `SensorManager.remapCoordinateSystem()`. In VR mode, the second player view might interpret yaw/pitch differently, leading to a 180° flip if the Z-axis convention changes.

### Files to Investigate
- `core/vr/src/main/java/.../core/vr/UnifiedVrManager.kt` — `enableVrMode()`, `disableVrMode()`, `applyOrientation()`
- `app/src/main/java/.../ui/capture/CapturePlayerVrSource.kt` — `createSecondPlayer()`, `applyOrientation()`, `onVrEnabled()`, `onVrDisabled()`
- `data/sensor/src/main/java/.../data/sensor/GyroOrientationController.kt` — `calibrate()`, `updateOrientation()`, `remapCoordinateSystem` logic
- `core/sensor-fusion/src/main/java/.../core/sensorfusion/SensorFusionEngine.kt` — SLERP smoothing, Euler extraction, `GazeState`

### Fix Candidates
1. **Preserve calibration across VR toggle:** Do NOT recalibrate gyro when entering/exiting VR. Only calibrate on first frame render and on manual button press.
2. **Verify IPD offset sign:** Check if `VR_IPD_YAW_DEG = 3.0f` is being added or subtracted in the wrong direction for the second player.
3. **Ensure consistent Euler extraction:** The `SensorFusionEngine.toEulerAngles()` should use the same convention for both mono and stereo modes. Verify no sign flip in `invertYaw`/`invertPitch` flags.
4. **Check conjugate application:** `q_relative = q_current * conjugate(q_calibration)` — verify this is applied identically before both player views are updated.

### Verification Steps
1. Log `GazeState(yawDeg, pitchDeg, rollDeg)` before and after VR toggle
2. Log `q_calibration`, `q_current`, `q_relative` in `SensorFusionEngine.update()`
3. Log `SensorFusionEngine.remapCoordinateSystem` output matrix
4. Manually verify with known orientation: point camera North (yaw=0), toggle VR, verify gaze is still North

---

## Bug 2: Detection Arrow Incorrect in Landscape + Coordinate System Mismatch

### Symptoms
The directional arrow (pointing to detected objects from JSON sidecar) works correctly ONLY when:
- Gyro is calibrated in a specific orientation
- Phone is in VERTICAL (portrait) orientation

When phone is rotated to HORIZONTAL (landscape), the arrow points incorrectly, suggesting a pitch/roll/yaw axis confusion in the coordinate system pipeline.

### Root Cause Hypothesis
Two independent issues likely compound:

1. **Display rotation not propagated to arrow math:**
   `GyroOrientationController.updateOrientation()` remaps the rotation matrix per `displayRotation`:
   - ROTATION_0 (portrait): `remapCoordinateSystem(mat, AXIS_X, AXIS_Z)` — pitch from values[1]
   - ROTATION_90 (landscape): `remapCoordinateSystem(mat, AXIS_Z, AXIS_MINUS_X)` — pitch from values[2]
   
   The `GazeState` produced by `SensorFusionEngine` already accounts for display rotation in its Euler angles. However, `DirectionArrowOverlayView` or `PanoramaFovMath.resolveTargetQuat()` may assume a fixed coordinate system that doesn't account for the current display rotation, causing the arrow angle to be off by 90°.

2. **JSON coordinate system ≠ 360° sphere coordinate system:**
   The Python pipeline (`offline-360-cv`) generates bounding boxes in a normalized image coordinate system. `EquirectangularProjection.fromNormalized(x, y)` maps [0,1]×[0,1] image coords to yaw/pitch. But the convention may be:
   - **JSON expects:** x=0 at left edge, y=0 at top (standard image convention)
   - **EquirectangularProjection expects:** x=0 at -180° yaw (left), y=0 at +90° pitch (top)
   
   If the sphere rendering (SphericalGLSurfaceView from Media3) uses a different origin, the yaw/pitch from `fromNormalized()` will be offset. For example, if Media3's spherical view places yaw=0 at the center of the equirectangular texture, but `fromNormalized()` places yaw=0 at the left edge, there's a 180° offset.

3. **Quaternion vs Euler angle mismatch in `resolveTargetQuat()`:**
   `PanoramaFovMath.resolveTargetQuat()`:
   ```kotlin
   // Rotate target's world-space vector by gaze.orientation.conjugate()
   // to get the target direction in the viewer's local frame
   ```
   This converts the target direction to local space using the gaze quaternion. But the target direction comes from `EquirectangularProjection.fromNormalized()` which produces `PanoramaDirection` containing a `UnitVector3` and `UnitQuaternion`. If these two quaternion conventions differ (e.g., one uses Z-up and the other uses Y-up), the local-space check will be wrong.

### Files to Investigate
- `core/math/src/main/java/.../core/math/panorama/PanoramaFovMath.kt` — `resolveTargetQuat()`: how target vector is rotated by gaze conjugate
- `core/math/src/main/java/.../core/math/panorama/EquirectangularProjection.kt` — `fromNormalized()`: [0,1]→yaw/pitch mapping, `fromYawPitch()`: yaw/pitch→quaternion
- `core/sensor-fusion/src/main/java/.../core/sensorfusion/SensorFusionEngine.kt` — `toEulerAngles()`: ZYX extraction order, yaw/pitch/roll convention
- `data/sensor/src/main/java/.../data/sensor/GyroOrientationController.kt` — `remapCoordinateSystem` per display rotation
- `feature/player/src/main/java/.../ui/player/overlay/DirectionArrowOverlayView.kt` — arrow angle calculation
- `app/src/main/java/.../ui/player/LocalSphericalPlayerActivity.kt` — `updateCurrentDetections()`: how it gets current position, resolves targets

### Diagnostic Approach
1. **Log the entire pipeline for one detected object:**
   - JSON centerNorm (x, y) → `fromNormalized()` → yaw/pitch → `PanoramaDirection`
   - GazeState (yaw, pitch from gyro) → gaze quaternion
   - `resolveTargetQuat()` → local frame direction → screen-space arrow angle
   - Do this in portrait AND landscape, compare

2. **Test with known targets:**
   - Insert a synthetic detection at center of equirectangular image (x=0.5, y=0.5)
   - Verify `fromNormalized(0.5, 0.5)` gives yaw=0°, pitch=0°
   - With gaze at yaw=0°, pitch=0°, verify arrow is not visible (target in FOV)
   - With gaze rotated 90° right, verify arrow points left

3. **Check Media3 spherical view conventions:**
   - Verify `SphericalGLSurfaceView` yaw/pitch match the `EquirectangularProjection` conventions
   - Check if Media3's internal sensor rotation (disabled) uses different axes

### Fix Candidates
1. **Pass display rotation to detection math:** If `GazeState` already accounts for display rotation, ensure `DirectionArrowOverlayView` receives the FULL rotation-compensated yaw/pitch. If not, compensate arrow angle by `displayRotation * 90°`.
2. **Verify equirectangular coordinate origin:** Ensure `fromNormalized()` maps x=0.5 to yaw=0°. If not, add an offset.
3. **Unify quaternion conventions:** If `EquirectangularProjection` uses one quaternion convention (e.g., ZYX Euler order) and `SensorFusionEngine` uses another, add a conversion step.

---

## Bug 3: VR Exit Freeze (Hang on DisableVr)

### Symptoms
When pressing the button to exit VR mode, the application freezes entirely. Changing the device orientation (triggering Activity recreation) resolves the freeze.

### Root Cause Hypothesis
`UnifiedVrManager.disableVrMode()` triggers `CapturePlayerVrSource.onVrDisabled()`, which:
1. Sets `mainPlayerView.visibility = View.VISIBLE`
2. Calls `mainPlayerView.pipeline` (may throw/block)
3. Calls `mainPlayerView.prepare(params)` and `mainPlayerView.play()`
4. Calls `instaCameraManager.setPipeline(mainPlayerView.pipeline)`
5. Calls `secondPlayerView?.destroy()`

**Deadlock/Lockout scenario:**
The PixelCopy loop (running at 33ms intervals → ~30fps) reads pixels from `secondPlayerView` into a Bitmap for the left-eye `ImageView`. When `disableVrMode()` is called on the main thread:
- The PixelCopy callback may still be waiting for the GPU to complete a copy from the surface
- `secondPlayerView.destroy()` destroys the surface while PixelCopy is active → GPU pipeline corruption
- The main thread blocks waiting for the GPU, which is waiting for PixelCopy, which is waiting for the destroyed surface → deadlock

When orientation changes, the Activity is destroyed and recreated, which cancels all pending operations and resets the surface, breaking the deadlock.

### Files to Investigate
- `core/vr/src/main/java/.../core/vr/UnifiedVrManager.kt` — `disableVrMode()`, PixelCopy loop management
- `app/src/main/java/.../ui/capture/CapturePlayerVrSource.kt` — `onVrDisabled()`
- `feature/player/src/main/java/.../ui/player/ExoPlayerVrSource.kt` — `onVrDisabled()` (player-specific)

### Fix Candidates
1. **Stop PixelCopy before destroying surfaces:** Before calling `onVrDisabled()` or `destroy()`, explicitly cancel/stop the PixelCopy loop (set a flag, remove callbacks, wait for current copy to complete with timeout).
2. **Run surface operations off main thread:** Move `prepare()`, `play()`, `destroy()` to a background thread using `viewModelScope.launch(Dispatchers.Default)`.
3. **Post enable/disable to Handler:** Use `Handler(Looper.getMainLooper()).post {}` for UI operations to avoid nested synchronous calls.
4. **Add timeout + force destroy:** If `onVrDisabled()` doesn't complete within 2 seconds, force-close without waiting.

### Verification
- Test VR toggle 20 times in a row (enter/exit/enter/exit...)
- Monitor ANR logs for main thread blockage during `disableVrMode()`
- Verify PixelCopy callback count matches expected frequency (stopped when VR is off)

---

## UI Issues

### P0: App Name & Icon
**Problem:** Default Insta360 SDK name and icon are shown in Android launcher.
**Fix:**
- Change `android:label` in `app/src/main/AndroidManifest.xml` from default to app name
- Replace `ic_launcher` / `ic_launcher_foreground` / `ic_launcher_background` in `app/src/main/res/mipmap-*` and `drawable-*` with custom assets
- Update `roundIcon` attribute

### P1: Offline Button Overlaps Camera List
**Problem:** Button element in `activity_main.xml` overlaps RecyclerView of discovered cameras.
**Fix:** Adjust layout constraints — button should be constrained to parent bottom, list should have `layout_marginBottom` set to button height + padding.

### P2: Black & White Design — Material3 Redesign
**Problem:** All colors are black/white, buttons are sharp rectangles, no visual hierarchy.
**Plan:**
1. Create a proper color palette (primary, secondary, surface, error colors) in `colors.xml`
2. Apply Material3 theme in `themes.xml` — `Theme.Material3.Dark` or custom
3. Replace plain `Button`/`TextView` with Material components: `MaterialButton`, `Chip`, `CardView`
4. Add corner radius, elevation, ripple effects
5. Add Lottie animations for loading, transitions, capture button states
6. Design a proper color-coded capture mode carousel

### P3: Player Settings BottomSheet
**Problem:** Settings dialog in player is ugly (inherits from `BaseBottomSheetDialogFragment` with transparent background and minimal styling).
**Fix:** Redesign with proper Material3 BottomSheet styling, rounded corners, structured sections, proper typography.

### P4: Capture Settings Picker
**Problem:** `PickerView` uses flat text with no visual distinction between selected/unselected items.
**Fix:** Highlight selected item, add icons for each setting type, use `ChipGroup` or segmented control pattern.

### Design Constraints
- VR mode requires minimal UI (only essential controls visible)
- Mono mode needs clear preview with overlaid controls
- Both modes need consistent color language (not black-on-white everywhere)

---

## Architectural Debt (Phase 2-3 leftovers)

### UseCases in wrong module
**Current:** UseCases in `:data:camera/usecase/` — concrete classes with SDK dependencies.
**Target:** Interfaces in `:domain`, implementations in `:data:camera`. Feature modules inject interfaces, not concrete classes.

### Feature modules import SDK directly
**Current:** `:feature:capture`, `:feature:connect`, `:feature:shot`, `:feature:settings` have `implementation(libs.insta.camera)`.
**Target:** Feature modules should NOT import SDK. All camera interaction goes through UseCases/Repository interfaces from `:domain`.

### ConnectViewModel in app module
**Reason:** Uses `ConnectService` (Android Service with R resources + `MainActivity` reference).
**Fix:** Extract `ConnectService` references behind an interface in `:core:base`, implement in `:app`, inject into `ConnectViewModel`. Then move VM to `:feature:connect`.

### PickerView resources in app
**Reason:** `PickerView`, `PickerAdapter`, `EffectiveMode` use app databinding layouts (`DialogFragmentPlayerSettingBinding`, `ItemSettingSelectBinding`, `ItemSettingValueBinding`).
**Fix:** Move layout resources to `:core:base` or `:feature:capture`, regenerate bindings.

---

## Future: CV Integration & Performance

### Phase 4: C++/JNI Foundation
**Purpose:** Low-latency computation for real-time object detection.
- Quaternion math on C++ with Eigen (3-10× faster)
- JSON detection parsing with simdjson (5-20× faster)
- GPU texture access via FrameGrabber in C++ (avoids Java→native copies)
- TFLite inference on GPU delegate (once per frame)
- Whole CV pipeline: FrameGrabber → ImagePreprocessor → TFLite → NMS → Tracker

### Phase 5: Testing & Benchmarking
**Purpose:** Confidence to ship + measurable performance goals.
- **Unit tests:** `Quaternion`, `SensorFusionEngine`, `UseCases`, `ViewModels` — 50+ tests
- **Integration tests:** `CameraRepository` with real camera, VR toggle smoke test
- **Macrobenchmark:** Cold start, first frame latency, VR frame drops
- **End-to-end latency:** Measure time from camera frame → detection result → arrow rendered on screen
- **Structured logging:** `EventLogger` with searchable event timeline in debug UI

### Target Performance Goals
| Metric | Current | Target |
|--------|---------|--------|
| Sensor fusion per frame | ~2ms (Kotlin) | <0.2ms (C++) |
| Detection JSON parse (1000 frames) | ~100ms (Gson) | ~5ms (simdjson) |
| CV inference per frame | N/A (offline) | <50ms (GPU TFLite) |
| End-to-end detection latency | N/A | <100ms |
| VR PixelCopy per frame | ~2ms | ~1ms |
