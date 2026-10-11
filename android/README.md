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

## Building in GitHub Actions

`.github/workflows/android.yml` builds the APK in the cloud - no local
Android SDK and no game data needed. Open **Actions -> Android build
-> Run workflow** (or push to the branch; `v*` tags publish a release
automatically):

* **make_release** - publish the APK as a GitHub release.

Optional secrets `KEYSTORE_BASE64` + `KEYSTORE_PASSWORD` +
`KEYSTORE_ALIAS` give a stable APK signature across builds (without
them each build gets a fresh key: the first install works, updating on
top needs a uninstall).

The game data (copy `main/*.pak` to the folder shown by the launcher)
is only needed on the phone at runtime. To prepare it locally:

```sh
./fetch_game_data.sh "https://drive.google.com/file/d/..." game-data
./fetch_game_data.sh "https://huggingface.co/datasets/..." game-data
```

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
