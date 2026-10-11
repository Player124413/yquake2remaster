#!/bin/sh
#
# Build the Kingpin Android APK.
#
# Requirements:
#   - Android SDK + NDK (25+) + CMake 3.22+
#   - ANDROID_HOME (or ANDROID_SDK_ROOT) pointing at the SDK
#   - Java 17
#   - Gradle 8.7+ (or open android/ in Android Studio)
#   - run ./fetch_deps.sh first
#
# Usage:
#   ./build_apk.sh [debug|release]
#
set -e

BUILD_TYPE="${1:-debug}"

cd "$(dirname "$0")"

if [ ! -d SDL2 ]; then
    echo "SDL2 sources missing - run ./fetch_deps.sh first."
    exit 1
fi

if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    echo "Set ANDROID_HOME (or ANDROID_SDK_ROOT) to your Android SDK path."
    exit 1
fi

# local.properties for the Android Gradle plugin
SDK_DIR="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
echo "sdk.dir=$SDK_DIR" > local.properties

GRADLE="gradle"
if [ -x "./gradlew" ]; then
    GRADLE="./gradlew"
elif ! command -v gradle >/dev/null 2>&1; then
    echo "Gradle not found. Either install Gradle 8.7+ or open this"
    echo "directory in Android Studio (it will set up the wrapper)."
    exit 1
fi

case "$BUILD_TYPE" in
    release)
        TASK="assembleRelease"
        ;;
    *)
        TASK="assembleDebug"
        ;;
esac

echo "==> Building $TASK ..."
"$GRADLE" "$TASK"

APK_DIR="app/build/outputs/apk/$BUILD_TYPE"
echo ""
echo "==> Done. APK(s) in android/$APK_DIR:"
ls -la "$APK_DIR"/*.apk 2>/dev/null || true
