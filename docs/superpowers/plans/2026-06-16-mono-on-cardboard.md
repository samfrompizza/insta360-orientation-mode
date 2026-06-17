# Mono-on-Cardboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the mono (non-VR) 360 view with the existing Cardboard native renderer in a new full-screen single-eye mode, so gyro sensitivity works in mono and the arrow can share the Cardboard head pose — replacing media3's `SphericalGLSurfaceView`.

**Architecture:** The native `CardboardRenderer` gains a `mono` flag. In mono it draws the sphere once into the full screen with a plain perspective projection (no per-eye split, no lens distortion); in VR it keeps the two-viewport + distortion path. `CardboardVrView` exposes `setMonoMode(Boolean)`. `PlayerScreen` uses the one `CardboardVrView` for both modes (toggling mono), and the media3 `SphericalPanoramaView` + its frame-metadata/camera-motion plumbing are deleted. The arrow reads the Cardboard pose in both modes.

**Tech Stack:** C++ (GL ES2 + Cardboard SDK native), Kotlin, Jetpack Compose, media3 1.10.1 (decoder only, no spherical GL view), JUnit4 + Robolectric + MockK for `:android`/`:app` tests.

---

## Background facts (read before starting)

- Native renderer: `android/src/main/cpp/cardboard_renderer.{h,cc}`. `OnDrawFrame` currently always renders two eyes into an FBO then calls `CardboardDistortionRenderer_renderEyeToDisplay`. `GetPose()` returns the (sensitivity-scaled) head matrix. `SetVrParams`/`SetSensitivity` already exist.
- `util.{h,cc}`: `Matrix4x4` has `operator*`, `ToGlArray()`, `Identity()`, `Translation()`. Need a new `Matrix4x4::Perspective(...)`. `sphere_.Draw(mvp_gl, st_matrix_, oes_texture_id_)` draws the sphere.
- The sphere is viewed from inside (`glDisable(GL_CULL_FACE)`), radius is small (the camera sits at origin). A perspective with the head_view applied looks correct as long as near/far bracket the sphere.
- JNI: `android/src/main/cpp/cardboard_jni.cc`, macro `JNI_METHOD(ret, name)`. `Native(ptr)` casts the long handle.
- `CardboardVrView` (`android/src/main/kotlin/com/panorama/android/gl/CardboardVrView.kt`) hosts the GL thread; it already has `setVrParams`, `setSensitivity`, `gazeRef`, external decls. Native calls go through `queueEvent { }`.
- `PlayerScreen.kt` currently branches on `state.vrEnabled`: VR → `CardboardVrView`, mono → `SphericalPanoramaView`. The mono branch wires `attachFrameMetadataListener`/`attachCameraMotionListener`. The arrow's gaze source is set by a `DisposableEffect(state.vrEnabled, vrView)`: VR → `vrView.gazeRef`, else → `viewModel.gazeRef`.
- To delete: `SphericalPanoramaView.kt`; `PlayerViewModel.attachFrameMetadataListener`/`attachCameraMotionListener`; `ExoVideoPlayer.setVideoFrameMetadataListener`/`setCameraMotionListener` and their tests.
- Build/run via context-mode `ctx_execute(language:"shell", ...)`, NOT raw Bash (the gradle hook redirects). Rebuild: `./gradlew :app:assembleDebug`; install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Native changes recompile via the CMake task.
- On-device check is the real test for GL/JNI (no JVM test harness for native). Device 4c3c060a over adb.

## File Structure

- **Modify** `android/src/main/cpp/util.h` / `util.cc` — add `Matrix4x4::Perspective(fovYRad, aspect, near, far)`.
- **Modify** `android/src/main/cpp/cardboard_renderer.{h,cc}` — add `mono_` flag + `SetMonoMode(bool)`; branch `OnDrawFrame` into a mono single-viewport path.
- **Modify** `android/src/main/cpp/cardboard_jni.cc` — add `nativeSetMonoMode`.
- **Modify** `android/src/main/kotlin/com/panorama/android/gl/CardboardVrView.kt` — add `setMonoMode` + external; doc update (now both modes).
- **Modify** `app/src/main/kotlin/com/panorama/app/player/PlayerScreen.kt` — single `CardboardVrView` for both modes; toggle mono; arrow gaze always from `cardboardRef.gazeRef`; drop `SphericalPanoramaView`.
- **Delete** `android/src/main/kotlin/com/panorama/android/gl/SphericalPanoramaView.kt`.
- **Modify** `app/src/main/kotlin/com/panorama/app/player/PlayerViewModel.kt` — remove the two media3 listener proxies.
- **Modify** `android/src/main/kotlin/com/panorama/android/media/ExoVideoPlayer.kt` + test — remove the two media3 setters.

