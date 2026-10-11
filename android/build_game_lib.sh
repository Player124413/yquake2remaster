#!/bin/sh
#
# Build the Kingpin game library (game.so / libgame.so) for Android from
# the official Kingpin SDK.
#
# !!! READ THIS FIRST !!!
#
# The Kingpin SDK is NOT free software. This script downloads it on YOUR
# machine, at YOUR request. Nothing from the SDK is stored in this
# repository. Make sure you are allowed to use and build the SDK (you
# need a legitimate copy of Kingpin: Life of Crime).
#
# How it works: the engine and the SDK game code share one ABI, defined
# by this repository's headers. kingpin_sdk_abi.patch brings the SDK
# headers to that ABI (configstring slots, entity_state_t/player_state_t
# extensions, game_import_t/game_export_t function slots, edict_s prefix)
# and fixes a few MSVC-isms in the SDK sources. The engine side of the
# ABI is implemented in this repository (see src/server/sv_game.c for
# the Kingpin engine imports and src/common/movemsg.c for the model part
# network sync). android/check_abi.sh verifies that both sides agree
# field for field.
#
# Known remaining limitations (all documented in doc/050_android.md):
#   * The Navigation (CNS) library shipped with the SDK is closed source
#     and only available as Windows .lib / Linux i386 .o. Android uses
#     the ABI-correct, fail-closed stubs in kingpin_nav_stubs.c; NPC route
#     finding is unavailable. GetGameAPI initializes the cvars game frames
#     expect, and missing functions no longer return indeterminate values.
#   * gi.GetObjectBounds() is a no-op in the engine: body part hit
#     detection has no object bounds to work with yet.
#   * gi.SaveCurrentGame() is a no-op: hired guys do not survive map
#     transitions.
#   * gi.skinindex() returns per model registration order, which may
#     differ from the skin order inside the MDX files, so some skins
#     can look wrong.
#
# Usage:
#   export ANDROID_NDK_HOME=/path/to/ndk
#   ./build_game_lib.sh
#
set -e

# In GitHub Actions, turn failure messages into visible annotations.
fail() {
    echo "!! $*"
    if [ "$GITHUB_ACTIONS" = "true" ]; then
        echo "::error title=Build Kingpin game library::$*"
    fi
    return 1
}

SDK_TAG="${SDK_TAG:-master}"
SDK_URL="${SDK_URL:-https://codeload.github.com/QuakeTools/Kingpin-SDK-v1.21/tar.gz/refs/heads/${SDK_TAG}}"

cd "$(dirname "$0")"
BUILD="$PWD/.kingpin-game-build"
OUT_JNI="$PWD/app/src/main/jniLibs"
PATCH="$PWD/kingpin_sdk_abi.patch"

