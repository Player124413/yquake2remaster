# Android Port

This directory contains a complete Android port of Yamagi Quake II
Remaster with first class touch controls and a launcher, targeting
Kingpin: Life of Crime content in particular.

## Features

* **Launcher app** with all settings in one place: game data location,
  renderer, native fullscreen output, frame limit, VSync, brightness and
  the full touch control configuration.
* **Touch controls** implemented as an on screen overlay:
  * virtual movement stick (fixed or floating),
  * swipe look with adjustable sensitivity and optional Y invert,
  * fully configurable action buttons (fire, alt-fire, jump, crouch,
    reload, use, weapon next/prev, kick, drop, buy, scores, menu,
    console) - each with its own visibility, size and key binding,
  * menu navigation gestures (swipe = arrows, tap = enter),
  * drag & drop **layout editor** with pinch resize,
  * optional vibration on button press,
  * **master switch to disable all touch controls** (for gamepad play).
* **Performance**
  * fullscreen output uses the phone's native display mode; SDL cannot
    switch Android into arbitrary scaled display modes,
  * three renderers: OpenGL ES 3 (quality), OpenGL ES 1 (compatible),
    software (fallback),
  * quality / balanced / fast presets in the launcher,
  * engine built with `-O3` and link time optimization for the target
    ABI (arm64-v8a, armeabi-v7a). Proper lower-resolution rendering
    requires an off-screen render target and is not exposed yet.

The launcher always passes its settings to the engine on the command
line (`+set`), so they win over `config.cfg`. Key bindings and everything
not managed by the launcher are still configured in game as usual.

## Building

### Requirements

* Android SDK + NDK (r25 or newer) + CMake 3.22+
* Java 17
* Gradle 8.7+ (or Android Studio, which can create the wrapper)
* `curl` and `tar` for the dependency scripts

### Steps

```sh
cd android

# 1. fetch SDL2 (the only third party dependency, not vendored)
./fetch_deps.sh

# 2. build the APK
export ANDROID_HOME=/path/to/android-sdk
./build_apk.sh debug      # or: ./build_apk.sh release
```

The APK is written to `app/build/outputs/apk/<type>/`. The native
libraries built into it are:

| Library          | Purpose                                          |
|------------------|--------------------------------------------------|
| `libSDL2.so`     | SDL2 (window, input, audio)                      |
| `libmain.so`     | the engine (loaded by the SDL2 Java bootstrap)   |
| `libref_gles3.so`| OpenGL ES 3 renderer (quality)                   |
| `libref_gles1.so`| OpenGL ES 1 renderer (compatible)                |
| `libref_soft.so` | software renderer (fallback)                     |
| `libgame.so`     | game logic (Quake II / ReRelease compatible)     |

### Command line arguments

`GameActivity` invokes the engine as:

```
-datadir <data path> -cfgdir <files>/cfg -libdir <nativeLibraryDir>
+set game main +set maptype 3 +set vid_renderer gles3
+set vid_fullscreen 2 +set r_mode -2 ...
```

`-libdir` tells the engine where the renderer modules live (the APK
native library dir), see `Sys_SetBinaryDir()` in
`src/backends/generic/misc.c`.

## Game data

The launcher does not ship any game data. Copy the game data of a game
you own into the data folder shown on the launcher screen. By default
that is:

```
/sdcard/Android/data/com.yquake2.kingpin/files/kingpin/
    main/
        pak0.pak
        pak1.pak
        ...
```

(On Android 11+ the app specific folder is reachable with a file
manager or `adb push`. If you prefer another location, tap "Grant file
access" in the launcher and enter the path in Settings.)

For Kingpin: copy the `main/` folder with the pak files from your
Kingpin installation. The engine picks up Kingpin formats (IBSP v38
maps, mdx models, Kingpin pak files) automatically; `maptype` defaults
to `3` (Kingpin) in the launcher.

## Game libraries

The engine loads the game logic from `game.so` in the game data
directory first, and falls back to `libgame.so` from the APK.

* The bundled `libgame.so` is built from this repository's game sources
  and plays Quake II, the ReRelease and the mission packs (including
  Kingpin maps, see `maptype`).
* `build_game_lib.sh` builds a game library from the official Kingpin
  SDK (downloaded on your machine; the SDK is **not** free software and
  is not part of this repository). This runs the original Kingpin:
  Life of Crime game logic - the campaign, weapons, money, AI.

The engine and the SDK game code share one game module ABI, defined by
this repository's headers (`src/common/header/shared.h`,
`src/game/header/game.h`) and applied to the SDK by
`android/kingpin_sdk_abi.patch`. It covers the extended
`entity_state_t` / `player_state_t` (model parts, alpha/scale),
`game_import_t` / `game_export_t` function slots (skinindex, object
bounds, savegame hooks), the `edict_s` prefix and the configstring
layout. `android/check_abi.sh` verifies that both sides agree field by
field - run it whenever the headers or the patch change.

