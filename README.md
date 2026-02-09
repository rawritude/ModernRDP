# ModernRDP

A modern Android RDP (Remote Desktop Protocol) client built with Kotlin, Jetpack Compose, and Material 3. Designed with first-class support for foldable devices like Samsung Galaxy Fold.

## Architecture

- **UI**: Jetpack Compose + Material 3 (Material You dynamic theming)
- **RDP Engine**: FreeRDP via `freeRDPCore` (Apache 2.0)
- **Language**: 100% Kotlin
- **Architecture**: MVVM + Clean Architecture
- **DI**: Hilt
- **Navigation**: Compose Navigation
- **Storage**: Room (connections) + DataStore (preferences)
- **Foldable Support**: Jetpack WindowManager + Material 3 Adaptive

## Features

- **Modern Material 3 UI** with dynamic color (Material You) and dark mode
- **Foldable-first design** — adaptive layouts for cover screen, inner screen, and tabletop mode
- **Dynamic resolution** — automatically adjusts RDP resolution when folding/unfolding
- **Connection manager** — save, organize, and quick-connect to servers
- **Full RDP support** — NLA, TLS, RDP Gateway, multi-touch, keyboard input
- **Pinch-to-zoom** — smooth zoom and pan during remote sessions
- **.rdp file import** — open standard RDP connection files

## Project Structure

```
app/src/main/java/com/modernrdp/
├── ModernRdpApp.kt              # Application class (Hilt entry)
├── MainActivity.kt              # Single activity, Compose host
├── navigation/
│   └── NavGraph.kt              # Compose Navigation routes
├── data/
│   ├── model/
│   │   └── RdpConnection.kt     # Connection entity
│   ├── local/
│   │   ├── RdpDatabase.kt       # Room database
│   │   └── ConnectionDao.kt     # Data access object
│   └── repository/
│       └── ConnectionRepository.kt
├── rdp/
│   ├── FreeRdpBridge.kt         # JNI bridge to libfreerdp
│   └── FoldableDisplayHelper.kt # Foldable display handling
├── ui/
│   ├── theme/
│   │   ├── Color.kt             # Color palette
│   │   ├── Theme.kt             # Material 3 theme (dynamic color)
│   │   └── Type.kt              # Typography
│   ├── components/
│   │   └── ConnectionCard.kt    # Reusable connection card
│   └── screens/
│       ├── home/                 # Connection list (home screen)
│       ├── editor/               # Add/edit connection
│       └── session/              # Active RDP session viewer
└── di/
    └── AppModule.kt             # Hilt dependency injection
```

## Building

### Prerequisites

- Android Studio Ladybug or later
- Android SDK 35
- JDK 17

### Steps

```bash
# Clone
git clone <repo-url>
cd ModernRDP

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

### FreeRDP Native Library

The RDP engine uses FreeRDP's native library. To build from source:

1. Clone [FreeRDP](https://github.com/FreeRDP/FreeRDP)
2. Follow the [Android build instructions](https://github.com/FreeRDP/FreeRDP/blob/master/docs/README.android)
3. Copy the built `.so` files to `app/src/main/jniLibs/`

Or use pre-built binaries from the FreeRDP releases.

## Foldable Device Support

ModernRDP is designed for Samsung Galaxy Fold and other foldable devices:

- **Cover screen** (narrow, ~6.2"): Optimized connection list, adapted resolution
- **Inner screen** (tablet, ~7.6"): Full adaptive layout, split-view capable
- **Tabletop mode** (half-folded): Session on top half, controls on bottom
- **Dynamic resize**: RDP resolution renegotiated seamlessly on fold/unfold
- **DPI-aware scaling**: Smart resolution scaling so remote content stays readable on high-DPI screens

## License

Apache 2.0 (app code) — uses FreeRDP (Apache 2.0) as the RDP engine.

## Status

Early development. The UI scaffold and architecture are in place. FreeRDP native integration is stubbed and ready to be connected.
