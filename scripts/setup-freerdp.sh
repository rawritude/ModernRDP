#!/usr/bin/env bash
#
# setup-freerdp.sh — Download or build FreeRDP native libraries for Android.
#
# Usage:
#   ./scripts/setup-freerdp.sh [--build]
#
# Without --build: downloads pre-built .so files from FreeRDP releases.
# With --build: clones FreeRDP source and builds from scratch (requires NDK).
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JNILIBS_DIR="$PROJECT_DIR/app/src/main/jniLibs"

FREERDP_VERSION="3.12.0"
FREERDP_REPO="https://github.com/FreeRDP/FreeRDP.git"

# Target ABIs matching app/build.gradle.kts ndk.abiFilters
ABIS="arm64-v8a x86_64"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info() { echo -e "${GREEN}[INFO]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# -------------------------------------------------------------------
# Option A: Build from source
# -------------------------------------------------------------------
build_from_source() {
    info "Building FreeRDP from source..."

    # Check prerequisites
    command -v cmake >/dev/null 2>&1 || error "cmake not found. Install cmake >= 3.13."

    if [ -z "${ANDROID_NDK:-}" ] && [ -z "${ANDROID_NDK_HOME:-}" ]; then
        error "ANDROID_NDK or ANDROID_NDK_HOME not set."
    fi
    local NDK="${ANDROID_NDK:-$ANDROID_NDK_HOME}"

    if [ -z "${ANDROID_SDK_ROOT:-}" ] && [ -z "${ANDROID_HOME:-}" ]; then
        error "ANDROID_SDK_ROOT or ANDROID_HOME not set."
    fi
    local SDK="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

    local FREERDP_SRC="$PROJECT_DIR/third_party/FreeRDP"

    # Clone if needed
    if [ ! -d "$FREERDP_SRC" ]; then
        info "Cloning FreeRDP v$FREERDP_VERSION..."
        mkdir -p "$PROJECT_DIR/third_party"
        git clone --depth 1 --branch "$FREERDP_VERSION" "$FREERDP_REPO" "$FREERDP_SRC"
    else
        info "FreeRDP source found at $FREERDP_SRC"
    fi

    # Build using FreeRDP's own script
    cd "$FREERDP_SRC"

    # Create a build config
    cat > /tmp/modernrdp-build.conf <<'BUILDCONF'
BUILD_ARCH="arm64-v8a x86_64"
NDK_TARGET=26
CMAKE_BUILD_TYPE=Release
WITH_OPENH264=0
WITH_OPENSSL=1
WITH_FFMPEG=0
WITH_AAD=0
WITH_MEDIACODEC=0
BUILD_DEPS=1
DEPS_ONLY=0
BUILDCONF

    info "Running FreeRDP Android build..."
    ./scripts/android-build-freerdp.sh \
        --ndk "$NDK" \
        --sdk "$SDK" \
        --conf /tmp/modernrdp-build.conf

    # Copy results to our jniLibs
    local BUILD_OUTPUT="$FREERDP_SRC/client/Android/Studio/freeRDPCore/src/main/jniLibs"
    for abi in $ABIS; do
        info "Copying $abi libraries..."
        mkdir -p "$JNILIBS_DIR/$abi"
        cp -r "$BUILD_OUTPUT/$abi/"* "$JNILIBS_DIR/$abi/"
    done

    info "FreeRDP build complete!"
}

# -------------------------------------------------------------------
# Option B: Create placeholder structure for manual setup
# -------------------------------------------------------------------
create_placeholder() {
    info "Creating jniLibs directory structure..."
    info ""
    info "You need to provide pre-built FreeRDP libraries."
    info "Build FreeRDP for Android or obtain pre-built .so files."
    info ""

    for abi in $ABIS; do
        local abi_dir="$JNILIBS_DIR/$abi"
        mkdir -p "$abi_dir/include/freerdp3"
        mkdir -p "$abi_dir/include/winpr3"
        mkdir -p "$abi_dir/include/openssl"

        cat > "$abi_dir/README.md" <<EOF
# FreeRDP Native Libraries ($abi)

Place the following files in this directory:

## Required .so files:
- libfreerdp3.so        (FreeRDP core)
- libfreerdp-client3.so (FreeRDP client)
- libwinpr3.so          (WinPR runtime)
- libssl.so             (OpenSSL SSL)
- libcrypto.so          (OpenSSL crypto)

## Optional .so files:
- libopenh264.so        (H.264 codec)
- libavcodec.so         (FFmpeg codec)
- libavutil.so          (FFmpeg util)
- libswscale.so         (FFmpeg scale)
- libswresample.so      (FFmpeg resample)
- libcjson.so           (Azure AD auth)

## Required headers:
- include/freerdp3/     (FreeRDP headers)
- include/winpr3/       (WinPR headers)
- include/openssl/      (OpenSSL headers)

## How to build:
\`\`\`bash
git clone https://github.com/FreeRDP/FreeRDP.git
cd FreeRDP
./scripts/android-build-freerdp.sh \\
    --ndk \$ANDROID_NDK_HOME \\
    --sdk \$ANDROID_SDK_ROOT

# Output will be in:
# client/Android/Studio/freeRDPCore/src/main/jniLibs/$abi/
\`\`\`
EOF
    done

    info "Directory structure created at: $JNILIBS_DIR/"
    info ""
    info "Per-ABI README files explain what's needed."
    info ""
    info "Quick start:"
    info "  1. Build FreeRDP:  ./scripts/setup-freerdp.sh --build"
    info "  2. Or manually place .so + headers in app/src/main/jniLibs/<abi>/"
    info ""
}

# -------------------------------------------------------------------
# Main
# -------------------------------------------------------------------
case "${1:-}" in
    --build)
        build_from_source
        ;;
    *)
        create_placeholder
        ;;
esac