if [ -z "$ANDROID_NDK_HOME" ] && [ -n "$ANDROID_HOME" ]; then
    for d in "$ANDROID_HOME"/ndk/*; do
        [ -d "$d" ] && ANDROID_NDK_HOME="$d"
    done
fi

if [ -z "$ANDROID_NDK_HOME" ] || [ ! -d "$ANDROID_NDK_HOME" ]; then
    echo "Set ANDROID_NDK_HOME to your Android NDK path."
    exit 1
fi

echo "==> Downloading the Kingpin SDK (this is third party, non-free code)..."
echo "    By continuing you confirm that you are allowed to build the SDK."
mkdir -p "$BUILD"
TMP_TAR="$(mktemp /tmp/kingpin-sdk-XXXXXX.tar.gz)"
trap 'rm -f "$TMP_TAR"' EXIT
curl -L -o "$TMP_TAR" "$SDK_URL"
tar -xzf "$TMP_TAR" -C "$BUILD"

SDK_SRC="$(find "$BUILD" -maxdepth 1 -mindepth 1 -type d | head -n 1)"

echo "==> Applying the game module ABI patch..."
( cd "$SDK_SRC" && patch -p1 < "$PATCH" )

echo "==> Checking CNS fallback cvar initialization..."
for name in nav_dynamic nav_debug nav_optimize nav_aipath; do
    if ! grep -Fq "$name = gi.cvar(\"$name\"" "$SDK_SRC/gamesrc/G_main.c"; then
        fail "SDK patch did not initialize required CNS cvar $name"
    fi
done

echo "==> Preparing sources (lowercasing file names)..."
GAMESRC="$BUILD/gamesrc"
rm -rf "$GAMESRC"
mkdir -p "$GAMESRC"

# Copy all files with lowercased names so #include "g_local.h" style
# includes work on case sensitive file systems.
( cd "$SDK_SRC/gamesrc" && for f in *; do
    [ -f "$f" ] || continue
    lower="$(printf '%s' "$f" | tr 'A-Z' 'a-z')"
    cp "$f" "$GAMESRC/$lower"
done )

# Keep the unavailable CNS interface explicit and type checked against
# the downloaded SDK headers instead of synthesizing void(void) symbols.
cp "$PWD/kingpin_nav_stubs.c" "$GAMESRC/kingpin_nav_stubs.c"

echo "==> Building with the Android NDK..."

build_abi() {
    ABI="$1"
    ARCH="$2"
    API=21
    TRIPLE="$3"

    CC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/${TRIPLE}${API}-clang"
    if [ ! -x "$CC" ]; then
        echo "NDK toolchain not found: $CC"
        return 1
    fi

    OBJDIR="$BUILD/obj-$ABI"
    mkdir -p "$OBJDIR"

    CFLAGS="-O2 -fPIC -fcommon -w \
        -D_stricmp=strcasecmp -D_strnicmp=strncasecmp -Dstrcmpi=strcasecmp \
        -Dstricmp=strcasecmp -Dstrnicmp=strncasecmp"

    echo "-- compiling for $ABI ..."
    COMPLOG="$BUILD/compile-$ABI.log"
    : > "$COMPLOG"
    # Every file must compile - a failed translation unit used to be
    # silently skipped (|| true) and the shared library still linked,
    # hiding broken builds behind a green step.
    ( cd "$GAMESRC" && for f in *.c; do
        echo "cc $f"
        "$CC" $CFLAGS -c "$f" -o "$OBJDIR/${f%.c}.o"
    done ) >> "$COMPLOG" 2>&1 || COMPFAIL=1
    # Surface every compiler error line as a GitHub annotation.
    grep 'error:' "$COMPLOG" | head -n 8 | while IFS= read -r line; do
        echo "$line"
        if [ "$GITHUB_ACTIONS" = "true" ]; then
            echo "::error title=Game lib $ABI::${line}"
        fi
    done
    if [ -n "$COMPFAIL" ]; then
        tail -n 20 "$COMPLOG"
        fail "Compilation failed for $ABI (see the tail above)"
    fi
    if grep -q 'error:' "$COMPLOG"; then
        fail "Compiler reported errors for $ABI"
    fi
    NC="$(ls "$GAMESRC"/*.c | wc -l)"
    NO="$(ls "$OBJDIR"/*.o | wc -l)"
    if [ "$NO" -ne "$NC" ]; then
        fail "$ABI: expected one object per .c file ($NC), found $NO"
    fi

    # The CNS fallback is compiled with prototypes from g_nav.h. A hard
    # --no-undefined link ensures future SDK changes cannot silently bring
    # back the old, ABI-unsafe generic stubs.
    echo "-- linking for $ABI ..."
    "$CC" -shared -Wl,--no-undefined -o "$BUILD/libgame-$ABI.so" \
        "$OBJDIR"/*.o -lm 2> "$BUILD/link-$ABI.log" || {
        tail -n 20 "$BUILD/link-$ABI.log"
        fail "Link failed for $ABI (see the linker output above)"
    }

    if ! nm -D "$BUILD/libgame-$ABI.so" | grep -q ' T GetGameAPI$'; then
        fail "$ABI: GetGameAPI is not exported from libgame.so"
    fi

    mkdir -p "$OUT_JNI/$ABI"
    cp "$BUILD/libgame-$ABI.so" "$OUT_JNI/$ABI/libgame.so"
    echo "-- installed $OUT_JNI/$ABI/libgame.so ($NO game objects)"
}

build_abi arm64-v8a aarch64 aarch64-linux-android
build_abi armeabi-v7a armv7 armv7a-linux-androideabi

echo ""
echo "==> Done. The game library is in app/src/main/jniLibs/*/libgame.so"
echo "    and will be picked up automatically at runtime (it is loaded"
echo "    from the game data directory first if one is present there)."
echo ""
echo "    Reminder: CNS path finding is unavailable; typed Android fallbacks are used."
echo "    Run ./check_abi.sh to re-verify the game module ABI."
