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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Application settings. The launcher is the single source of truth for
 * everything it manages: settings are translated into engine cvars and
 * passed on the command line, where they override config.cfg.
 */
public final class Prefs
{
    private static final String NAME = "kingpin_prefs";

    public final SharedPreferences sp;

    public Prefs(Context ctx)
    {
        sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    /* ---------------------------------------------------------------- */
    /* Helpers                                                          */
    /* ---------------------------------------------------------------- */

    public boolean getBool(String key, boolean def)
    {
        return sp.getBoolean(key, def);
    }

    public void setBool(String key, boolean value)
    {
        sp.edit().putBoolean(key, value).apply();
    }

    public int getInt(String key, int def)
    {
        return sp.getInt(key, def);
    }

    public void setInt(String key, int value)
    {
        sp.edit().putInt(key, value).apply();
    }

    public float getFloat(String key, float def)
    {
        return sp.getFloat(key, def);
    }

    public void setFloat(String key, float value)
    {
        sp.edit().putFloat(key, value).apply();
    }

    public String getString(String key, String def)
    {
        return sp.getString(key, def);
    }

    public void setString(String key, String value)
    {
        sp.edit().putString(key, value).apply();
    }

    /* ---------------------------------------------------------------- */
    /* Game                                                             */
    /* ---------------------------------------------------------------- */

    /** Default game data location: app specific external storage. */
    public static String defaultDataPath(Context ctx)
    {
        File ext = ctx.getExternalFilesDir(null);
        File base = (ext != null) ? ext : ctx.getFilesDir();
        return new File(base, "kingpin").getAbsolutePath();
    }

    public String dataPath(Context ctx)
    {
        return getString("data_path", defaultDataPath(ctx));
    }

    public void setDataPath(String path)
    {
        setString("data_path", path);
    }

    /** Game directory inside the data path ("main" for Kingpin). */
    public String gameDir()
    {
        return getString("game_dir", "main");
    }

    public void setGameDir(String dir)
    {
        setString("game_dir", dir);
    }

    /* ---------------------------------------------------------------- */
    /* Graphics & performance                                           */
    /* ---------------------------------------------------------------- */

    /* renderers: 0 = GLES3 (quality), 1 = GLES1 (compatible), 2 = software */
    public int renderer()
    {
        return getInt("renderer", 0);
    }

    public String rendererName()
    {
        switch (renderer())
        {
            case 1:
                return "gles1";
            case 2:
                return "soft";
            default:
                return "gles3";
        }
    }

    /** Target frame rate; 0 = uncapped. */
    public int fpsLimit()
    {
        return getInt("fps_limit", 60);
    }

    public boolean vsync()
    {
        return getBool("vsync", true);
    }

    public float brightness()
    {
        return getFloat("brightness", 1.0f);
    }

    /* ---------------------------------------------------------------- */
    /* Touch controls                                                   */
    /* ---------------------------------------------------------------- */

    /** Master switch: false disables all on screen controls and injection. */
    public boolean touchEnabled()
    {
        return getBool("touch_enable", true);
    }

    public float touchAlpha()
    {
        return getFloat("touch_alpha", 0.65f);
    }

    public float touchScale()
    {
        return getFloat("touch_scale", 1.0f);
    }

    /** 0 = fixed stick, 1 = floating stick. */
    public int stickMode()
    {
        return getInt("stick_mode", 1);
    }

    public float stickSize()
    {
        return getFloat("stick_size", 1.0f);
    }

    public float stickDeadzone()
    {
        return getFloat("stick_deadzone", 0.12f);
    }

    public float lookSensitivity()
    {
        return getFloat("look_sensitivity", 1.0f);
    }

    public boolean invertY()
    {
        return getBool("invert_y", false);
    }

    public boolean vibrate()
    {
        return getBool("vibrate", true);
    }

    public boolean etc2Enabled()
    {
        return getBool("etc2", true);
    }

    /** Extra sensitivity for the right stick area drag look. */
    public float lookSmoothing()
    {
        return getFloat("look_smoothing", 0.15f);
    }

    /* ---------------------------------------------------------------- */
    /* Engine command line                                              */
    /* ---------------------------------------------------------------- */

    /**
     * Build the SDL_main argv from the current settings. Every value the
     * launcher manages is passed with +set, which the engine applies over
     * its config.cfg (see Cbuf_AddEarlyCommands in src/common/cmdparser.c).
     */
    public List<String> engineArgs(Context ctx, String nativeLibDir)
    {
        List<String> args = new ArrayList<>();

        // Diagnostic log first: the engine redirects stdout/stderr into
        // it before any check can fail (see -logfile in backends/unix/main.c).
        args.add("-logfile");
        args.add(GameLog.file(ctx).getAbsolutePath());

        args.add("-datadir");
        args.add(dataPath(ctx));
        args.add("-cfgdir");
        args.add(new File(ctx.getFilesDir(), "cfg").getAbsolutePath());

        if (nativeLibDir != null && nativeLibDir.length() > 0)
        {
            args.add("-libdir");
            args.add(nativeLibDir);
        }

        if (gameDir().length() > 0)
        {
            args.add("+set");
            args.add("game");
            args.add(gameDir());
        }

        // Kingpin maps by default, the selector in the launcher can change it.
        args.add("+set");
        args.add("maptype");
        args.add(String.valueOf(getInt("maptype", 3)));

        // Graphics
        args.add("+set");
        args.add("vid_renderer");
        args.add(rendererName());

        // Android cannot switch the display into arbitrary resolutions.
        // Request fullscreen-desktop at the actual display mode instead of
        // r_mode -1: SDL otherwise rejects the scaled mode and falls back to
        // a tiny 640x480 window.
        args.add("+set");
        args.add("vid_fullscreen");
        args.add("2");
        args.add("+set");
        args.add("r_mode");
        args.add("-2");

        args.add("+set");
        args.add("r_vsync");
        args.add(vsync() ? "1" : "0");

        // ETC2 texture compression (GLES3 renderer).
        args.add("+set");
        args.add("r_etc2");
        args.add(etc2Enabled() ? "1" : "0");

        args.add("+set");
        args.add("vid_maxfps");
        args.add(String.valueOf(fpsLimit() > 0 ? fpsLimit() : 300));

        args.add("+set");
        args.add("cl_maxfps");
        args.add("-1");

        args.add("+set");
        args.add("vid_gamma");
        args.add(String.valueOf(brightness()));

        // Audio: SDL audio is used on Android (no OpenAL build).
        args.add("+set");
        args.add("s_openal");
        args.add("0");

        // Touch
        args.add("+set");
        args.add("touch_inject");
        args.add(touchEnabled() ? "1" : "0");

        // Sensible mobile defaults, users can override in autoexec.cfg.
        args.add("+set");
        args.add("vid_pauseonfocuslost");
        args.add("1");

        return args;
    }
}
