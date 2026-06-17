# Native VR (Google Cardboard SDK)

This directory holds the JNI bridge + GL renderer that drive the VR (split-screen
stereo) path, built on the open-source Google Cardboard SDK (`github.com/googlevr/cardboard`,
v1.34.0, Apache-2.0 — NOT the deprecated `com.google.vr`/GvrLayout).

## Vendored artifacts

- `../../../libs/cardboard/sdk-release.aar` — the prebuilt Cardboard SDK AAR. Consumed by `:app`
  as `implementation(files(...))` so its `classes.jar` (QR/device-params Java layer) and
  `jni/arm64-v8a/libGfxPluginCardboard.so` are packaged into the APK.
- `cardboard.h` — the Cardboard C API header (copy of `sdk/include/cardboard.h` from the SDK).
- `jni/arm64-v8a/libGfxPluginCardboard.so` — extracted from the AAR; the CMake target links it.

All three are copies of Cardboard SDK v1.34.0 artifacts.

## Rebuilding the AAR / .so

The SDK is not on Maven. To regenerate the artifacts (e.g. to update the SDK version):

```bash
git clone https://github.com/googlevr/cardboard.git
cd cardboard
# requires NDK 29.0.14206865 + CMake 3.22.1 + JDK 17
./gradlew :sdk:assembleRelease
# -> sdk/build/outputs/aar/sdk-release.aar
```

Then copy the AAR to `android/libs/cardboard/`, copy `sdk/include/cardboard.h` here, and extract
`jni/arm64-v8a/libGfxPluginCardboard.so` from the AAR into `jni/arm64-v8a/`.

## Toolchain

- NDK `29.0.14206865`, CMake `3.22.1` (installed under `$ANDROID_HOME`).
- The render/tracking API is pure native C (`CardboardHeadTracker_*`, `CardboardLensDistortion_*`,
  `CardboardDistortionRenderer_*`). The Java layer in the AAR only provides the QR viewer-profile
  flow + device/screen params. Our `cardboard_jni.cc` calls the C API; `CardboardVrView.kt` owns the
  `GLSurfaceView`, the OES `SurfaceTexture` (fed to ExoPlayer), and the lifecycle.
