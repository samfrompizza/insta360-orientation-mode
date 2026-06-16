# Panorama 360 Viewer

An Android app for viewing 360° panoramic video — from a file or live from an Insta360 camera —
with gyroscope-steered orientation, a split-screen Cardboard VR mode, and an off-screen detection
arrow that points toward drones flagged in a sidecar file.

## Features

- **360 video playback** — open an equirectangular clip; mono (full-screen) or split-screen VR.
- **Gyroscope orientation** — the view follows the phone's head pose; sensitivity is adjustable.
- **VR mode** — Google Cardboard SDK stereo with tunable per-eye size and gap.
- **Live camera preview** — connect to an Insta360 camera over Wi-Fi / USB / Bluetooth and watch the
  live panoramic stream, steered by the gyroscope.
- **Detection arrow** — when a detections sidecar (JSON from the offline CV pipeline) is loaded, an
  on-screen arrow points toward objects that are currently outside the field of view.
- **Manual orientation lock** — portrait/landscape toggle in mono (no auto-rotate); VR is landscape.

## Modules

- `:core` — pure-JVM math, orientation, detection model, FOV/arrow resolver (no Android deps; unit-tested).
- `:android` — Android layer: GL/Cardboard renderer (`CardboardVrView`), ExoPlayer wrapper, sensor
  engine, Insta360 camera connection.
- `:app` — Compose UI (library, player, live screens), Hilt graph, navigation.

## Build

```bash
./gradlew :app:assembleDebug          # build the debug APK
./gradlew :app:installDebug           # install on a connected device
./gradlew :core:test                  # pure-JVM unit tests
./gradlew :app:testDebugUnitTest      # app unit tests
```

`local.properties` must point `sdk.dir` at your Android SDK. The build targets `arm64-v8a`,
`compileSdk` 36, `minSdk` 29, Java/Kotlin 17. The native Cardboard renderer is built via CMake/NDK.

The Insta360 SDK resolves from `https://androidsdk.insta360.com/repository/maven-public/`
(configured in `settings.gradle.kts`).

## Documentation

Design specs and implementation plans live in `docs/superpowers/`.

## Verifying changes

Most features need real hardware: 360 playback and VR on a phone, live preview and capture on an
Insta360 camera. CI (`.github/workflows/ci.yml`) runs the `:core` tests and an app assemble on every
pull request and on pushes to `master`.
