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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

/**
 * On screen touch controls: a virtual movement stick, swipe look and
 * configurable action buttons. Also doubles as the layout editor when
 * {@link #setEditMode} is enabled.
 *
 * All input is forwarded to the engine through {@link NativeLib}; nothing
 * here touches engine state directly.
 */
public class TouchControlsView extends View
{
    /* Engine key destinations (keydest_t) */
    public static final int KEY_GAME = 0;
    public static final int KEY_CONSOLE = 1;
    public static final int KEY_MESSAGE = 2;
    public static final int KEY_MENU = 3;

    /* base size of a size-1.0 button, in density independent pixels */
    private static final float BASE_BUTTON_DP = 58.0f;
    /* degrees of view rotation per pixel of look drag at sensitivity 1 */
    private static final float LOOK_BASE = 0.115f;
    /* menu gesture tuning */
    private static final float MENU_GESTURE_STEP = 48.0f;
    private static final long MENU_TAP_MAX_MS = 350;
    private static final float MENU_TAP_SLOP = 22.0f;

    private static final int ROLE_NONE = 0;
    private static final int ROLE_BUTTON = 1;
    private static final int ROLE_STICK = 2;
    private static final int ROLE_LOOK = 3;
    private static final int ROLE_MENU = 4;

    private final Prefs prefs;
    private final TouchLayout layout;

    private boolean editMode = false;
    private TouchLayout.Button selected = null;

    private float density;
    private boolean overlayEnabled;

    private static final class Pointer
    {
        int role = ROLE_NONE;
        TouchLayout.Button button;
        float x, y;          /* last position */
        float startX, startY;/* down position */
        float accX, accY;    /* gesture accumulators (menu) */
        long startTime;
    }

    private final SparseArray<Pointer> pointers = new SparseArray<>();

    /* stick state */
    private int stickPointerId = -1;
    private float stickBaseX, stickBaseY;
    private float stickThumbX, stickThumbY;

    /* pinch resize state (editor) */
    private int pinchPointerId = -1;
    private float pinchStartDist = 0, pinchStartSize = 1;

    /* paints */
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final Vibrator vibrator;

    public interface OnSelectionListener
    {
        void onSelected(TouchLayout.Button b);
    }

    private OnSelectionListener selectionListener;

    public TouchControlsView(Context context, Prefs prefs, TouchLayout layout)
    {
        super(context);
        this.prefs = prefs;
        this.layout = layout;
        this.density = context.getResources().getDisplayMetrics().density;

        overlayEnabled = prefs.touchEnabled();

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2.0f * density);
        strokePaint.setColor(Color.WHITE);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setShadowLayer(2.0f * density, 0, 1.0f * density, 0x80000000);