Known limitations of the Kingpin game library:

* The closed-source AI navigation library (CNS) is unavailable on
  Android. `android/kingpin_nav_stubs.c` provides ABI-correct,
  fail-closed fallbacks, and the SDK adapter initializes the navigation
  cvars before gameplay. NPC route finding is therefore unavailable; this
  avoids the old stubs' undefined return values and the null
  `nav_dynamic` dereference on a single-player frame.
* `gi.GetObjectBounds()` is a no-op: per body part hit detection has no
  object bounds yet.
* `gi.SaveCurrentGame()` is a no-op: hired guys do not survive map
  transitions.
* `gi.skinindex()` returns per model registration order, which may
  differ from the skin order embedded in the MDX files - some skins may
  look wrong.
* Model part rendering on the client is not implemented yet: NPCs and
  players are drawn as their base model without detachable parts
  (heads, weapons). The part data is synced over the network
  (`U_KPPARTS`) and ready for the renderer to use.

## Touch controls reference

All touch settings live in the launcher (Settings → Touch controls):

| Setting           | Meaning                                             |
|-------------------|-----------------------------------------------------|
| Enable touch      | master switch; off = overlay hidden, no injection   |
| Overlay opacity   | transparency of all on screen controls              |
| Button size       | global size multiplier                              |
| Movement stick    | fixed (always bottom left) or floating (appears under the finger on the left half of the screen) |
| Stick size        | movement stick size multiplier                      |
| Stick dead zone   | inner dead zone of the stick                        |
| Look sensitivity  | swipe look speed                                    |
| Invert Y          | invert vertical look                                |
| Vibration         | short haptic pulse on button press                  |
| Buttons           | per button visibility, size and key binding         |
| Layout editor     | drag buttons to move, pinch to resize, hide/show    |

Menu navigation: swipe up/down/left/right to move the selection, tap to
confirm. Over the console the overlay only shows its buttons so the soft
keyboard can be used.

## Performance guide

For performance tuning on phones:

1. Try the **Fast** preset (OpenGL ES 1, VSync off) first. Actual frame
   rate depends on the device and map; 60 fps cannot be guaranteed on
   every phone.
2. Or fine tune manually:
   * Output currently uses the native display resolution. Reduced
     internal render resolution is not implemented yet (the old custom
     SDL mode incorrectly fell back to a small window on Android).
   * **ETC2 texture compression** (default on, `r_etc2`) can reduce
     texture memory use on supported GLES devices.
   * `vid_maxfps 60` with VSync on gives smooth frame pacing; VSync off
     can reduce input latency on high-refresh displays.
   * The software renderer is a compatibility fallback and is CPU-bound.
3. Close background apps. The engine defaults to `cl_async 1` with
   automatic packet framerate, which keeps movement independent of
   render framerate.

## Repository layout

```
android/
  fetch_deps.sh        SDL2 download + Java bootstrap copy
  build_apk.sh         Gradle build entry point
  build_game_lib.sh    Kingpin SDK game library (experimental)
  app/src/main/
    java/com/yquake2/kingpin/
      LauncherActivity.java    main screen, presets, data check
      SettingsActivity.java    all settings
      TouchEditorActivity.java layout editor
      GameActivity.java        SDL host + overlay, engine arguments
      TouchControlsView.java   overlay + editor rendering/input
      TouchLayout.java         button model + persistence
      Prefs.java               settings -> engine cvars
      NativeLib.java           JNI declarations
    cpp/                       (CMake entry: the repository root
                                CMakeLists.txt has ANDROID support)
```

Engine side pieces of the port:

* `src/client/input/touch_inject.c` - thread safe event queue that feeds
  look/movement/keys into the client (`TouchInj_ApplyCmd()` is called
  from `CL_CreateCmd()` right after `IN_Move()`).
* `src/backends/unix/android_jni.c` - JNI bridge (`com.yquake2.kingpin.NativeLib`).
* `Sys_SetBinaryDir()` (`src/backends/generic/misc.c`) and
  `-libdir` (`src/backends/unix/main.c`) - library location override.
* `CMakeLists.txt` - `if(ANDROID)` builds `libmain.so`, the renderer
  modules and the game library as regular shared libraries.

## Limitations / future work

* Kingpin SDK game logic: NPC path finding needs a real navigation
  library, body part hit detection needs the MDX object bounds loader
  and part rendering needs client side model part drawing (see "Game
  libraries" above).
* HTTP downloads are disabled in the Android build (cURL is not bundled).
* OpenAL is not bundled; the SDL audio backend is used.
* The overlay draws in Java, so external gamepads see no overlay at all
  (that is what the master switch is for).
