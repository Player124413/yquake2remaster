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

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import org.libsdl.app.SDLActivity;

import java.util.List;

/**
 * Hosts the SDL game surface and the touch control overlay.
 */
public class GameActivity extends SDLActivity
{
    private TouchControlsView touchView;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        /* SDLActivity.onCreate() has loaded the native libraries at this
           point (or set mBrokenLibraries); open the engine log first so
           every engine message lands in the launcher visible file. */
        GameLog.init(this);
        GameLog.log("GameActivity: onCreate (SDL libraries ok: " +
                (!SDLActivity.mBrokenLibraries) + ")");

        try
        {
            NativeLib.nativeLogOpen(GameLog.file(this).getAbsolutePath());
            GameLog.log("GameActivity: engine log opened: " +
                    GameLog.file(this).getAbsolutePath());
        }
        catch (UnsatisfiedLinkError e)
        {
            GameLog.log("GameActivity: engine library not loaded: " + e);
        }

        GameLog.log("GameActivity: onCreate done");

        Prefs prefs = new Prefs(this);
        TouchLayout layout = new TouchLayout();
        layout.load(prefs);

        touchView = new TouchControlsView(this, prefs, layout);

        ViewGroup root = mLayout;

        if (root != null)
        {
            root.addView(touchView, 0,
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    @Override
    protected void onDestroy()
    {
        GameLog.log("GameActivity: onDestroy");

        try
        {
            NativeLib.nativeAllUp();
        }
        catch (UnsatisfiedLinkError e)
        {
            /* engine not started, nothing to do */
        }

        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);

        if (!hasFocus)
        {
            try
            {
                NativeLib.nativeAllUp();
            }
            catch (UnsatisfiedLinkError e)
            {
                /* ignore */
            }
        }
    }

    /**
     * Command line for SDL_main. Android uses the display's native mode;
     * asking SDL for a non-native exclusive mode creates a 640x480 fallback
     * window instead of scaling the game to the screen.
     */
    @Override
    protected String[] getArguments()
    {
        Prefs prefs = new Prefs(this);
        String libDir = getApplicationInfo().nativeLibraryDir;
        List<String> args = prefs.engineArgs(this, libDir);

        GameLog.log("GameActivity: engine args: " + args);

        return args.toArray(new String[0]);
    }
}