---

### Task 1: Perspective matrix in util

**Files:**
- Modify: `android/src/main/cpp/util.h:30-32` (after `Translation`)
- Modify: `android/src/main/cpp/util.cc` (after `Matrix4x4::Translation` impl, near top)

- [ ] **Step 1: Declare Perspective in util.h**

In `struct Matrix4x4`, after `static Matrix4x4 Translation(float x, float y, float z);`, add:

```cpp
  // Standard GL perspective projection (column-major). fov_y in radians.
  static Matrix4x4 Perspective(float fov_y, float aspect, float near, float far);
```

- [ ] **Step 2: Implement Perspective in util.cc**

Find `Matrix4x4 Matrix4x4::Translation(` and add immediately after its closing brace:

```cpp
Matrix4x4 Matrix4x4::Perspective(float fov_y, float aspect, float near,
                                 float far) {
  Matrix4x4 r{};  // zero-initialised
  const float f = 1.0f / std::tan(fov_y * 0.5f);
  r.m[0][0] = f / aspect;
  r.m[1][1] = f;
  r.m[2][2] = (far + near) / (near - far);
  r.m[2][3] = -1.0f;
  r.m[3][2] = (2.0f * far * near) / (near - far);
  // column-major: m[col][row]; the -1 that copies z into w is m[2][3].
  return r;
}
```

`<cmath>` is already included (added for `Quatf::ScaledAngle`). If a build error says `std::tan` is missing, confirm `#include <cmath>` is present in util.cc.

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :android:buildCMakeDebug` (or `:app:assembleDebug`)
Expected: `BUILD SUCCESSFUL`, no CMake/ninja errors.

- [ ] **Step 4: Commit**

```bash
git add android/src/main/cpp/util.h android/src/main/cpp/util.cc
git commit -m "feat(android): add Matrix4x4::Perspective for the mono projection"
```

---

### Task 2: Native mono render path

**Files:**
- Modify: `android/src/main/cpp/cardboard_renderer.h` (public method + field)
- Modify: `android/src/main/cpp/cardboard_renderer.cc` (`SetMonoMode` + `OnDrawFrame` branch)

- [ ] **Step 1: Declare SetMonoMode + field in the header**

In `cardboard_renderer.h`, after `void SetSensitivity(float s) { sensitivity_ = s; }` add:

```cpp
  // When true, render one full-screen view (no stereo split, no lens distortion).
  void SetMonoMode(bool mono) { mono_ = mono; }
```

In the private fields, after `bool ref_pose_set_ = false;` add:

```cpp
  bool mono_ = false;  // full-screen single view vs split-screen stereo
