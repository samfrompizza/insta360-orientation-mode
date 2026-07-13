# Insta360 SDK Demo — 360° Camera App

Android application built on the [Insta360 SDK](https://www.insta360.com/) for live preview, capture, and playback of 360° spherical video with VR headset support.

## Features

- **Live Camera Preview** — connect to Insta360 cameras via WiFi/USB and stream 360° video in real time
- **VR Mode** — stereoscopic view for VR headsets (Google Cardboard compatible) with gyroscope-based head tracking
- **Offline Playback** — play back recorded 360° footage with directional arrows pointing to detected objects (CV sidecar)
- **Gyroscope Orientation** — quaternion-based sensor fusion for smooth head tracking in both mono and VR modes
- **Modular Architecture** — Hilt DI, clean architecture with UseCases, feature modules (`:feature:capture`, `:feature:player`, etc.)

## Tech Stack

- **Language:** Kotlin, Java
- **UI:** Android Views (XML), Material Design
- **DI:** Hilt
- **Player:** Media3 ExoPlayer 1.5.1 for offline spherical playback
- **Math:** Quaternion-based sensor fusion (`:core:sensor-fusion`), equirectangular projection (`:core:math`)
- **Build:** Gradle 8.13, AGP 8.12.3, Kotlin 2.0.21, NDK 25.2

## Requirements

- Android device with `arm64-v8a` (ARMv8) architecture
- Insta360 camera (tested with ONE RS/X series)
- WiFi or USB connection to camera

## Build

```bash
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew assembleDebug
```

## Architecture

| Layer | Modules | Purpose |
|-------|---------|---------|
| **App** | `:app` | Activities, layouts, resources |
| **Features** | `:feature:capture`, `:feature:player`, `:feature:connect`, `:feature:settings`, `:feature:shot` | Screen-level UI + ViewModels |
| **Domain** | `:domain` | Repository interfaces, model classes |
| **Data** | `:data:camera`, `:data:media`, `:data:sensor` | Repository implementations, UseCases |
| **Core** | `:core:base`, `:core:math`, `:core:sensor-fusion`, `:core:vr`, `:core:detection` | Shared utilities, math, VR engine |

## License

Proprietary — depends on Insta360 SDK (private repository).
