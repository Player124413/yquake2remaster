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
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 * Main launcher screen: data check, quick performance presets and the
 * entries to the settings / touch editor screens.
 */
public class LauncherActivity extends Activity
{
    private Prefs prefs;
    private TextView statusText;

    private static final int REQUEST_MANAGE_STORAGE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        prefs = new Prefs(this);
        statusText = findViewById(R.id.status_text);

        GameLog.init(this);
        GameLog.log("launcher: onCreate (SDK " + Build.VERSION.SDK_INT +
                ", data path " + prefs.dataPath(this) + ")");

        Button play = findViewById(R.id.btn_play);
        play.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                startGame();
            }
        });

        findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                startActivity(new Intent(LauncherActivity.this, SettingsActivity.class));
            }
        });

        findViewById(R.id.btn_touch_editor).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                startActivity(new Intent(LauncherActivity.this, TouchEditorActivity.class));
            }
        });

        findViewById(R.id.btn_grant_files).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                requestFileAccess();
            }
        });

        findViewById(R.id.btn_log).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                showLogDialog();
            }
        });

        findViewById(R.id.preset_quality).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                applyPreset(0);
            }
        });

        findViewById(R.id.preset_balanced).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                applyPreset(1);
            }
        });

        findViewById(R.id.preset_fast).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                applyPreset(2);
            }
        });
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        updateStatus();
    }

    private void updateStatus()
    {
        boolean ok = dataPresent();
        statusText.setText(ok ? R.string.data_ok : R.string.data_missing);
        statusText.setTextColor(getResources().getColor(ok ? R.color.ok : R.color.error));
    }

    private boolean dataPresent()
    {
        File root = new File(prefs.dataPath(this));
        File game = new File(root, prefs.gameDir());

        if (!game.isDirectory())
        {
            GameLog.log("launcher: data check: no directory " + game);
            return false;
        }

        String[] files = game.list();

        if (files == null)
        {
            GameLog.log("launcher: data check: cannot list " + game +
                    " (storage permission missing?)");
            return false;
        }

        StringBuilder paks = new StringBuilder();

        for (String f : files)
        {
            if (f.toLowerCase().endsWith(".pak"))
            {
                if (paks.length() < 200)
                {
                    paks.append(f).append(' ');
                }
            }
        }

        GameLog.log("launcher: data check: " + game +
                " contains paks: " + (paks.length() > 0 ? "[" + paks.toString().trim() + "]" : "NONE"));

        return paks.length() > 0;
    }

    private void startGame()
    {
        boolean ok = dataPresent();
        GameLog.log("launcher: start game requested (data ok: " + ok + ")");

        if (!ok)
        {
            Toast.makeText(this, R.string.data_missing, Toast.LENGTH_LONG).show();
        }

        startActivity(new Intent(this, GameActivity.class));
    }

    /**
     * Diagnostic log viewer: shows the shared launcher/engine log with
     * copy-to-clipboard, share, clear and refresh actions.
     */
    private void showLogDialog()
    {
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad / 2, pad, 0);

        final TextView text = new TextView(this);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(11);
        text.setTextIsSelectable(true);
        text.setTextColor(getResources().getColor(R.color.text_secondary));

        String log = GameLog.read(this);
        text.setText(log.length() > 0 ? log : getString(R.string.log_empty));

        final ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button copy = new Button(this);
        copy.setText(R.string.log_copy);
        copy.setTextSize(12);
        copy.setSingleLine(true);
        copy.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                GameLog.copy(LauncherActivity.this, text.getText().toString());
                Toast.makeText(LauncherActivity.this,
                        R.string.log_copied, Toast.LENGTH_SHORT).show();
            }
        });

        Button share = new Button(this);
        share.setText(R.string.log_share);
        share.setTextSize(12);
        share.setSingleLine(true);
        share.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, text.getText().toString());
                startActivity(Intent.createChooser(send,
                        getString(R.string.log_share)));
            }
        });

        Button clear = new Button(this);
        clear.setText(R.string.log_clear);
        clear.setTextSize(12);
        clear.setSingleLine(true);
        clear.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                GameLog.clear(LauncherActivity.this);
                text.setText(R.string.log_empty);
            }
        });

        Button refresh = new Button(this);
        refresh.setText(R.string.log_refresh);
        refresh.setTextSize(12);
        refresh.setSingleLine(true);
        refresh.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                String s = GameLog.read(LauncherActivity.this);
                text.setText(s.length() > 0 ? s : getString(R.string.log_empty));
                scroll.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        scroll.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        row.addView(copy, blp);
        row.addView(share, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        row.addView(clear, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        row.addView(refresh, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        root.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(R.string.log_title)
                .setView(root)
                .setNegativeButton(R.string.log_close, null)
                .show();

        scroll.post(new Runnable()
        {
            @Override
            public void run()
            {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    /**
     * Performance presets. Values map to the launcher managed cvars:
     * 0 quality (full res, GLES3, vsync), 1 balanced, 2 fast,
     * 3 potato (low res, software renderer).
     */
    private void applyPreset(int preset)
    {
        switch (preset)
        {
            case 0:
                prefs.setInt("renderer", 0);
                prefs.setFloat("render_scale", 1.0f);
                prefs.setInt("fps_limit", 60);
                prefs.setBool("vsync", true);
                break;

            case 1:
                prefs.setInt("renderer", 0);
                prefs.setFloat("render_scale", 0.85f);
                prefs.setInt("fps_limit", 60);
                prefs.setBool("vsync", true);
                break;

            case 2:
                prefs.setInt("renderer", 1);
                prefs.setFloat("render_scale", 0.7f);
                prefs.setInt("fps_limit", 60);
                prefs.setBool("vsync", false);
                break;

            case 3:
            default:
                prefs.setInt("renderer", 2);
                prefs.setFloat("render_scale", 0.5f);
                prefs.setInt("fps_limit", 60);
                prefs.setBool("vsync", false);
                break;
        }

        int name;

        switch (preset)
        {
            case 0:
                name = R.string.preset_quality;
                break;
            case 1:
                name = R.string.preset_balanced;
                break;
            case 2:
                name = R.string.preset_fast;
                break;
            default:
                name = R.string.preset_potato;
                break;
        }

        Toast.makeText(this, name, Toast.LENGTH_SHORT).show();
    }

    private void requestFileAccess()
    {
        if (Build.VERSION.SDK_INT < 30 ||
            Environment.isExternalStorageManager())
        {
            Toast.makeText(this, R.string.files_granted, Toast.LENGTH_SHORT).show();
            return;
        }

        try
        {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
        }
        catch (Exception e)
        {
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_MANAGE_STORAGE)
        {
            boolean granted = (Build.VERSION.SDK_INT < 30) ||
                    Environment.isExternalStorageManager();

            Toast.makeText(this,
                    granted ? R.string.files_granted : R.string.files_not_granted,
                    Toast.LENGTH_SHORT).show();
        }
    }
}