```

- [ ] **Step 2: Branch OnDrawFrame for mono**

In `cardboard_renderer.cc`, replace the body of `OnDrawFrame` from the line
`glBindFramebuffer(GL_FRAMEBUFFER, framebuffer_);` through the closing of the
distortion call (the `CardboardDistortionRenderer_renderEyeToDisplay(...)` block)
with this mono-aware version. Keep the `UpdateDeviceParams`/`sphere_ready_`/`head_view`
lines above it unchanged:

```cpp
  if (mono_) {
    // Mono: draw the sphere once, straight to the display, with a plain perspective.
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glEnable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);
    glDisable(GL_SCISSOR_TEST);
    glViewport(0, 0, screen_width_, screen_height_);
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    const float aspect =
        screen_height_ > 0 ? static_cast<float>(screen_width_) / screen_height_ : 1.0f;
    const Matrix4x4 projection =
        Matrix4x4::Perspective(kMonoFovYRad, aspect, /*near=*/0.1f, /*far=*/100.0f);
    const Matrix4x4 mvp = projection * head_view;
    const std::array<float, 16> mvp_gl = mvp.ToGlArray();
    sphere_.Draw(mvp_gl.data(), st_matrix_, oes_texture_id_);
    CHECKGL("OnDrawFrame(mono)");
    return;
  }

  glBindFramebuffer(GL_FRAMEBUFFER, framebuffer_);
  glEnable(GL_DEPTH_TEST);
  glDisable(GL_CULL_FACE);  // sphere is viewed from inside; keep all faces
  glDisable(GL_SCISSOR_TEST);
  glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
  glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

  const int half = screen_width_ / 2;
  const int vw = static_cast<int>(half * eye_scale_);
  const int vh = static_cast<int>(screen_height_ * eye_scale_);
  const int gap = static_cast<int>(half * eye_gap_);
  const int cy = (screen_height_ - vh) / 2;
  for (int eye = 0; eye < 2; ++eye) {
    const int half_origin = (eye == kLeft) ? 0 : half;
    const int cx = half_origin + (half - vw) / 2 + (eye == kLeft ? -gap : gap);
    glViewport(cx, cy, vw, vh);
    const Matrix4x4 eye_matrix = GetMatrixFromGlArray(eye_matrices_[eye]);
    const Matrix4x4 projection = GetMatrixFromGlArray(projection_matrices_[eye]);
    const Matrix4x4 mvp = projection * (eye_matrix * head_view);
    const std::array<float, 16> mvp_gl = mvp.ToGlArray();
    sphere_.Draw(mvp_gl.data(), st_matrix_, oes_texture_id_);
  }

  CardboardDistortionRenderer_renderEyeToDisplay(
      distortion_renderer_, /*target=*/0, /*x=*/0, /*y=*/0, screen_width_,
      screen_height_, &left_eye_texture_description_,
      &right_eye_texture_description_);
  CHECKGL("OnDrawFrame");
```

- [ ] **Step 3: Add the mono FOV constant**

Near the top of `cardboard_renderer.cc`, beside the existing `constexpr float kZNear`/`kZFar`, add:

```cpp
constexpr float kMonoFovYRad = 1.3962634f;  // ~80° vertical FOV for the mono view
```

- [ ] **Step 4: Guard mono against the device-params black screen**

`UpdateDeviceParams()` returns false (clears to black) until a viewer profile resolves. In mono we don't need the lens profile. At the very top of `OnDrawFrame`, replace:

```cpp
  if (!UpdateDeviceParams()) {
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    return;
  }
  if (!sphere_ready_) return;
```

with:

```cpp
  // Mono needs no lens profile; only the stereo path depends on device params.
  if (!mono_ && !UpdateDeviceParams()) {
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    return;
  }
  if (!sphere_ready_) return;
```

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add android/src/main/cpp/cardboard_renderer.h android/src/main/cpp/cardboard_renderer.cc
git commit -m "feat(android): mono single-viewport render path in the Cardboard renderer"
```

---

### Task 3: JNI + CardboardVrView mono toggle

**Files:**
- Modify: `android/src/main/cpp/cardboard_jni.cc` (after `nativeSetSensitivity`)
- Modify: `android/src/main/kotlin/com/panorama/android/gl/CardboardVrView.kt`

- [ ] **Step 1: Add the JNI method**

After the `nativeSetSensitivity` JNI_METHOD block, add:

```cpp
JNI_METHOD(void, nativeSetMonoMode)
(JNIEnv* /*env*/, jobject /*obj*/, jlong ptr, jboolean mono) {
  Native(ptr)->SetMonoMode(mono);
}
```

- [ ] **Step 2: Add setMonoMode + external in CardboardVrView**

After the `setSensitivity` function add:

```kotlin
    /** Switch between full-screen mono (true) and split-screen stereo VR (false). GL thread. */
    fun setMonoMode(mono: Boolean) {
        if (nativeApp != 0L) queueEvent { if (nativeApp != 0L) nativeSetMonoMode(nativeApp, mono) }
    }
```

After `private external fun nativeSetSensitivity(nativeApp: Long, s: Float)` add:

```kotlin
    private external fun nativeSetMonoMode(nativeApp: Long, mono: Boolean)
```

- [ ] **Step 3: Update the class doc**

