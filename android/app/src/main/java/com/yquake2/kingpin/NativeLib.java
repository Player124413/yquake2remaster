/*
 * Copyright (C) 2026 the Yamagi Quake II Remaster contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * See the GNU General Public License for more details.
 */

package com.yquake2.kingpin;

/**
 * JNI bridge to the engine. All methods are implemented in
 * src/backends/unix/android_jni.c and only work once libmain.so has been
 * loaded by {@link GameActivity}. Never call them from the launcher.
 */
public final class NativeLib
{
    private NativeLib()
    {
    }

    /** Point Sys_GetBinaryDir() at the APK native library dir. */
    public static native void nativeInit(String libDir);

    /** Press/release a key by engine key name ("MOUSE1", "SPACE", ...). */
    public static native void nativeKey(String keyName, boolean down);

    /** Camera look delta in degrees (positive yaw turns right). */
    public static native void nativeLook(float yaw, float pitch);

    /** Virtual movement stick state, both axes in [-1, 1]. */
    public static native void nativeStick(float forward, float side);

    /** Release all injected keys and zero all motion state. */
    public static native void nativeAllUp();

    /** Current key destination: 0 game, 1 console, 2 message, 3 menu. */
    public static native int nativeKeyDest();

    /** 1 when the engine touch injection is enabled. */
    public static native int nativeTouchEnabled();

    /** Execute a console command. */
    public static native void nativeConsoleCmd(String cmd);
}
