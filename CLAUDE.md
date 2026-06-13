# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Set JAVA_HOME (required before any Gradle command)
export JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
export PATH="$JAVA_HOME/bin:$PATH"

# Build debug APK
./gradlew assembleDebug

# Run all unit tests
./gradlew testDebugUnitTest

# Run a specific test class
./gradlew testDebugUnitTest --tests "*PanoramaFovMathTest*"

# Clean build
./gradlew clean assembleDebug
```

## Architecture

This is an Android companion app for Insta360 cameras. It connects via BLE/WiFi/USB, controls capture (photo/video/live modes), and provides 360° spherical video playback with gyroscope-controlled view direction and VR split-screen mode.

### Base Classes (Generic VB + VM pattern)

Every screen extends `BaseActivity<T : ViewBinding, V : BaseViewModel>` or `BaseFragment<T, V>`. These use reflection to auto-inflate the ViewBinding and create the ViewModel. Override `initView()` and `initListener()` for setup, `onEvent(event: BaseEvent)` for event handling.

The event system: ViewModels emit `BaseEvent` instances via a `MutableSharedFlow`. BaseActivity/BaseFragment collect from it in a coroutine and dispatch to `onEvent()`.

The two main screens:
- **`CaptureActivity`** — live camera preview, capture control, VR mode for live stream
- **`LocalSphericalPlayerActivity`** — offline 360° video playback with ExoPlayer (Media3), detection overlays, VR mode

### Gyroscope Orientation (`GyroOrientationController`)

Located in `ui/capture/` but shared by both capture and player modules. Quaternion-based sensor fusion:

1. Reads `Sensor.TYPE_ROTATION_VECTOR` → converts to rotation matrix via `SensorManager.getRotationMatrixFromVector()`
2. Remaps matrix per display rotation (`remapCoordinateSystem`) — ROTATION_90 uses `(AXIS_Z, AXIS_MINUS_X)`, ROTATION_0 uses `(AXIS_X, AXIS_Z)`
3. Converts to `Quaternion(w,x,y,z)` via Shepperd's algorithm
4. Calibration: stores current quaternion; subsequent readings use `q_relative = q_current * conjugate(q_calibration)`
5. SLERP smoothing (alpha=0.12) between previous and current relative quaternion
6. ZYX Euler extraction from smoothed quaternion → `(yaw, pitch, roll)` in degrees
7. Sensitivity scaling: `yawSensitivity = 0.04 * sensivity`, `pitchSensitivity = 0.02 * sensivity` (default sensivity=1.2, adjustable via VR settings dialog)
8. Raw (unscaled) Euler angles exposed via `getRawEulerYawDeg()`/`getRawEulerPitchDeg()` — these always update even before calibration

In the player, Media3's built-in sensor rotation is **disabled** (`setUseSensorRotation(false)`) — the custom gyro has exclusive control of the view via `sphericalView.setYaw/setPitch` (reflection).

### Spherical Video & Panorama Math

**`EquirectangularProjection`** — coordinate system: +X forward, +Y right, +Z up (right-handed). yaw=0 at horizontal center of equirectangular image, pitch=0 at vertical center. `fromNormalized(x,y)` maps [0,1]×[0,1] image coords to yaw/pitch. `fromYawPitch(yaw,pitch)` produces a `PanoramaDirection` containing a 3D `UnitVector3` and a `UnitQuaternion`.

**`PanoramaFovMath`** — two methods for determining if a target is visible:
- `resolveTarget()` — Euler angle subtraction (simple, used in capture)
- `resolveTargetQuat()` — quaternion-based (used in player): rotates target's world-space vector by `gaze.orientation.conjugate()` to get the target direction in the viewer's local frame, then checks if it's within FOV. This is robust against mismatched Euler conventions between the gyro and equirectangular math.

**FOV** is adjustable via `HORIZONTAL_FOV_RAD`/`VERTICAL_FOV_RAD` in `LocalSphericalPlayerActivity`'s companion object (default: 60° H, 45° V).

### Detection Overlay System

1. A Python script generates JSON sidecar files with frame-by-frame object detections (bounding boxes, normalized centers)
2. `VideoDetectionSidecarParser` parses JSON → `VideoDetectionTimeline` for time-indexed lookup
3. Every 200ms, `updateCurrentDetections()` gets the current playback position, finds the nearest detection frame, and converts each detected object's `centerNorm` → `PanoramaDirection` via `EquirectangularProjection.fromNormalized()`
4. `resolveTargetQuat()` checks if target is inside the current FOV; if outside, computes a screen-space arrow angle
5. `DirectionArrowOverlayView` draws a white arrow on a dark circle at the screen edge pointing toward the target. In VR mode, draws two arrows (left eye at 25% width, right eye at 75% width).

### VR Mode

**Capture VR** (`VrManager`): Creates a second `InstaCapturePlayerView` for the right eye, copies its frames to a left-eye `ImageView` via `PixelCopy` at 30fps. Right eye yaw offset by `vrIpdYawDeg` (default 3°) for stereoscopic effect.

**Player VR** (`LocalVrManager`): Uses the same `SphericalGLSurfaceView` for both eyes — copies frames via `PixelCopy` to a left-eye `ImageView` at 30fps. Both views are in a horizontal `LinearLayout` with equal weight, adjustable scale (0.5–1.5) and spacing (±200dp).

## Key Dependencies

- **Media3 ExoPlayer 1.5.1** — `SphericalGLSurfaceView` for 360° video rendering
- **Insta360 SDK** (`com.arashivision.sdk:sdkcamera` and `:sdkmedia`, version `1.8.1_build_06`) — camera control and live preview
- **Kotlin 2.0.21**, AGP 8.12.3, compileSdk 35, minSdk 29

## Display Rotation Conventions

The app works in landscape orientation (ROTATION_90). The gyro's `remapCoordinateSystem` mapping depends on rotation:
- ROTATION_0 (portrait): remap `(AXIS_X, AXIS_Z)` — pitch = values[1]
- ROTATION_90/270 (landscape): remap `(AXIS_Z, AXIS_MINUS_X)` or `(AXIS_MINUS_Z, AXIS_X)` — pitch = values[2] (not values[1], which is roll in landscape)

The gyro controller handles this by selecting the correct `getOrientation` component based on display rotation.