Change the class KDoc first line from "Split-screen stereo VR viewer backed by the Google Cardboard SDK." to:

```kotlin
/** 360 viewer backed by the Google Cardboard SDK native renderer. Renders either a full-screen mono
 *  view or split-screen stereo VR ([setMonoMode]); both share the same head tracker, so gyro
 *  sensitivity and the head pose ([gazeRef]) are consistent across modes.
```

(Leave the rest of the KDoc body.)

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add android/src/main/cpp/cardboard_jni.cc android/src/main/kotlin/com/panorama/android/gl/CardboardVrView.kt
git commit -m "feat(android): expose mono-mode toggle through JNI to CardboardVrView"
```

---

### Task 4: PlayerScreen uses one CardboardVrView for both modes

**Files:**
- Modify: `app/src/main/kotlin/com/panorama/app/player/PlayerScreen.kt`

- [ ] **Step 1: Replace the vrEnabled branch with a single CardboardVrView**

Replace the whole `if (state.vrEnabled) { ... } else { ... }` block (the two `AndroidView` factories for `CardboardVrView` and `SphericalPanoramaView`) with this single factory. Keep `cardboardRef` but remove `sphericalRef`:

```kotlin
        var cardboardRef by remember { mutableStateOf<CardboardVrView?>(null) }

        // One Cardboard-backed view for both modes; mono toggles the single-viewport path.
        val cardboardView = remember {
            { ctx: android.content.Context ->
                CardboardVrView(ctx).apply {
                    cardboardRef = this
                    onVideoSurfaceReady = { surface -> viewModel.attachVideoSurface(surface) }
                    onVideoSurfaceDestroyed = { viewModel.attachVideoSurface(null) }
                    setVrParams(viewModel.vrEyeScale, viewModel.vrEyeGap)
                    setSensitivity(viewModel.vrSensitivity)
                    setMonoMode(!viewModel.state.value.vrEnabled)
                }
            }
        }
        AndroidView(
            factory = cardboardView,
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.setMonoMode(!state.vrEnabled)
                view.onResume()
            },
            onRelease = { view ->
                view.onDestroy()
                cardboardRef = null
            },
        )
```

- [ ] **Step 2: Simplify the lifecycle observer (drop sphericalRef)**

In the `DisposableEffect(lifecycleOwner)` observer, remove the two `sphericalRef?.onResume()` / `sphericalRef?.onPause()` lines, leaving only the `cardboardRef?.` calls and `viewModel.startSensor()/stopSensor()`.

- [ ] **Step 3: Arrow gaze always from the Cardboard pose**

Replace the arrow-gaze `DisposableEffect` + `arrowGazeRef` block with:

```kotlin
        // Both modes render with the Cardboard head tracker, so the arrow always reads its pose.
        val vrView = cardboardRef
        val arrowGazeRef = vrView?.gazeRef ?: viewModel.gazeRef
        DisposableEffect(vrView) {
            if (vrView != null) viewModel.useGazeSource { vrView.gazeRef.get() }
            onDispose { viewModel.useGazeSource(null) }
        }
```

- [ ] **Step 4: Remove the now-unused import and SphericalPanoramaView usage**

Delete the import line `import com.panorama.android.gl.SphericalPanoramaView`. Confirm no `sphericalRef` / `SphericalPanoramaView` / `attachFrameMetadataListener` / `attachCameraMotionListener` references remain in this file:

Run: `grep -n 'spherical\|Spherical\|FrameMetadata\|CameraMotion' app/src/main/kotlin/com/panorama/app/player/PlayerScreen.kt`
Expected: no output.

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/panorama/app/player/PlayerScreen.kt
git commit -m "feat(app): render mono and VR through one CardboardVrView; arrow uses Cardboard pose"
```

---

### Task 5: Delete media3 SphericalPanoramaView and its plumbing

**Files:**
- Delete: `android/src/main/kotlin/com/panorama/android/gl/SphericalPanoramaView.kt`
- Modify: `app/src/main/kotlin/com/panorama/app/player/PlayerViewModel.kt` (remove two proxies + unused imports)
- Modify: `android/src/main/kotlin/com/panorama/android/media/ExoVideoPlayer.kt` (remove two setters + unused imports)
- Modify: `android/src/test/kotlin/com/panorama/android/media/ExoVideoPlayerTest.kt` (remove the two tests)

