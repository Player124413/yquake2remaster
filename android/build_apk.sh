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

if [ -f "./gradlew" ]; then
    run_gradle() { sh ./gradlew "$@"; }
elif command -v gradle >/dev/null 2>&1; then
    run_gradle() { gradle "$@"; }
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
set +e
run_gradle "$TASK" --stacktrace > "$GRADLE_LOG" 2>&1
GRADLE_STATUS=$?
set -e
cat "$GRADLE_LOG"

if [ "$GRADLE_STATUS" -ne 0 ]; then
    echo ""
    echo "==> Gradle failed (exit $GRADLE_STATUS), last lines of the log:"
    tail -n 60 "$GRADLE_LOG"
    # In GitHub Actions surface the important lines as annotations so
    # they are visible without downloading the full log bundle.
    if [ "$GITHUB_ACTIONS" = "true" ]; then
        # Signal lines first (CMake/compiler errors, root causes).
        grep -n -E 'CMake Error|error:|Error:|What went wrong|Caused by|fatal|undefined' "$GRADLE_LOG" | head -n 8 | while IFS= read -r line; do
            echo "::error title=Gradle failure detail::$(printf '%s' "$line" | tr '\n' ' ' | cut -c1-400)"
        done
        echo "::error title=Gradle log tail (see android/gradle-build.log artifact)::$(tail -n 40 "$GRADLE_LOG" | tr '\n' '|' | cut -c1-3000)"
    fi
    exit 1
fi

APK_DIR="app/build/outputs/apk/$BUILD_TYPE"
echo ""
echo "==> Done. APK(s) in android/$APK_DIR:"
ls -la "$APK_DIR"/*.apk 2>/dev/null || true
