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
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Drag & drop editor for the on screen controls. Drag buttons to move
 * them, use two fingers to pinch resize, toggle visibility and fine tune
 * the size from the bottom bar.
 */
public class TouchEditorActivity extends Activity
{
    private Prefs prefs;
    private TouchLayout layout;
    private TouchControlsView touchView;
    private TextView selectedLabel;
    private SeekBar sizeSeek;
    private CheckBox visibleBox;

    private boolean updatingUi = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_touch_editor);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefs = new Prefs(this);
        layout = new TouchLayout();
        layout.load(prefs);

        /* place the editor canvas between the background and the bar */
        ViewGroup content = findViewById(android.R.id.content);

        touchView = new TouchControlsView(this, prefs, layout);
        touchView.setEditMode(true);
        content.addView(touchView, 0, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        selectedLabel = findViewById(R.id.editor_selected_label);
        sizeSeek = findViewById(R.id.editor_size);
        visibleBox = findViewById(R.id.editor_visible);

        touchView.setSelectionListener(new TouchControlsView.OnSelectionListener()
        {
            @Override
            public void onSelected(TouchLayout.Button b)
            {
                updateUiFor(b);
            }
        });

        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser)
            {
                if (updatingUi)
                {
                    return;
                }

                TouchLayout.Button b = touchView.getSelected();

                if (b != null)
                {
                    b.size = (30 + progress) / 100.0f;
                    touchView.invalidate();
                }
            }

            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });

        visibleBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            public void onCheckedChanged(CompoundButton b, boolean on)
            {
                if (updatingUi)
                {
                    return;
                }

                TouchLayout.Button sel = touchView.getSelected();

                if (sel != null)
                {
                    sel.visible = on;
                    touchView.invalidate();
                }
            }
        });

        findViewById(R.id.editor_reset).setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                layout.reset(prefs);
                touchView.select(null);
                updateUiFor(null);
                touchView.invalidate();
            }
        });

        findViewById(R.id.editor_save).setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                layout.save(prefs);
                finish();
            }
        });

        updateUiFor(null);
    }

    private void updateUiFor(TouchLayout.Button b)
    {
        updatingUi = true;

        if (b == null)
        {
            selectedLabel.setText(R.string.title_touch_editor);
            sizeSeek.setEnabled(false);
            visibleBox.setEnabled(false);
        }
        else
        {
            selectedLabel.setText(b.label + "  [" + b.key + "]");
            sizeSeek.setEnabled(true);
            visibleBox.setEnabled(true);
            sizeSeek.setProgress(Math.round(b.size * 100) - 30);
            visibleBox.setChecked(b.visible);
        }

        updatingUi = false;
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        /* keep the layout even when leaving without pressing Save */
        layout.save(prefs);
    }
}