- [ ] **Step 1: Delete the view**

```bash
git rm android/src/main/kotlin/com/panorama/android/gl/SphericalPanoramaView.kt
```

- [ ] **Step 2: Remove the ViewModel proxies**

In `PlayerViewModel.kt` delete the two functions:

```kotlin
    fun attachFrameMetadataListener(listener: VideoFrameMetadataListener) =
        exo.setVideoFrameMetadataListener(listener)

    fun attachCameraMotionListener(listener: CameraMotionListener) =
        exo.setCameraMotionListener(listener)
```

Then remove their now-unused imports `androidx.media3.exoplayer.video.VideoFrameMetadataListener` and `androidx.media3.exoplayer.video.spherical.CameraMotionListener` if no longer referenced.

Run: `grep -n 'VideoFrameMetadataListener\|CameraMotionListener' app/src/main/kotlin/com/panorama/app/player/PlayerViewModel.kt`
Expected: no output.

- [ ] **Step 3: Remove the ExoVideoPlayer setters**

In `ExoVideoPlayer.kt` delete:

```kotlin
    fun setVideoFrameMetadataListener(listener: VideoFrameMetadataListener) =
        onPlayerThread { (player as ExoPlayer).setVideoFrameMetadataListener(listener) }

    fun setCameraMotionListener(listener: CameraMotionListener) =
        onPlayerThread { (player as ExoPlayer).setCameraMotionListener(listener) }
```

Remove their now-unused imports (`VideoFrameMetadataListener`, `CameraMotionListener`) if unreferenced elsewhere in the file.

Run: `grep -n 'FrameMetadataListener\|CameraMotionListener' android/src/main/kotlin/com/panorama/android/media/ExoVideoPlayer.kt`
Expected: no output.

- [ ] **Step 4: Remove the two ExoVideoPlayer tests**

In `ExoVideoPlayerTest.kt` delete the test methods named ``setVideoFrameMetadataListener delegates on the player looper`` and ``setCameraMotionListener delegates on the player looper`` (and any imports they alone used).

Run: `grep -n 'FrameMetadataListener\|CameraMotionListener' android/src/test/kotlin/com/panorama/android/media/ExoVideoPlayerTest.kt`
Expected: no output.

- [ ] **Step 5: Build + run unit tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :android:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: drop media3 SphericalGLSurfaceView path (Cardboard renders mono too)"
```

---

### Task 6: On-device verification

**Files:** none (manual device check).

- [ ] **Step 1: Install and launch**

Run:
```
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.panorama.app/.MainActivity
```

- [ ] **Step 2: Mono playback check**

Open a 360 clip (no VR). Confirm with the user: video plays, right-side-up, not frozen, fills the screen, and head/phone rotation pans the view.

- [ ] **Step 3: Mono sensitivity check**

Open Settings overlay, change Gyro sensitivity. Confirm with the user that the pan speed in mono changes with the slider.

- [ ] **Step 4: VR still works**

Toggle VR on. Confirm split-screen stereo + lens distortion still render and the size/distance/sensitivity sliders still work.

- [ ] **Step 5: No regressions in open/freeze**

Confirm with the user there is no black screen / freeze on opening clips (the bug class that originally drove the media3 switch). If a freeze appears, STOP and debug with systematic-debugging before proceeding.

- [ ] **Step 6: Final commit (only if any tuning was needed)**

If mono FOV / near-far needed adjustment during the device check, commit it:

```bash
git add android/src/main/cpp/cardboard_renderer.cc
git commit -m "fix(android): tune mono FOV for natural framing"
```

---

## Notes / risks

- **Mono FOV (`kMonoFovYRad`)** is a first guess (~80° vertical). On device it may feel too wide (fish-eye) or too narrow; adjust in Task 6 Step 6.
- **Open/freeze regression:** the media3 switch was made to dodge a thread-affinity/recomposition/render-loop bug class. The Cardboard path already opens video reliably in VR today, so reusing it for mono should not reintroduce that class — but Task 6 Step 5 explicitly checks for it.
- **Arrow direction** remains a separate known issue (pose convention); this plan only makes mono share the Cardboard pose, it does not fix the arrow's screen-mapping.
