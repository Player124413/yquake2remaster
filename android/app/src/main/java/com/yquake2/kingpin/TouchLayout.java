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

import java.util.ArrayList;
import java.util.List;

/**
 * Model of the on screen control layout. Positions are normalized to the
 * screen (0..1), sizes are multipliers of the base size. The layout is
 * stored in SharedPreferences and can be edited in {@link TouchEditorActivity}.
 */
public final class TouchLayout
{
    /** One on screen button. */
    public static final class Button
    {
        public String id;
        public String label;
        public String key;
        public float x, y;      /* normalized center */
        public float size;      /* size multiplier */
        public float alpha;     /* per button alpha multiplier */
        public boolean visible;

        public Button(String id, String label, String key,
                      float x, float y, float size, boolean visible)
        {
            this.id = id;
            this.label = label;
            this.key = key;
            this.x = x;
            this.y = y;
            this.size = size;
            this.alpha = 1.0f;
            this.visible = visible;
        }
    }

    public final List<Button> buttons = new ArrayList<>();

    public TouchLayout()
    {
        setDefaults();
    }

    /** Default Kingpin layout, landscape. */
    public void setDefaults()
    {
        buttons.clear();

        /* right hand: fire cluster */
        add("fire", "FIRE", "MOUSE1", 0.885f, 0.720f, 1.45f, true);
        add("altfire", "ALT", "MOUSE2", 0.735f, 0.845f, 1.15f, true);
        add("jump", "JUMP", "SPACE", 0.895f, 0.430f, 1.15f, true);
        add("crouch", "CROUCH", "CTRL", 0.745f, 0.560f, 1.05f, true);
        add("reload", "RELOAD", "R", 0.905f, 0.220f, 1.0f, true);
        add("use", "USE", "E", 0.760f, 0.235f, 1.0f, true);
        add("weapnext", "WPN+", "MWHEELDOWN", 0.630f, 0.185f, 0.9f, true);
        add("weapprev", "WPN-", "MWHEELUP", 0.630f, 0.345f, 0.9f, true);
        add("kick", "KICK", "K", 0.615f, 0.620f, 0.95f, true);
        add("drop", "DROP", "G", 0.510f, 0.780f, 0.85f, true);
        add("buy", "BUY", "B", 0.510f, 0.600f, 0.85f, true);

        /* left hand */
        add("scores", "SCORES", "TAB", 0.075f, 0.175f, 0.9f, true);
        add("menu", "MENU", "ESCAPE", 0.055f, 0.055f, 0.85f, true);
        add("console", "CON", "CONSOLE", 0.155f, 0.055f, 0.85f, false);
    }

    private void add(String id, String label, String key,
                     float x, float y, float size, boolean visible)
    {
        buttons.add(new Button(id, label, key, x, y, size, visible));
    }

    public Button find(String id)
    {
        for (Button b : buttons)
        {
            if (b.id.equals(id))
            {
                return b;
            }
        }

        return null;
    }

    /* ---------------------------------------------------------------- */
    /* Persistence                                                      */
    /* ---------------------------------------------------------------- */

    public void load(Prefs prefs)
    {
        for (Button b : buttons)
        {
            String p = "btn_" + b.id + "_";
            b.x = prefs.getFloat(p + "x", b.x);
            b.y = prefs.getFloat(p + "y", b.y);
            b.size = prefs.getFloat(p + "size", b.size);
            b.alpha = prefs.getFloat(p + "alpha", b.alpha);
            b.visible = prefs.getBool(p + "vis", b.visible);
            b.key = prefs.getString(p + "key", b.key);
        }
    }

    public void save(Prefs prefs)
    {
        for (Button b : buttons)
        {
            String p = "btn_" + b.id + "_";
            prefs.setFloat(p + "x", b.x);
            prefs.setFloat(p + "y", b.y);
            prefs.setFloat(p + "size", b.size);
            prefs.setFloat(p + "alpha", b.alpha);
            prefs.setBool(p + "vis", b.visible);
            prefs.setString(p + "key", b.key);
        }
    }

    public void reset(Prefs prefs)
    {
        setDefaults();

        for (Button b : buttons)
        {
            String p = "btn_" + b.id + "_";
            prefs.setFloat(p + "x", b.x);
            prefs.setFloat(p + "y", b.y);
            prefs.setFloat(p + "size", b.size);
            prefs.setFloat(p + "alpha", b.alpha);
            prefs.setBool(p + "vis", b.visible);
            prefs.setString(p + "key", b.key);
        }
    }

    /**
     * Key names offered in the button configuration. The names are resolved
     * by Key_StringToKeynum() inside the engine, so they match the engine
     * key names (see keynames[] in src/client/cl_keyboard.c).
     */
    public static final String[] KEY_NAMES = {
        "MOUSE1", "MOUSE2", "MOUSE3", "SPACE", "CTRL", "SHIFT", "ALT",
        "ENTER", "ESCAPE", "TAB", "BACKSPACE", "CONSOLE",
        "UPARROW", "DOWNARROW", "LEFTARROW", "RIGHTARROW",
        "MWHEELUP", "MWHEELDOWN",
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
        "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
        "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10",
        "F11", "F12"
    };
}