        selPaint.setStyle(Paint.Style.STROKE);
        selPaint.setStrokeWidth(3.0f * density);
        selPaint.setColor(0xFFFFC107);

        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        setClickable(true);
    }

    public void setEditMode(boolean edit)
    {
        this.editMode = edit;
        releaseEverything();
    }

    public void setSelectionListener(OnSelectionListener l)
    {
        this.selectionListener = l;
    }

    public TouchLayout.Button getSelected()
    {
        return selected;
    }

    public void select(TouchLayout.Button b)
    {
        selected = b;
        invalidate();
    }

    public void reload()
    {
        overlayEnabled = prefs.touchEnabled();
        invalidate();
    }

    /* ---------------------------------------------------------------- */
    /* Geometry helpers                                                 */
    /* ---------------------------------------------------------------- */

    private float buttonRadius(TouchLayout.Button b)
    {
        float base = BASE_BUTTON_DP * density;
        return base * 0.5f * prefs.touchScale() * b.size;
    }

    private float buttonCenterX(TouchLayout.Button b)
    {
        return b.x * getWidth();
    }

    private float buttonCenterY(TouchLayout.Button b)
    {
        return b.y * getHeight();
    }

    private float stickRadius()
    {
        return BASE_BUTTON_DP * density * 1.35f * prefs.touchScale() * prefs.stickSize();
    }

    private void sendStick(float fx, float fy)
    {
        if (!editMode)
        {
            NativeLib.nativeStick(fx, fy);
        }
    }

    private void sendLook(float dx, float dy)
    {
        if (editMode)
        {
            return;
        }

        float sens = prefs.lookSensitivity() * LOOK_BASE;
        float yaw = dx * sens;
        float pitch = dy * sens;

        if (prefs.invertY())
        {
            pitch = -pitch;
        }

        NativeLib.nativeLook(yaw, pitch);
    }

    private void pressKey(String key, boolean down)
    {
        if (!editMode)
        {
            NativeLib.nativeKey(key, down);
        }
    }

    private void tapKey(String key)
    {
        pressKey(key, true);
        pressKey(key, false);
    }

    private void vibrate(int ms)
    {
        if (!prefs.vibrate() || vibrator == null || !vibrator.hasVibrator())
        {
            return;
        }

        if (Build.VERSION.SDK_INT >= 26)
        {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        }
        else
        {
            vibrator.vibrate(ms);
        }
    }

    private int currentKeyDest()
    {
        if (editMode)
        {
            return KEY_GAME;
        }

        try
        {
            return NativeLib.nativeKeyDest();
        }
        catch (UnsatisfiedLinkError e)
        {
            return KEY_GAME;
        }
    }

    /* ---------------------------------------------------------------- */
    /* Hit testing                                                      */
    /* ---------------------------------------------------------------- */

    private TouchLayout.Button hitButton(float x, float y)
    {
        TouchLayout.Button best = null;
        float bestDist = Float.MAX_VALUE;

        for (TouchLayout.Button b : layout.buttons)
        {
            if (!editMode && !b.visible)
            {
                continue;
            }

            float dx = x - buttonCenterX(b);
            float dy = y - buttonCenterY(b);
            float r = buttonRadius(b) * 1.3f;

            if (dx * dx + dy * dy <= r * r)
            {
                float d = dx * dx + dy * dy;

                if (d < bestDist)
                {
                    bestDist = d;
                    best = b;
                }
            }
        }

        return best;
    }

    private boolean isOnStick(float x, float y)
    {
        if (stickPointerId != -1)
        {
            return false;
        }

        if (prefs.stickMode() == 1)
        {
            /* floating stick: whole left half of the screen */
            return x < getWidth() * 0.45f;
        }

        /* fixed stick: near the base only */
        float r = stickRadius() * 1.7f;
        float dx = x - stickBaseX;
        float dy = y - stickBaseY;
        return dx * dx + dy * dy <= r * r;
    }

    private void updateStick(float x, float y)
    {
        float r = stickRadius();
        float dx = x - stickBaseX;
        float dy = y - stickBaseY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);

        if (len > r)
        {
            dx = dx / len * r;
            dy = dy / len * r;
        }

        stickThumbX = stickBaseX + dx;
        stickThumbY = stickBaseY + dy;

        float nx = dx / r;
        float ny = dy / r;
        float dead = prefs.stickDeadzone();
        float mag = (float) Math.sqrt(nx * nx + ny * ny);

        if (mag < dead)
        {
            sendStick(0, 0);
        }
        else
        {
            float scale = (mag - dead) / (1.0f - dead) / mag;
            sendStick(nx * scale, -ny * scale);
        }

        invalidate();
    }

    /* ---------------------------------------------------------------- */
    /* Touch handling                                                   */
    /* ---------------------------------------------------------------- */

    @Override
    public boolean onTouchEvent(MotionEvent ev)
    {
        if (!overlayEnabled && !editMode)
        {
            return false;
        }

        switch (ev.getActionMasked())
        {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                return handleDown(ev);

            case MotionEvent.ACTION_MOVE:
                handleMove(ev);
                return true;

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                handleUp(ev);
                return true;

            case MotionEvent.ACTION_CANCEL:
                releaseEverything();
                return true;
        }

        return true;
    }

    private boolean handleDown(MotionEvent ev)
    {
        int idx = ev.getActionIndex();
        int id = ev.getPointerId(idx);
        float x = ev.getX(idx);
        float y = ev.getY(idx);

        Pointer p = new Pointer();
        p.x = x;
        p.y = y;
        p.startX = x;
        p.startY = y;
        p.startTime = ev.getEventTime();
        pointers.put(id, p);

        if (editMode)
        {
            TouchLayout.Button b = hitButton(x, y);

            if (b != null)
            {
                select(b);
                p.role = ROLE_LOOK;   /* drag moves the selected button */
            }
            else if (selected != null)
            {
                /* empty area: start a pinch resize */
                p.role = ROLE_STICK;
                pinchPointerId = id;
                beginPinchFromCurrent(ev);
            }

            return true;
        }

        int keyDest = currentKeyDest();

        if (keyDest == KEY_CONSOLE || keyDest == KEY_MESSAGE)
        {
            /* over the console only explicit buttons are handled,
               everything else passes through to the engine surface
               below (which pops up the soft keyboard) */
            TouchLayout.Button b = hitButton(x, y);

            if (b != null && b.visible)
            {
                p.role = ROLE_BUTTON;
                p.button = b;
                pressKey(b.key, true);
                vibrate(16);
                invalidate();
                return true;
            }

            pointers.remove(id);
            return false;
        }

        if (keyDest == KEY_MENU)
        {
            p.role = ROLE_MENU;
            return true;
        }

        /* in game */
        TouchLayout.Button b = hitButton(x, y);

        if (b != null && b.visible)
        {
            p.role = ROLE_BUTTON;
            p.button = b;
            pressKey(b.key, true);
            vibrate(16);
            invalidate();
            return true;
        }

        if (isOnStick(x, y))
        {
            p.role = ROLE_STICK;
            stickPointerId = id;

            if (prefs.stickMode() == 1)
            {
                /* floating stick appears under the finger */
                stickBaseX = x;
                stickBaseY = y;
            }

            stickThumbX = x;
            stickThumbY = y;
            updateStick(x, y);
            return true;
        }

        p.role = ROLE_LOOK;
        return true;
    }

    private void handleMove(MotionEvent ev)
    {
        /* editor pinch */
        if (editMode && pinchPointerId != -1 && selected != null)
        {
            Pointer a = pointers.get(pinchPointerId);
            Pointer b = null;

            for (int i = 0; i < pointers.size(); i++)
            {
                int id = pointers.keyAt(i);

                if (id != pinchPointerId)
                {
                    b = pointers.get(id);
                    break;
                }
            }

            if (a != null && b != null)
            {
                float dx = a.x - b.x;
                float dy = a.y - b.y;
                float d = (float) Math.sqrt(dx * dx + dy * dy);

                if (pinchStartDist > 1)
                {
                    selected.size = Math.max(0.3f, Math.min(3.0f,
                            pinchStartSize * (d / pinchStartDist)));
                    invalidate();
                }
            }
        }

        for (int i = 0; i < ev.getPointerCount(); i++)
        {
            int id = ev.getPointerId(i);
            float x = ev.getX(i);
            float y = ev.getY(i);
            Pointer p = pointers.get(id);

            if (p == null)
            {
                continue;
            }

            float dx = x - p.x;
            float dy = y - p.y;
            p.x = x;
            p.y = y;

            switch (p.role)
            {
                case ROLE_STICK:
                    if (!editMode)
                    {
                        updateStick(x, y);
                    }
                    break;

                case ROLE_LOOK:
                    if (editMode)
                    {
                        if (selected != null)
                        {
                            selected.x = clamp01(selected.x + dx / getWidth());
                            selected.y = clamp01(selected.y + dy / getHeight());
                            invalidate();
                        }
                    }
                    else
                    {
                        sendLook(dx, dy);
                    }
                    break;

                case ROLE_MENU:
                {
                    float totalX = x - p.startX;
                    float totalY = y - p.startY;

                    if (Math.abs(totalX) > MENU_TAP_SLOP || Math.abs(totalY) > MENU_TAP_SLOP)
                    {
                        /* swipe: emit arrow key steps */
                        while (totalY - p.accY > MENU_GESTURE_STEP)
                        {
                            tapKey("DOWNARROW");
                            p.accY += MENU_GESTURE_STEP;
                        }

                        while (totalY - p.accY < -MENU_GESTURE_STEP)
                        {
                            tapKey("UPARROW");
                            p.accY -= MENU_GESTURE_STEP;
                        }

                        while (totalX - p.accX > MENU_GESTURE_STEP)
                        {
                            tapKey("RIGHTARROW");
                            p.accX += MENU_GESTURE_STEP;
                        }

                        while (totalX - p.accX < -MENU_GESTURE_STEP)
                        {
                            tapKey("LEFTARROW");
                            p.accX -= MENU_GESTURE_STEP;
                        }
                    }
                    break;
                }

                case ROLE_BUTTON:
                {
                    TouchLayout.Button b = p.button;

                    if (b != null)
                    {
                        float bx = x - buttonCenterX(b);
                        float by = y - buttonCenterY(b);
                        float r = buttonRadius(b) * 1.6f;

                        if (bx * bx + by * by > r * r)
                        {
                            pressKey(b.key, false);
                            p.role = ROLE_NONE;
                            p.button = null;
                            invalidate();
                        }
                    }
                    break;
                }
            }
        }
    }

    private void handleUp(MotionEvent ev)
    {
        int idx = ev.getActionIndex();
        int id = ev.getPointerId(idx);
        Pointer p = pointers.get(id);

        if (p != null)
        {
            switch (p.role)
            {
                case ROLE_BUTTON:
                    if (p.button != null)
                    {
                        pressKey(p.button.key, false);
                        invalidate();
                    }
                    break;

                case ROLE_STICK:
                    if (!editMode && id == stickPointerId)
                    {
                        stickPointerId = -1;
                        sendStick(0, 0);
                        invalidate();
                    }
                    break;

                case ROLE_MENU:
                {
                    float totalX = p.x - p.startX;
                    float totalY = p.y - p.startY;

                    if (Math.abs(totalX) < MENU_TAP_SLOP && Math.abs(totalY) < MENU_TAP_SLOP &&
                        (ev.getEventTime() - p.startTime) < MENU_TAP_MAX_MS)
                    {
                        tapKey("ENTER");
                    }
                    break;
                }
            }
        }

        if (editMode && id == pinchPointerId)
        {
            pinchPointerId = -1;
            pinchStartDist = 0;
        }

        pointers.remove(id);

        if (ev.getActionMasked() == MotionEvent.ACTION_UP)
        {
            /* last pointer: make sure nothing stays stuck */
            if (!editMode)
            {
                sendStick(0, 0);
            }

            stickPointerId = -1;
            pinchPointerId = -1;
        }
    }

    private void beginPinchFromCurrent(MotionEvent ev)
    {
        if (ev.getPointerCount() >= 2)
        {
            float dx = ev.getX(0) - ev.getX(1);
            float dy = ev.getY(0) - ev.getY(1);
            pinchStartDist = (float) Math.sqrt(dx * dx + dy * dy);
            pinchStartSize = (selected != null) ? selected.size : 1.0f;
        }
    }

    private void releaseEverything()
    {
        for (int i = 0; i < pointers.size(); i++)
        {
            Pointer p = pointers.valueAt(i);

            if (p.role == ROLE_BUTTON && p.button != null)
            {
                pressKey(p.button.key, false);
            }
        }

        pointers.clear();
        stickPointerId = -1;
        pinchPointerId = -1;
        sendStick(0, 0);
        invalidate();
    }

    private static float clamp01(float v)
    {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    /* ---------------------------------------------------------------- */
    /* Drawing                                                          */
    /* ---------------------------------------------------------------- */

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);

        float alpha = prefs.touchAlpha();
        int keyDest = currentKeyDest();

        if (editMode || (prefs.touchEnabled() && keyDest == KEY_GAME))
        {
            drawStick(canvas, alpha);
        }

        for (TouchLayout.Button b : layout.buttons)
        {
            if (!editMode && !b.visible)
            {
                continue;
            }

            drawButton(canvas, b, alpha);
        }

        if (editMode && selected != null)
        {
            float r = buttonRadius(selected) + 7.0f * density;
            canvas.drawCircle(buttonCenterX(selected), buttonCenterY(selected), r, selPaint);
        }
    }

    private void drawStick(Canvas canvas, float alpha)
    {
        float r = stickRadius();
        float cx = (stickPointerId != -1) ? stickBaseX : (getWidth() * 0.13f);
        float cy = (stickPointerId != -1) ? stickBaseY : (getHeight() * 0.72f);
        float tx = (stickPointerId != -1) ? stickThumbX : cx;
        float ty = (stickPointerId != -1) ? stickThumbY : cy;

        int a = (int) (alpha * 255 * 0.45f);
        fillPaint.setColor(Color.argb(a, 20, 20, 24));
        canvas.drawCircle(cx, cy, r, fillPaint);

        strokePaint.setColor(Color.argb((int) (alpha * 255 * 0.7f), 255, 255, 255));
        canvas.drawCircle(cx, cy, r, strokePaint);

        fillPaint.setColor(Color.argb((int) (alpha * 255 * 0.75f), 240, 240, 255));
        canvas.drawCircle(tx, ty, r * 0.45f, fillPaint);
    }

    private void drawButton(Canvas canvas, TouchLayout.Button b, float globalAlpha)
    {
        float r = buttonRadius(b);
        float cx = buttonCenterX(b);
        float cy = buttonCenterY(b);
        float a = globalAlpha * b.alpha;

        if (!b.visible)
        {
            a *= 0.3f;
        }

        rect.set(cx - r, cy - r, cx + r, cy + r);
        float rr = r * 0.35f;

        fillPaint.setColor(Color.argb((int) (a * 255 * 0.55f), 30, 32, 40));
        canvas.drawRoundRect(rect, rr, rr, fillPaint);

        strokePaint.setColor(Color.argb((int) (a * 255), 255, 255, 255));
        canvas.drawRoundRect(rect, rr, rr, strokePaint);

        textPaint.setTextSize(r * 0.52f);
        textPaint.setAlpha((int) (a * 255));
        float textY = cy - (textPaint.descent() + textPaint.ascent()) / 2;
        canvas.drawText(b.label, cx, textY, textPaint);
    }
}
