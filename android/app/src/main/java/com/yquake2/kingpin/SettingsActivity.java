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

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

/**
 * All launcher settings: game data, display / performance and the touch
 * controls (sizes, visibility, sensitivity). Everything is written to
 * {@link Prefs} immediately.
 */
public class SettingsActivity extends Activity
{
    private Prefs prefs;
    private TouchLayout layout;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = new Prefs(this);
        layout = new TouchLayout();
        layout.load(prefs);

        root = findViewById(R.id.settings_root);
        buildGameSection();
        buildDisplaySection();
        buildTouchSection();
        buildButtonsSection();
        buildFooter();
    }

    /* ---------------------------------------------------------------- */
    /* Row builders                                                     */
    /* ---------------------------------------------------------------- */

    private TextView sectionTitle(int resId)
    {
        TextView tv = new TextView(this);
        tv.setText(resId);
        tv.setTextColor(getResources().getColor(R.color.accent));
        tv.setTextSize(14);
        tv.setPadding(0, dp(18), 0, dp(6));
        tv.setGravity(Gravity.START);
        root.addView(tv);
        return tv;
    }

    private TextView label(String text)
    {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(getResources().getColor(R.color.text_secondary));
        tv.setTextSize(13);
        tv.setPadding(0, dp(10), 0, dp(2));
        root.addView(tv);
        return tv;
    }

    private SeekBar seekBar(int progress, int max, final SeekBar.OnSeekBarChangeListener l)
    {
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(progress);
        sb.setOnSeekBarChangeListener(l);
        root.addView(sb, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sb;
    }

    private Switch switchRow(String text, boolean checked,
                             final CompoundButton.OnCheckedChangeListener l)
    {
        Switch sw = new Switch(this);
        sw.setText(text);
        sw.setTextColor(getResources().getColor(R.color.text_primary));
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(l);
        sw.setPadding(0, dp(10), 0, dp(10));
        root.addView(sw, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sw;
    }

    private Spinner spinner(String[] entries, int selected,
                            final AdapterView.OnItemSelectedListener l)
    {
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, entries);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        sp.setSelection(selected);
        sp.setOnItemSelectedListener(l);
        root.addView(sp, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sp;
    }

    private EditText editText(String text, final TextWatcher watcher)
    {
        EditText et = new EditText(this);
        et.setText(text);
        et.setTextColor(getResources().getColor(R.color.text_primary));
        et.setSingleLine(true);
        et.addTextChangedListener(watcher);
        root.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return et;
    }

    private int dp(int v)
    {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /* ---------------------------------------------------------------- */
    /* Sections                                                         */
    /* ---------------------------------------------------------------- */

    private void buildGameSection()
    {
        sectionTitle(R.string.sec_game);

        label(getString(R.string.data_path) + " — " + getString(R.string.data_path_hint));
        editText(prefs.dataPath(this), new TextWatcher()
        {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s)
            {
                prefs.setDataPath(s.toString().trim());
            }
        });

        label(getString(R.string.game_dir));
        editText(prefs.gameDir(), new TextWatcher()
        {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s)
            {
                prefs.setGameDir(s.toString().trim());
            }
        });
    }

    private void buildDisplaySection()
    {
        sectionTitle(R.string.sec_display);

        label(getString(R.string.renderer));
        spinner(new String[]{"OpenGL ES 3 (quality)", "OpenGL ES 1 (compatible)", "Software"},
                prefs.renderer(),
                new AdapterView.OnItemSelectedListener()
                {
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id)
                    {
                        prefs.setInt("renderer", pos);
                    }

                    public void onNothingSelected(AdapterView<?> p) {}
                });

        label(getString(R.string.android_display_note));

        label(getString(R.string.fps_limit));
        spinner(new String[]{"30", "60", "90", "120", "144", "Uncapped"},
                fpsToIndex(prefs.fpsLimit()),
                new AdapterView.OnItemSelectedListener()
                {
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id)
                    {
                        prefs.setInt("fps_limit", indexToFps(pos));
                    }

                    public void onNothingSelected(AdapterView<?> p) {}
                });

        switchRow(getString(R.string.vsync), prefs.vsync(),
                new CompoundButton.OnCheckedChangeListener()
                {
                    public void onCheckedChanged(CompoundButton b, boolean on)
                    {
                        prefs.setBool("vsync", on);
                    }
                });

        switchRow(getString(R.string.etc2), prefs.etc2Enabled(),
                new CompoundButton.OnCheckedChangeListener()
                {
                    public void onCheckedChanged(CompoundButton b, boolean on)
                    {
                        prefs.setBool("etc2", on);
                    }
                });

        label(getString(R.string.brightness));
        seekBar(Math.round((prefs.brightness() - 0.5f) * 100), 150,
                new SeekBar.OnSeekBarChangeListener()
                {
                    public void onProgressChanged(SeekBar sb, int progress, boolean fromUser)
                    {
                        prefs.setFloat("brightness", 0.5f + progress / 100.0f);
                    }

                    public void onStartTrackingTouch(SeekBar sb) {}
                    public void onStopTrackingTouch(SeekBar sb) {}
                });
    }

    private void buildTouchSection()
    {
        sectionTitle(R.string.sec_touch);

        switchRow(getString(R.string.touch_enable) + "\n" + getString(R.string.touch_enable_note),
                prefs.touchEnabled(),
                new CompoundButton.OnCheckedChangeListener()
                {
                    public void onCheckedChanged(CompoundButton b, boolean on)
                    {
                        prefs.setBool("touch_enable", on);
                    }
                });

        label(getString(R.string.touch_alpha) + " (%)");
        seekBar(Math.round(prefs.touchAlpha() * 100) - 10, 90,
                new SeekBar.OnSeekBarChangeListener()
                {
                    public void onProgressChanged(SeekBar sb, int progress, boolean fromUser)
                    {
                        prefs.setFloat("touch_alpha", (10 + progress) / 100.0f);
                    }

                    public void onStartTrackingTouch(SeekBar sb) {}
                    public void onStopTrackingTouch(SeekBar sb) {}
                });

        label(getString(R.string.touch_scale) + " (%)");
        seekBar(Math.round(prefs.touchScale() * 100) - 50, 150,
                new SeekBar.OnSeekBarChangeListener()
                {
                    public void onProgressChanged(SeekBar sb, int progress, boolean fromUser)
                    {
                        prefs.setFloat("touch_scale", (50 + progress) / 100.0f);
                    }

                    public void onStartTrackingTouch(SeekBar sb) {}
                    public void onStopTrackingTouch(SeekBar sb) {}
                });

        label(getString(R.string.stick_mode));
        spinner(new String[]{"Fixed", "Floating"},
                prefs.stickMode(),
                new AdapterView.OnItemSelectedListener()
                {
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id)
                    {
                        prefs.setInt("stick_mode", pos);
                    }

                    public void onNothingSelected(AdapterView<?> p) {}
                });

        label(getString(R.string.stick_size) + " (%)");
        seekBar(Math.round(prefs.stickSize() * 100) - 50, 150,
                new SeekBar.OnSeekBarChangeListener()
                {
                    public void onProgressChanged(SeekBar sb, int progress, boolean fromUser)
                    {
                        prefs.setFloat("stick_size", (50 + progress) / 100.0f);
                    }

                    public void onStartTrackingTouch(SeekBar sb) {}
                    public void onStopTrackingTouch(SeekBar sb) {}
                });

        label(getString(R.string.stick_deadzone) + " (%)");
        seekBar(Math.round(prefs.stickDeadzone() * 100), 40,
                new SeekBar.OnSeekBarChangeListener()
                {
                    public void onProgressChanged(SeekBar sb, int progress, boolean fromUser)
                    {
                        prefs.setFloat("stick_deadzone", progress / 100.0f);
                    }

                    public void onStartTrackingTouch(SeekBar sb) {}
                    public void onStopTrackingTouch(SeekBar sb) {}
                });

        label(getString(R.string.look_sensitivity) + " (%)");
        seekBar(Math.round(prefs.lookSensitivity() * 100) - 25, 275,
                new SeekBar.OnSeekBarChangeListener()
                {
                    public void onProgressChanged(SeekBar sb, int progress, boolean fromUser)
                    {
                        prefs.setFloat("look_sensitivity", (25 + progress) / 100.0f);
                    }

                    public void onStartTrackingTouch(SeekBar sb) {}
                    public void onStopTrackingTouch(SeekBar sb) {}
                });

        switchRow(getString(R.string.invert_y), prefs.invertY(),
                new CompoundButton.OnCheckedChangeListener()
                {
                    public void onCheckedChanged(CompoundButton b, boolean on)
                    {
                        prefs.setBool("invert_y", on);
                    }
                });

        switchRow(getString(R.string.vibrate), prefs.vibrate(),
                new CompoundButton.OnCheckedChangeListener()
                {
                    public void onCheckedChanged(CompoundButton b, boolean on)
                    {
                        prefs.setBool("vibrate", on);
                    }
                });
    }

    private void buildButtonsSection()
    {
        sectionTitle(R.string.buttons_section);

        for (final TouchLayout.Button btn : layout.buttons)
        {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));

            CheckBox cb = new CheckBox(this);
            cb.setChecked(btn.visible);
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
            {
                public void onCheckedChanged(CompoundButton b, boolean on)
                {
                    btn.visible = on;
                    layout.save(prefs);
                }
            });
            row.addView(cb, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f));

            TextView name = new TextView(this);
            name.setText(btn.label);
            name.setTextColor(getResources().getColor(R.color.text_primary));
            name.setTextSize(13);
            row.addView(name, new LinearLayout.LayoutParams(
                    dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));

            SeekBar size = new SeekBar(this);
            size.setMax(270);
            size.setProgress(Math.round(btn.size * 100) - 30);
            size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
            {
                public void onProgressChanged(SeekBar sb, int progress, boolean fromUser)
                {
                    btn.size = (30 + progress) / 100.0f;
                    layout.save(prefs);
                }

                public void onStartTrackingTouch(SeekBar sb) {}
                public void onStopTrackingTouch(SeekBar sb) {}
            });
            row.addView(size, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Spinner key = new Spinner(this);
            ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, TouchLayout.KEY_NAMES);
            ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            key.setAdapter(ad);
            int idx = 0;

            for (int i = 0; i < TouchLayout.KEY_NAMES.length; i++)
            {
                if (TouchLayout.KEY_NAMES[i].equalsIgnoreCase(btn.key))
                {
                    idx = i;
                    break;
                }
            }

            key.setSelection(idx);
            key.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
            {
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id)
                {
                    btn.key = TouchLayout.KEY_NAMES[pos];
                    layout.save(prefs);
                }

                public void onNothingSelected(AdapterView<?> p) {}
            });
            row.addView(key, new LinearLayout.LayoutParams(
                    dp(110), ViewGroup.LayoutParams.WRAP_CONTENT));

            root.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        Button editor = new Button(this);
        editor.setText(R.string.touch_editor);
        editor.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                startActivity(new Intent(SettingsActivity.this, TouchEditorActivity.class));
            }
        });
        root.addView(editor, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button reset = new Button(this);
        reset.setText(R.string.reset_layout);
        reset.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                layout.reset(prefs);
                recreate();
            }
        });
        root.addView(reset, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildFooter()
    {
        Button back = new Button(this);
        back.setText(R.string.back);
        back.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                finish();
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        root.addView(back, lp);
    }

    /* ---------------------------------------------------------------- */
    /* Helpers                                                          */
    /* ---------------------------------------------------------------- */

    private static int fpsToIndex(int fps)
    {
        switch (fps)
        {
            case 30:
                return 0;
            case 60:
                return 1;
            case 90:
                return 2;
            case 120:
                return 3;
            case 144:
                return 4;
            default:
                return 5;
        }
    }

    private static int indexToFps(int idx)
    {
        switch (idx)
        {
            case 0:
                return 30;
            case 1:
                return 60;
            case 2:
                return 90;
            case 3:
                return 120;
            case 4:
                return 144;
            default:
                return 0;
        }
    }
}
