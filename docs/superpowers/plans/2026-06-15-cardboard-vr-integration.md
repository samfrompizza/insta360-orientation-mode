# Cardboard VR Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the custom split-screen GL stack (`PanoramaGlView` + `PanoramaRenderer` + `StereoEyeLayout` + `GazePredictor`) in the VR (`state.vrEnabled == true`) path with a Google Cardboard SDK–driven renderer that draws the same equirect sphere (sampling the single ExoPlayer OES texture) into two per-eye viewports and applies Cardboard lens barrel distortion via `CardboardDistortionRenderer_renderEyeToDisplay`. Head tracking comes from Cardboard's own IMU fusion (`CardboardHeadTracker`), not the `OrientationEngine`/`TYPE_ROTATION_VECTOR` pipeline. The mono path (`SphericalPanoramaView`) is untouched. One decoder, one OES `SurfaceTexture`, sampled for both eyes — never two players.

**Architecture:** Hybrid, branched in `PlayerScreen` on `state.vrEnabled` (the existing branch point — see `PlayerScreen.kt`, VR factory creates `PanoramaGlView`, mono creates `SphericalPanoramaView`). The VR branch is rewired to host a new `CardboardVrView` (a `GLSurfaceView` subclass) instead of `PanoramaGlView`. `CardboardVrView` owns a GL context, creates an OES `SurfaceTexture`/`Surface` on its GL thread and hands it to the player via the **same** `onVideoSurfaceReady: ((Surface) -> Unit)?` contract the existing views use (wired to `viewModel.attachVideoSurface(surface)` → `ExoVideoPlayer.setVideoSurface`). Per frame the GL thread: pumps the newest decoded frame into the OES texture; queries the Cardboard head pose + per-eye view/projection matrices; renders the equirect sphere into ONE wide off-screen FBO texture (left half = left eye, right half = right eye); then calls `CardboardDistortionRenderer_renderEyeToDisplay` to warp both halves to the display. All Cardboard rendering and head tracking are **pure native C** in `libGfxPluginCardboard.so`; we link it from our own NDK/CMake module `cardboard_jni`. The Java side of the prebuilt AAR (`com.google.cardboard.sdk.*`) provides only the QR viewer-profile flow (`QrCodeCaptureActivity`) + device/screen params.

The custom GL VR stack (`PanoramaGlView.kt`, `PanoramaRenderer.kt`, `StereoEyeLayout` in `:core`, `GazePredictor`, the VR shaders) is **NOT deleted** in this plan — it is left in place until the Cardboard path is verified on-device, then removed in a later cleanup task (Task 11, optional). `OrientationEngine` keeps running read-only to feed the off-screen `ArrowOverlay` (which reads `gazeRef` independently). Whether the arrow should instead track the Cardboard pose is an **OPEN QUESTION** (see below), deferred.

