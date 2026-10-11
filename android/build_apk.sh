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

GRADLE="sh ./gradlew"
if [ -f "./gradlew" ]; then
    GRADLE="sh ./gradlew"
elif command -v gradle >/dev/null 2>&1; then
    GRADLE="gradle"
else
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
GRADLE_LOG="$PWD/gradle-build.log"
if ! "$GRADLE" "$TASK" --stacktrace 2>&1 | tee "$GRADLE_LOG"; then
    echo ""
    echo "==> Gradle failed, last lines of the log:"
    tail -n 60 "$GRADLE_LOG"
    # In GitHub Actions surface the tail as annotations so it is
    # visible without downloading the full log bundle.
    if [ "$GITHUB_ACTIONS" = "true" ]; then
        n=0
        while [ "$n" -lt 200 ]; do
            echo "::error title=Gradle build failed::$(tail -n 200 "$GRADLE_LOG" | sed -n "$((n + 1)),$((n + 20))p" | tr '\n' '|' | cut -c1-4000)"
            n=$((n + 20))
        done
    fi
    exit 1
fi

APK_DIR="app/build/outputs/apk/$BUILD_TYPE"
echo ""
echo "==> Done. APK(s) in android/$APK_DIR:"
ls -la "$APK_DIR"/*.apk 2>/dev/null || true
