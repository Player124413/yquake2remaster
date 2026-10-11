# Kingpin Android Port

Android port of Yamagi Quake II Remaster with a launcher and high
quality touch controls, aimed at Kingpin: Life of Crime.

**Full documentation: [../doc/050_android.md](../doc/050_android.md)**

## Quick start

```sh
./fetch_deps.sh            # download SDL2 (not vendored)
export ANDROID_HOME=/path/to/android-sdk
./build_apk.sh debug       # build the APK
```

Then copy your game data (`main/*.pak`) to the data folder shown in the
launcher and press PLAY.

## What is here

* `app/src/main/java/com/yquake2/kingpin/` - launcher, settings, touch
  control overlay and layout editor (plain Java, no external deps).
* `fetch_deps.sh` - fetches SDL2 and its Android Java bootstrap.
* `build_apk.sh` - Gradle build wrapper.
* `build_game_lib.sh` - Kingpin SDK game library build (the SDK is
  downloaded on your machine, it is not free software). The engine
  side of the game module ABI lives in this repository; see
  `kingpin_sdk_abi.patch` and `check_abi.sh`.
* `kingpin_sdk_abi.patch` - adapts the Kingpin SDK headers to the game
  module ABI defined by this repository's headers.
* `check_abi.sh` - verifies engine and SDK header layout agreement.

The engine side of the port (touch injection layer, JNI bridge, Android
CMake support) is documented in `doc/050_android.md`.
