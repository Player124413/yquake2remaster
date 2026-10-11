#!/bin/sh
#
# Fetch build dependencies for the Kingpin Android port.
#
# Downloads SDL2 (the only external dependency) from GitHub and copies
# its Android Java bootstrap into the app. Everything stays outside of
# Git (see .gitignore), so no third party code is vendored.
#
# Usage: ./fetch_deps.sh
#
set -e

SDL2_TAG="${SDL2_TAG:-release-2.32.10}"
SDL2_URL="${SDL2_URL:-https://codeload.github.com/libsdl-org/SDL/tar.gz/refs/tags/${SDL2_TAG}}"

cd "$(dirname "$0")"

if [ -d SDL2 ] && [ -f SDL2/CMakeLists.txt ]; then
    echo "SDL2 sources already present in android/SDL2, nothing to do."
    echo "Delete android/SDL2 and re-run to fetch again."
    exit 0
fi

echo "==> Downloading SDL2 (${SDL2_TAG})..."
TMP_TAR="$(mktemp /tmp/sdl2-XXXXXX.tar.gz)"
TMP_DIR="$(mktemp -d /tmp/sdl2-XXXXXX)"

trap 'rm -f "$TMP_TAR"; rm -rf "$TMP_DIR"' EXIT

curl -L -o "$TMP_TAR" "$SDL2_URL"
tar -xzf "$TMP_TAR" -C "$TMP_DIR"

EXTRACTED="$(find "$TMP_DIR" -maxdepth 1 -mindepth 1 -type d | head -n 1)"
mv "$EXTRACTED" SDL2

echo "==> Copying SDL2 Android Java bootstrap..."
mkdir -p app/src/main/java/org/libsdl/app
cp SDL2/android-project/app/src/main/java/org/libsdl/app/*.java \
   app/src/main/java/org/libsdl/app/

echo "==> Done. SDL2 is in android/SDL2 (not tracked by Git)."
