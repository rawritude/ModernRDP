# ModernRDP

A modern Android RDP (Remote Desktop Protocol) client built with Kotlin, Jetpack Compose, and Material 3. Designed with first-class support for foldable devices like Samsung Galaxy Fold.

## Architecture

- **UI**: Jetpack Compose + Material 3 (Material You dynamic theming)
- **RDP Engine**: FreeRDP via JNI (Apache 2.0)
- **Language**: 100% Kotlin (app) + C (native JNI bridge)
- **Architecture**: MVVM + Clean Architecture
- **DI**: Hilt
- **Navigation**: Compose Navigation
- **Storage**: Room (connections + groups) with encrypted credentials
- **Security**: Android Keystore (AES-256-GCM) for credential encryption
- **Foldable Support**: Jetpack WindowManager

## Features

- **Modern Material 3 UI** with dynamic color (Material You) and dark mode
- **Foldable-first design** — adaptive layouts for cover screen, inner screen, and tabletop mode
- **Dynamic resolution** — automatically adjusts RDP resolution when folding/unfolding
- **Connection manager** — save, organize, and quick-connect to servers
- **Connection groups** — organize servers into collapsible folders
- **Credential encryption** — passwords secured with AES-256-GCM via Android Keystore
- **Session persistence** — foreground service keeps RDP sessions alive when backgrounded
- **Full RDP support** — NLA, TLS, RDP Gateway, multi-touch, keyboard input
- **Pinch-to-zoom** — smooth zoom and pan during remote sessions
- **.rdp file import** — open standard RDP connection files

## Project Structure

```
app/src/main/
├── cpp/                                 # Native C code (FreeRDP JNI bridge)
│   ├── CMakeLists.txt                   # NDK build config, links FreeRDP .so libs
│   ├── modernrdp_jni.c                  # JNI lifecycle, RDP thread, input events
│   ├── modernrdp_jni.h                  # Context struct, event types, queue API
│   ├── modernrdp_event.c                # Thread-safe event queue
│   ├── modernrdp_callback.c             # JNI callback dispatch (native → Kotlin)
│   └── modernrdp_callback.h             # Callback prototypes
├── java/com/modernrdp/
│   ├── ModernRdpApp.kt                  # Application class (Hilt entry)
│   ├── MainActivity.kt                  # Single activity, Compose host
│   ├── navigation/
│   │   └── NavGraph.kt                  # Compose Navigation routes
│   ├── data/
│   │   ├── model/
│   │   │   ├── RdpConnection.kt         # Connection entity (Room)
│   │   │   └── ConnectionGroup.kt       # Group/folder entity (Room)
│   │   ├── local/
│   │   │   ├── RdpDatabase.kt           # Room database (v2, with migrations)
│   │   │   ├── ConnectionDao.kt         # Connection data access
│   │   │   └── GroupDao.kt              # Group data access
│   │   ├── repository/
│   │   │   └── ConnectionRepository.kt  # Data layer with encrypt/decrypt
│   │   └── crypto/
│   │       └── CredentialEncryption.kt   # AES-256-GCM via Android Keystore
│   ├── rdp/
│   │   ├── FreeRdpBridge.kt             # JNI bridge to libfreerdp
│   │   └── FoldableDisplayHelper.kt     # Foldable display handling
│   ├── service/
│   │   └── RdpSessionService.kt         # Foreground service for session persistence
│   ├── ui/
│   │   ├── theme/
│   │   │   ├── Color.kt                 # Color palette
│   │   │   ├── Theme.kt                 # Material 3 theme (dynamic color)
│   │   │   └── Type.kt                  # Typography
│   │   ├── components/
│   │   │   └── ConnectionCard.kt        # Reusable connection card
│   │   └── screens/
│   │       ├── home/                     # Connection list with group headers
│   │       ├── editor/                   # Add/edit connection + group picker
│   │       └── session/                  # Active RDP session viewer
│   └── di/
│       └── AppModule.kt                 # Hilt dependency injection
└── jniLibs/
    ├── arm64-v8a/                        # Pre-built FreeRDP libs (arm64)
    └── x86_64/                           # Pre-built FreeRDP libs (x86_64)
```

## Building

### Prerequisites

- Android Studio Ladybug or later
- Android SDK 35
- NDK (for native C compilation)
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

### FreeRDP Native Libraries

The RDP engine requires pre-built FreeRDP shared libraries. Use the included setup script:

```bash
# Create jniLibs directory structure with instructions
./scripts/setup-freerdp.sh

# Or build FreeRDP from source automatically
./scripts/setup-freerdp.sh --build
```

**Manual setup:** Place the following `.so` files in `app/src/main/jniLibs/<abi>/`:
- `libfreerdp3.so`
- `libfreerdp-client3.so`
- `libwinpr3.so`
- `libssl.so`
- `libcrypto.so`

With headers in `jniLibs/<abi>/include/{freerdp3,winpr3,openssl}/`.

## Foldable Device Support

ModernRDP is designed for Samsung Galaxy Fold and other foldable devices:

- **Cover screen** (narrow, ~6.2"): Optimized connection list, adapted resolution
- **Inner screen** (tablet, ~7.6"): Full adaptive layout, split-view capable
- **Tabletop mode** (half-folded): Session on top half, controls on bottom
- **Dynamic resize**: RDP resolution renegotiated seamlessly on fold/unfold
- **DPI-aware scaling**: Smart resolution scaling so remote content stays readable on high-DPI screens (xxxhdpi 0.5x, xxhdpi 0.6x)

## Security

- Credentials are encrypted at rest using **AES-256-GCM** with keys stored in the **Android Keystore**
- Hardware-backed keys never leave the secure element
- Encrypted values are transparently handled — existing plaintext entries are auto-encrypted on next save
- TLS and NLA enabled by default for all connections

## License

Apache 2.0 (app code) — uses FreeRDP (Apache 2.0) as the RDP engine.

## Status

Active development. Core architecture, FreeRDP native integration, credential encryption, session persistence, and connection groups are implemented. Targeting first release with full RDP session support on foldable devices.
