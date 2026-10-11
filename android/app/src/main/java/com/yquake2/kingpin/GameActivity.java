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
import android.util.DisplayMetrics;
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
     * Command line for SDL_main. The render scale is translated into a
     * custom engine resolution here, where the real display metrics are
     * known.
     */
    @Override
    protected String[] getArguments()
    {
        Prefs prefs = new Prefs(this);

        float scale = prefs.renderScale();

        if (scale < 0.25f)
        {
            scale = 0.25f;
        }

        if (scale > 1.0f)
        {
            scale = 1.0f;
        }

        {
            DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getRealMetrics(dm);

            int w = Math.max(320, Math.round(dm.widthPixels * scale));
            int h = Math.max(240, Math.round(dm.heightPixels * scale));

            /* round to multiples of 8, some drivers like that */
            w &= ~7;
            h &= ~7;

            prefs.setInt("custom_width", w);
            prefs.setInt("custom_height", h);
        }

        /* engine renders to a fullscreen scaled window on Android */
        prefs.setBool("force_fullscreen", true);

        String libDir = getApplicationInfo().nativeLibraryDir;
        List<String> args = prefs.engineArgs(this, libDir);

        return args.toArray(new String[0]);
    }
}