**Tech Stack:** Kotlin + JNI/C++17, Jetpack Compose, AGP 9.2.1 / Gradle 9.4.1, Kotlin 2.3.21, compileSdk 36 / minSdk 29 / targetSdk 35, arm64-v8a only, Java/Kotlin 17. NDK `29.0.14206865`, CMake `3.22.1` (both installed under `/home/farid/android-sdk/{ndk,cmake}`). media3 1.10.1 ExoPlayer (unchanged). Hilt DI. Cardboard SDK v1.34.0 consumed as a **prebuilt `.aar` file dependency** (NOT the `sdk/` Groovy module). C API: `cardboard.h` (vendored), `libGfxPluginCardboard.so` (extracted from the AAR's `jni/arm64-v8a/`).

---

## OPEN QUESTIONS (resolve with user before/while implementing)

1. **Arrow gaze source in VR.** The off-screen guidance `ArrowOverlay` currently reads `OrientationEngine.gazeRef` (raw `TYPE_ROTATION_VECTOR`, unpredicted) in both mono and VR. With Cardboard owning head tracking for the rendered sphere, the arrow's yaw will be derived from a *different* fusion than the sphere the user sees, so the arrow may point slightly off relative to where the head is actually looking. Options: (a) leave as-is — `OrientationEngine` still runs, arrow stays on `gazeRef`, accept minor divergence (recommended for first cut, zero extra work); (b) expose the Cardboard pose yaw out of the native layer (new JNI getter reading the same `CardboardHeadTracker_getPose` quaternion) and feed the arrow from that. **This plan implements (a)** and notes (b) as a deferred Task 10. Confirm (a) is acceptable.

2. **Activity for the QR flow.** `QrCodeCaptureActivity` is an `AppCompatActivity` inside the AAR; launching it requires that the app theme/dependency chain satisfies AppCompat. The app currently uses a single `ComponentActivity` (`MainActivity`) with no AppCompat dependency visible. Need to confirm whether adding `androidx.appcompat:appcompat` (transitive in the AAR or added explicitly) and merging the AAR's manifest activity entry is acceptable, or whether to start with `CardboardQrCode_scanQrCodeAndSaveDeviceParams()` from native (which the SDK wires to its own activity internally). **This plan uses the native `scanQrCodeAndSaveDeviceParams` entry point (Task 9)** to avoid hand-declaring the activity, and flags the AppCompat/manifest-merge path as the fallback. Confirm.

3. **Predicted-pose lead time.** Hello-cardboard uses `GetBootTimeNano() + 50_000_000` ns (50 ms) prediction for `CardboardHeadTracker_getPose`. Our existing pipeline had a `DEFAULT_LEAD_TIME_MS`-style predictor (`GazePredictor`). 50 ms is a sane default; confirm we are not trying to match a specific value from the old predictor.

4. **Sphere mesh ownership.** The native renderer needs an equirect sphere mesh (positions + UVs) and a `samplerExternalOES` shader, mirroring what `PanoramaRenderer`/`EquirectProjection` do in Kotlin today. This plan **builds the sphere in C** inside `cardboard_jni` (self-contained, like hello-cardboard builds its room/target). It does NOT reuse `:core`'s `EquirectProjection` (that is JVM-side). Confirm we accept a second sphere generator in C rather than threading mesh data across JNI.

---

## Background facts (read before starting — all verified)

### Existing project wiring (paths exact)
- **Branch point:** `app/src/main/kotlin/com/panorama/app/player/PlayerScreen.kt` — `if (state.vrEnabled) { ...PanoramaGlView... } else { ...SphericalPanoramaView... }`. The VR factory sets `glViewRef = this; bindGazeRef(viewModel.gazeRef); onVideoSurfaceReady = { surface -> viewModel.attachVideoSurface(surface) }`, then `AndroidView(update = { view -> view.setVrEnabled(true); view.onResume() }, onRelease = { glViewRef = null })`. A `DisposableEffect(lifecycleOwner)` forwards `ON_RESUME → viewModel.startSensor(); glViewRef?.onResume()` and `ON_PAUSE → glViewRef?.onPause(); viewModel.stopSensor()`.
- **Surface contract (the contract `CardboardVrView` must satisfy):** existing GL views expose `var onVideoSurfaceReady: ((Surface) -> Unit)?` and invoke it on the GL thread once the OES `SurfaceTexture` exists. `PanoramaGlView.onSurfaceTextureReady(st)` does `st.setOnFrameAvailableListener { renderer.pendingFrame = true }; onVideoSurfaceReady?.invoke(Surface(st))`. `SphericalPanoramaView` also exposes `onVideoSurfaceDestroyed: (() -> Unit)?`.
- **OES texture creation pattern (mirror in C):** `PanoramaRenderer.createOesTexture()` does `glGenTextures` → `glBindTexture(GL_TEXTURE_EXTERNAL_OES, id)` → MIN/MAG `GL_LINEAR`, WRAP_S/T `GL_CLAMP_TO_EDGE`; then `SurfaceTexture(textureId)`, per frame `updateTexImage()` + `getTransformMatrix(stMatrix)` (4×4, applied as `uStMatrix` to UVs). The OES `SurfaceTexture` must be created from a GL texture id that exists in the GLSurfaceView's context — so it must be generated on the GL thread (`onSurfaceCreated`), passed up to Kotlin (or created in Kotlin and its id handed to C — see Task 6 design).
- **Player:** `android/src/main/kotlin/com/panorama/android/media/ExoVideoPlayer.kt` — single owner of the thread-affine `Player`; every access hops onto `applicationLooper` via `onPlayerThread {}`. `setVideoSurface(surface: Surface?) = onPlayerThread { player.setVideoSurface(surface) }`. Built off-main on `HandlerThread("ExoPlayer")`, prewarmed in `PanoramaApp`. **KEEP unchanged** — we hand it exactly one `Surface` from the Cardboard view's OES texture.
- **ViewModel:** `app/src/main/kotlin/com/panorama/app/player/PlayerViewModel.kt` — `attachVideoSurface(surface: Surface?) = exo.setVideoSurface(surface)`, `gazeRef get() = orientationEngine.gazeRef`, `startSensor()/stopSensor()`, `toggleVr() = _state.update { it.copy(vrEnabled = !it.vrEnabled) }`. The frame-metadata / camera-motion sinks are mono-only and are NOT used by Cardboard (Cardboard does not consume media3 projection metadata).
- **Controls:** `app/src/main/kotlin/com/panorama/app/player/PlayerControls.kt` — has `onToggleVr` / `onRecalibrate`; the QR "gear" entry (Task 9) is added here.
- **Build files:** `:android` and `:app` are `build.gradle.kts`. `app` already declares `ndk { abiFilters += "arm64-v8a" }`. `root build.gradle.kts` pins KGP 2.3.21 + KSP 2.3.9 via `buildscript`. Versions in `gradle/libs.versions.toml` (`agp = "9.2.1"`, etc.). Gradle wrapper: `gradle-9.4.1-bin.zip`.
- **Manifest:** `app/src/main/AndroidManifest.xml` — single `.MainActivity`, `.PanoramaApp` application, no camera permission, no AppCompat.

### Cardboard SDK facts (verified)
- AAR built and present: `/home/farid/sandbox/misc/cardboard-spike/sdk/build/outputs/aar/sdk-release.aar` (316 KB). Contains `jni/arm64-v8a/libGfxPluginCardboard.so` + `classes.jar` (`com.google.cardboard.sdk.QrCodeCaptureActivity`, `DeviceParamsUtils`, `ScreenParamsUtils`, `UsedByNative`). Header: `/home/farid/sandbox/misc/cardboard-spike/sdk/include/cardboard.h`.
- **NOT on Maven; NOT importing the `sdk/` Groovy module** (it is AGP 8.13). We consume the prebuilt AAR as a flat-dir/file dependency and write our own AGP-9 CMake native module.
- **Reference impl to mirror:** `/home/farid/sandbox/misc/cardboard-spike/hellocardboard-android/src/main/jni/{hello_cardboard_jni.cc,hello_cardboard_app.cc,hello_cardboard_app.h}`, `VrActivity.java`, `CMakeLists.txt`.

### C API call sequence (verified from `cardboard.h` + `hello_cardboard_app.cc`)
- **Enums/structs:** `CardboardEye { kLeft=0, kRight=1 }`; `CardboardViewportOrientation { kLandscapeLeft=0, ... }`; `CardboardSupportedOpenGlEsTextureType { kGlTexture2D=0, kGlTextureExternalOes=1 }`; `CardboardMesh { int* indices; int n_indices; float* vertices; float* uvs; int n_vertices; }`; `CardboardEyeTextureDescription { uint64_t texture; float left_u, right_u, top_v, bottom_v; }`; `CardboardOpenGlEsDistortionRendererConfig { CardboardSupportedOpenGlEsTextureType texture_type; }`.
- **Init (once, native ctor):** `Cardboard_initializeAndroid(JavaVM* vm, jobject context)`; `head_tracker = CardboardHeadTracker_create()`; `CardboardHeadTracker_setLowPassFilter(head_tracker, 6)`.
- **Device params (lazy, on first draw / screen change):** `uint8_t* buffer; int size; CardboardQrCode_getSavedDeviceParams(&buffer, &size);` if `size==0` → no profile yet (render black, return). Else `lens_distortion = CardboardLensDistortion_create(buffer, size, screen_width, screen_height); CardboardQrCode_destroy(buffer);` then build FBO (`GlSetup`), `distortion_renderer = CardboardOpenGlEs2DistortionRenderer_create(&config)` with `config.texture_type = kGlTexture2D` (the eye FBO is a normal `GL_TEXTURE_2D`, NOT the OES texture). Then per eye: `CardboardLensDistortion_getDistortionMesh(ld, eye, &mesh); CardboardDistortionRenderer_setMesh(dr, &mesh, eye); CardboardLensDistortion_getEyeFromHeadMatrix(ld, eye, eye_matrices[eye]); CardboardLensDistortion_getProjectionMatrix(ld, eye, zNear, zFar, projection_matrices[eye]);`
- **FBO target (mirror `GlSetup`):** one `GL_TEXTURE_2D` `texture_` sized `screen_width × screen_height` (`glTexImage2D(..., GL_RGB, ..., GL_UNSIGNED_BYTE, 0)`), a `GL_DEPTH_COMPONENT16` renderbuffer, one framebuffer with color attachment `texture_` + depth attachment. `left_eye_texture_description = { texture_, left_u=0, right_u=0.5, top_v=1, bottom_v=0 }`, `right_eye_texture_description = { texture_, left_u=0.5, right_u=1, top_v=1, bottom_v=0 }` (both halves of ONE wide texture).
- **Per frame (`OnDrawFrame`):** if `!UpdateDeviceParams()` return. `head_view = GetPose()` where `GetPose` does `CardboardHeadTracker_getPose(head_tracker, GetBootTimeNano()+50_000_000, kLandscapeLeft, out_position[3], out_orientation[4])` then builds a 4×4. `glBindFramebuffer(GL_FRAMEBUFFER, framebuffer_)`; enable depth/cull/blend; `glClear`. `for (eye in {kLeft,kRight}) { glViewport(eye==kLeft ? 0 : w/2, 0, w/2, h); eye_view = eye_matrices[eye] * head_view; mvp = projection_matrices[eye] * eye_view; drawSphere(mvp); }`. Then `CardboardDistortionRenderer_renderEyeToDisplay(dr, /*target=*/0, 0, 0, screen_width, screen_height, &left_eye_texture_description, &right_eye_texture_description)`.
- **Lifecycle:** `OnPause → CardboardHeadTracker_pause(head_tracker)`; `OnResume → CardboardHeadTracker_resume(head_tracker)`; dtor → `CardboardHeadTracker_destroy`, `CardboardLensDistortion_destroy`, `CardboardDistortionRenderer_destroy`.
- **QR profile:** `CardboardQrCode_scanQrCodeAndSaveDeviceParams()` (launches the SDK's own scan flow, saves params); next `UpdateDeviceParams` picks them up (the device-params-changed flag). `CardboardQrCode_getDeviceParamsChangedCount()` can detect change.

### Build/run discipline (critical)
- Gradle/AGP builds run via context-mode `ctx_execute(language:"shell", ...)`, **NOT** the Bash tool (a hook redirects gradle). `git`/`grep`/`ls`/file ops use Bash.
- After edits rebuild with `--rerun-tasks` (config cache can mark `assembleDebug` UP-TO-DATE without repackaging the APK).
- Set NDK + CMake explicitly so AGP finds the installed toolchain at `/home/farid/android-sdk` (not the default `~/Android/Sdk`). Use `android.ndkVersion = "29.0.14206865"` and `externalNativeBuild.cmake.version = "3.22.1"`. Builds need `ANDROID_HOME=/home/farid/android-sdk` in the env.

---

## File Structure

- **Create** `android/libs/cardboard/sdk-release.aar` — vendored prebuilt Cardboard AAR (copied from the spike). Committed binary (see Task 1).
- **Create** `android/src/main/cpp/cardboard.h` — vendored C header (copied from `sdk/include/cardboard.h`).
- **Create** `android/src/main/cpp/jni/arm64-v8a/libGfxPluginCardboard.so` — extracted native lib the CMake target links against.
- **Create** `android/src/main/cpp/CMakeLists.txt` — our AGP-9 CMake module producing `libcardboard_jni.so`, linking `libGfxPluginCardboard.so` + `GLESv2`/`GLESv3`/`EGL`/`android`/`log`.
- **Create** `android/src/main/cpp/cardboard_jni.cc` — JNI bridge (mirrors `hello_cardboard_jni.cc`).
- **Create** `android/src/main/cpp/cardboard_renderer.cc` / `cardboard_renderer.h` — the `CardboardRenderer` C++ class (mirrors `HelloCardboardApp`).
- **Create** `android/src/main/cpp/sphere_mesh.cc` / `sphere_mesh.h` — equirect UV sphere generator + `samplerExternalOES` shader program.
- **Create** `android/src/main/kotlin/com/panorama/android/gl/CardboardVrView.kt` — `GLSurfaceView` subclass with native method declarations, OES `SurfaceTexture`/`Surface` creation, lifecycle, `scanQrCode()`, visibility-tied render loop.
- **Modify** `android/build.gradle.kts` — `externalNativeBuild`, `ndkVersion`, `ndk { abiFilters += "arm64-v8a" }`, `-DANDROID_STL=c++_shared`.
- **Modify** `app/build.gradle.kts` — `ndkVersion`, AAR file dependency.
- **Modify** `app/src/main/kotlin/com/panorama/app/player/PlayerScreen.kt` — VR branch hosts `CardboardVrView`; lifecycle via `cardboardViewRef`.
- **Modify** `app/src/main/kotlin/com/panorama/app/player/PlayerControls.kt` — "Set up headset (QR)" control → `onScanQrCode`.
- **Modify** `app/src/main/AndroidManifest.xml` — `<uses-permission android:name="android.permission.CAMERA"/>` (+ fallback `QrCodeCaptureActivity`).
- **Create** `android/src/main/cpp/README.md` — provenance + rebuild instructions for the AAR/.so/header.
- **NOT touched / NOT deleted (this plan):** `PanoramaGlView.kt`, `PanoramaRenderer.kt`, `StereoEyeLayout` (`:core`), `GazePredictor`, VR shaders, `OrientationEngine.kt`, `ArrowOverlay.kt`, `SphericalPanoramaView.kt`, `ExoVideoPlayer.kt`, `PlayerViewModel.kt`, all `:core`.

> **Test note:** Native C and `GLSurfaceView` GL-thread behavior are not unit-testable under Robolectric. Verification for Tasks 3–9 is therefore **build-passes** (the `:android`/`:app` assemble with the native module) plus the on-device smoke tests in Task 8. Kotlin glue that *can* be tested without GL (e.g. the `CardboardVrView` lifecycle flag transitions) gets a Robolectric test where practical; otherwise the milestone is the green build + device check.

---

### Task 1: Vendor the prebuilt Cardboard AAR, header, and native lib

Bring the spike artifacts into the project tree so the build does not depend on the external `cardboard-spike` checkout.

**Files:**
- Create: `android/libs/cardboard/sdk-release.aar`
- Create: `android/src/main/cpp/cardboard.h`
- Create: `android/src/main/cpp/jni/arm64-v8a/libGfxPluginCardboard.so`
- Create: `android/src/main/cpp/README.md`

- [ ] **Step 1: Create vendor dirs** (Bash): `mkdir -p android/libs/cardboard android/src/main/cpp/jni/arm64-v8a`
- [ ] **Step 2: Copy AAR + header:** `cp /home/farid/sandbox/misc/cardboard-spike/sdk/build/outputs/aar/sdk-release.aar android/libs/cardboard/sdk-release.aar` and `cp /home/farid/sandbox/misc/cardboard-spike/sdk/include/cardboard.h android/src/main/cpp/cardboard.h`
- [ ] **Step 3: Extract arm64 `.so`:** `cd android/src/main/cpp && unzip -o ../../../libs/cardboard/sdk-release.aar 'jni/arm64-v8a/libGfxPluginCardboard.so' -d .` Verify with `file jni/arm64-v8a/libGfxPluginCardboard.so` → `ELF 64-bit ... ARM aarch64`.
- [ ] **Step 4: Write `README.md`** documenting provenance + rebuild (`cd /home/farid/sandbox/misc/cardboard-spike && ./gradlew :sdk:assembleRelease`), NDK `29.0.14206865`, CMake `3.22.1`, v1.34.0.
- [ ] **Step 5: Commit decision** — stage the vendored binaries (do not commit unless user asks; global commit policy).

**Verify (Bash):** all four files exist; `unzip -l android/libs/cardboard/sdk-release.aar | grep -E 'classes.jar|libGfxPluginCardboard.so'`; `grep -c CardboardHeadTracker_create android/src/main/cpp/cardboard.h` ≥ 1.

---

### Task 2: Enable NDK/CMake in the build and consume the AAR (no native code yet → provable build milestone)

**Design note:** A `com.android.library` module (`:android`) declaring an AAR-inside-AAR file dependency is historically fragile. Robust pattern: declare the Cardboard AAR as a dependency of **`:app`** (so its `classes.jar` + `.so` are packaged into the APK), while the **native CMake module lives in `:android`** and links the `.so` from the Task-1 path. **This plan uses that split.** Fallback: `implementation(files("libs/cardboard/sdk-release.aar"))` in `:android`.

**Files:** Modify `app/build.gradle.kts`, `android/build.gradle.kts`

- [ ] **Step 1:** In `:app` dependencies add `implementation(files("../android/libs/cardboard/sdk-release.aar"))` (file dependency — simplest, bypasses flat-dir metadata). If `settings.gradle.kts` sets `repositoriesMode = FAIL_ON_PROJECT_REPOS`, no repo change is needed for a file dependency.
- [ ] **Step 2:** Pin `ndkVersion = "29.0.14206865"` in BOTH modules' `android { }`. Add `ndk { abiFilters += "arm64-v8a" }` to `:android`'s `defaultConfig` (`:app` already has it).
- [ ] **Step 3:** Ensure AGP finds NDK at `/home/farid/android-sdk/ndk/29.0.14206865` (check `local.properties` `sdk.dir`; else build with `ANDROID_HOME=/home/farid/android-sdk`). No CMake module yet.
- [ ] **Step 4: Build:** `ctx_execute(shell): cd <proj> && ANDROID_HOME=/home/farid/android-sdk ./gradlew :app:assembleDebug --rerun-tasks 2>&1 | tail -40`

**Verify:** `BUILD SUCCESSFUL`; `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep libGfxPluginCardboard.so` → `lib/arm64-v8a/libGfxPluginCardboard.so`.

---

### Task 3: Add the CMake native module + a no-op JNI bridge (native compiles + loads)

**Files:** Create `android/src/main/cpp/CMakeLists.txt`, `cardboard_jni.cc`; Modify `android/build.gradle.kts`.

- [ ] **Step 1: `CMakeLists.txt`** (Task 3 includes only `cardboard_jni.cc`; add `cardboard_renderer.cc`/`sphere_mesh.cc` in Task 5):
  ```cmake
  cmake_minimum_required(VERSION 3.22.1)
  project(cardboard_jni VERSION 1.0.0 LANGUAGES CXX)
  set(CMAKE_CXX_STANDARD 17)
  set(CMAKE_CXX_STANDARD_REQUIRED True)
  add_compile_options(-Wall -Wextra)
  find_library(ANDROID_LIB android)
  find_library(GLESv2_LIB GLESv2)
  find_library(GLESv3_LIB GLESv3)
  find_library(EGL_LIB EGL)
  find_library(LOG_LIB log)
  set(CARDBOARD_LIB ${CMAKE_CURRENT_SOURCE_DIR}/jni/${ANDROID_ABI}/libGfxPluginCardboard.so)
  add_library(cardboard_jni SHARED cardboard_jni.cc)
  target_include_directories(cardboard_jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
  target_link_libraries(cardboard_jni ${ANDROID_LIB} ${GLESv2_LIB} ${GLESv3_LIB} ${EGL_LIB} ${LOG_LIB} ${CARDBOARD_LIB})
  ```
- [ ] **Step 2: No-op `cardboard_jni.cc`** with `JNI_OnLoad` capturing `JavaVM*` + `nativeOnCreate`/`nativeOnDestroy` stubs. Mangle for package `com.panorama.android.gl.CardboardVrView`:
  ```cpp
  #include <jni.h>
  #include <android/log.h>
  #define JNI_METHOD(rt, name) JNIEXPORT rt JNICALL Java_com_panorama_android_gl_CardboardVrView_##name
  static JavaVM* g_vm = nullptr;
  extern "C" {
  JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) { g_vm = vm; return JNI_VERSION_1_6; }
  JNI_METHOD(jlong, nativeOnCreate)(JNIEnv*, jobject, jobject) { return 0; }
  JNI_METHOD(void, nativeOnDestroy)(JNIEnv*, jobject, jlong) {}
  }
  ```
- [ ] **Step 3: Wire `externalNativeBuild` in `android/build.gradle.kts`:**
  ```kotlin
  android {
      defaultConfig { externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_shared" } } }
      externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
  }
  ```
- [ ] **Step 4: Build** (`:app:assembleDebug --rerun-tasks`, with `ANDROID_HOME`).

**Verify:** `BUILD SUCCESSFUL`; `unzip -l .../app-debug.apk | grep -E 'lib/arm64-v8a/(libcardboard_jni|libGfxPluginCardboard|libc++_shared).so'` → all three present.

---

### Task 4: `CardboardVrView` Kotlin shell + black-screen Cardboard init (no video)

**Files:** Create `android/src/main/kotlin/com/panorama/android/gl/CardboardVrView.kt`; Modify `PlayerScreen.kt`.

- [ ] **Step 1: `CardboardVrView.kt`** — `GLSurfaceView` subclass; `companion object { init { System.loadLibrary("cardboard_jni") } }`; native decls `nativeOnCreate(context)/nativeOnDestroy/nativeOnSurfaceCreated/nativeSetScreenParams/nativeOnDrawFrame/nativeSetOesTextureId/nativeOnPause/nativeOnResume/nativeScanQrCode`; `nativeApp = nativeOnCreate(activityContext)` in `init` (unwrap Activity via `context.findActivity()`); `setEGLContextClientVersion(2)`; `setRenderer` routing to native; visibility-tied loop (`RENDERMODE_WHEN_DIRTY` in init; `onResume → RENDERMODE_CONTINUOUSLY + queueEvent{nativeOnResume}`; `onPause → WHEN_DIRTY + queueEvent{nativeOnPause}`); `onDestroy()`, `scanQrCode()`; `var onVideoSurfaceReady/onVideoSurfaceDestroyed`.
- [ ] **Step 2:** Renderer black-screen-safe (`OnDrawFrame` clears black until profile exists — full impl Task 5; do Task 5 before hosting if sequential).
- [ ] **Step 3: Host in `PlayerScreen.kt` VR branch** — replace `PanoramaGlView` factory with `CardboardVrView(ctx).apply { cardboardViewRef = this; onVideoSurfaceReady = { viewModel.attachVideoSurface(it) } }`; add `var cardboardViewRef by remember { mutableStateOf<CardboardVrView?>(null) }`; `update = { it.onResume() }`, `onRelease = { it.onDestroy(); cardboardViewRef = null }`; forward `ON_RESUME/ON_PAUSE` to `cardboardViewRef`. Drop `bindGazeRef`/`setVrEnabled`.
- [ ] **Step 4: Build.**

**Verify:** build green. Device (defer to Task 8): VR-on shows black split view, no crash, native init log, no `UnsatisfiedLinkError`.

---

### Task 5: Native equirect sphere renderer + per-eye render + distortion (the core)

**Files:** Create `cardboard_renderer.{h,cc}`, `sphere_mesh.{h,cc}`; Modify `cardboard_jni.cc`, `CMakeLists.txt`.

- [ ] **Step 1: `sphere_mesh.{h,cc}`** — UV sphere (64×32), inward normals, equirect UVs (`u=lon/2π`, `v=lat/π`), index buffer; `samplerExternalOES` shader (`#extension GL_OES_EGL_image_external : require`); `CompileProgram` helper.
- [ ] **Step 2: `cardboard_renderer.h`** — class mirroring `HelloCardboardApp`: head tracker / lens distortion / distortion renderer / FBO / `oes_texture_id_` / sphere VBO/IBO+program / `eye_matrices_[2][16]` / `projection_matrices_[2][16]` / `st_matrix_[16]` / screen dims / changed flags. Methods: ctor(`JavaVM*, jobject`), dtor, `OnSurfaceCreated/SetScreenParams/SetOesTextureId/OnDrawFrame/OnPause/OnResume/ScanQrCode`, private `UpdateDeviceParams/GlSetup/GlTeardown/GetPose/DrawSphere`.
- [ ] **Step 3: `cardboard_renderer.cc`** — implement the verified C sequence (see Background → C API call sequence). ctor: `Cardboard_initializeAndroid` + `CardboardHeadTracker_create` + `setLowPassFilter(6)`. `OnSurfaceCreated`: compile program, build+upload sphere (OES id supplied by Kotlin). `UpdateDeviceParams`: getSavedDeviceParams → size==0 return false → else lens distortion create + GlSetup + distortion renderer (`kGlTexture2D`) + per-eye mesh/matrices. `GlSetup`: wide `GL_TEXTURE_2D` FBO + depth RB; eye descriptions `{tex,0,0.5,1,0}`/`{tex,0.5,1,1,0}`. `GetPose`: `getPose(..., GetBootTimeNano()+50ms, kLandscapeLeft, ...)` → 4×4. `OnDrawFrame`: `if(!UpdateDeviceParams){clear black;return}` → bind FBO → per-eye viewport+mvp+DrawSphere → `renderEyeToDisplay`. `DrawSphere`: bind `GL_TEXTURE_EXTERNAL_OES` (`#include <GLES2/gl2ext.h>`), set `u_MVP`/`u_StMatrix`, draw.
- [ ] **Step 4: Route JNI** in `cardboard_jni.cc` — `nativeOnCreate` returns `new CardboardRenderer(g_vm, contextObj)`; dispatch the rest; add `nativeSetOesTextureId`, `nativeSetStMatrix` (Task 6), `nativeScanQrCode`.
- [ ] **Step 5: Build.**

**Verify:** `BUILD SUCCESSFUL`; both `.so` in APK. (Visual → Task 8.)

---

### Task 6: Feed the single ExoPlayer video into the view (one decoder → one OES texture, both eyes)

**Files:** Modify `CardboardVrView.kt`, `cardboard_jni.cc`.

- [ ] **Step 1:** In `Renderer.onSurfaceCreated`, after `nativeOnSurfaceCreated`, gen OES texture (Kotlin `GLES20.glGenTextures` + `GLES11Ext.GL_TEXTURE_EXTERNAL_OES`, `GL_LINEAR`/`GL_CLAMP_TO_EDGE`), `surfaceTexture = SurfaceTexture(id)`, `nativeSetOesTextureId(nativeApp, id)`, `setOnFrameAvailableListener { requestRender() }`, post `onVideoSurfaceReady?.invoke(Surface(surfaceTexture))`.
- [ ] **Step 2:** In `Renderer.onDrawFrame`, before `nativeOnDrawFrame`: `surfaceTexture?.let { it.updateTexImage(); it.getTransformMatrix(stMatrix) }`; `nativeSetStMatrix(nativeApp, stMatrix)`. `DrawSphere` applies `u_StMatrix` to UVs.
- [ ] **Step 3:** Surface teardown → `onVideoSurfaceDestroyed?.invoke()` + release `SurfaceTexture`; wire `onVideoSurfaceDestroyed = { viewModel.attachVideoSurface(null) }` in Task 7.
- [ ] **Step 4: Build.**

**Verify:** build green. Device (Task 8): with saved profile, live 360 video split into two eyes; pause freezes frame while head-look still updates.

---

### Task 7: Finalize `PlayerScreen` VR-branch wiring + lifecycle parity

**Files:** Modify `PlayerScreen.kt`.

- [ ] **Step 1:** VR factory: `CardboardVrView` with `cardboardViewRef`, `onVideoSurfaceReady`, `onVideoSurfaceDestroyed = { viewModel.attachVideoSurface(null) }`. No `bindGazeRef`/`setVrEnabled`.
- [ ] **Step 2:** `update = { it.onResume() }`, `onRelease = { it.onDestroy(); cardboardViewRef = null }`.
- [ ] **Step 3:** Observer forwards `ON_RESUME → startSensor(); cardboardViewRef?.onResume()`, `ON_PAUSE → cardboardViewRef?.onPause(); stopSensor()` (keep sensor for arrow per OPEN QUESTION 1a).
- [ ] **Step 4:** `ArrowOverlay` unchanged.
- [ ] **Step 5: Build.**

**Verify:** build green; VR↔mono swap detaches/attaches surface cleanly (logcat shows `setVideoSurface(null)` then new surface).

---

### Task 8: On-device smoke test (user-run)

- [ ] **Step 1: Install:** `ctx_execute(shell): cd <proj> && ANDROID_HOME=/home/farid/android-sdk ./gradlew :app:installDebug --rerun-tasks 2>&1 | tail -20`
- [ ] **Step 2 (user):** Open 360 clip, toggle VR On → split-screen stereo, live video both eyes, head-look via Cardboard, lens distortion. No profile → black until Task 9 QR.
- [ ] **Step 3 (user):** Pause → frame stays, head-look updates; background/foreground → no crash, resume; VR off → mono resumes.
- [ ] **Step 4:** `adb logcat -d -t 300 | grep -iE 'cardboard|UnsatisfiedLink|GLError|libGfxPlugin'`.

**Verify:** behaviors hold; no native crash.

---

### Task 9: QR viewer-profile flow (gear/scan control)

**Files:** Modify `PlayerControls.kt`, `PlayerScreen.kt`, `AndroidManifest.xml`.

- [ ] **Step 1:** Add `<uses-permission android:name="android.permission.CAMERA"/>`.
- [ ] **Step 2:** `PlayerControls` gets `onScanQrCode: () -> Unit` + a `FilledTonalButton("Set up headset")`.
- [ ] **Step 3:** `PlayerScreen` passes `onScanQrCode = { cardboardViewRef?.scanQrCode() }` → `nativeScanQrCode → CardboardQrCode_scanQrCodeAndSaveDeviceParams()`. Add `getDeviceParamsChangedCount()` check at top of `UpdateDeviceParams`.
- [ ] **Step 4 (fallback, OPEN QUESTION 2):** if native scan won't launch, declare `QrCodeCaptureActivity` in manifest + add `androidx.appcompat:appcompat`, launch via Intent.
- [ ] **Step 5: Build.**

**Verify:** build green. Device (user): scan QR → black eyes now show distorted stereo video.

---

### Task 10 (DEFERRED — OPEN QUESTION 1b): Arrow gaze from Cardboard pose

Only if user wants the arrow to track Cardboard pose instead of `gazeRef`.

- [ ] Add `nativeGetPoseYawDeg(p): Float`; expose thread-safe getter; feed `ArrowOverlay` in VR from it; keep mono on `gazeRef`; verify arrow consistency on-device.

---

### Task 11 (OPTIONAL cleanup, after Cardboard verified): retire the custom VR GL stack

Only after Tasks 8–9 pass on-device and user confirms Cardboard is the sole VR path.

- [ ] `rg PanoramaGlView|PanoramaRenderer|StereoEyeLayout|GazePredictor`; delete the VR-only stack + tests; keep `EquirectProjection` if mono still uses it; run `:core:test :android:test :app:test :app:assembleDebug`.

---

## Verification summary (per milestone)

| Task | Provable check |
|---|---|
| 1 | Vendored files exist; AAR lists `classes.jar` + arm64 `.so`; header has Cardboard symbols |
| 2 | `:app:assembleDebug` green; `libGfxPluginCardboard.so` in APK |
| 3 | green; `libcardboard_jni.so` + `libGfxPluginCardboard.so` + `libc++_shared.so` in APK; no `UnsatisfiedLinkError` |
| 4 | green; device: black split view, no crash, native init log |
| 5 | green (visual deferred to 8) |
| 6 | green; device: live stereo video, both eyes, one decoder |
| 7 | green; VR↔mono swap detaches/attaches surface cleanly |
| 8 | device: stereo + head-look + distortion + lifecycle parity |
| 9 | device: QR scan saves profile, eyes show distorted video |
