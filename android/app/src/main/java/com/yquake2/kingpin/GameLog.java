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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Append-only diagnostic log shared by the launcher and the engine.
 *
 * The launcher writes its events through {@link #log}; the engine writes
 * its console output into the same file through JNI (see
 * Android_LogFileWrite in src/backends/unix/android_jni.c, opened by
 * NativeLib.nativeLogOpen() before SDL_main runs). The launcher shows
 * the whole file in a dialog with a copy-to-clipboard button.
 */
public final class GameLog
{
    private static final String TAG = "Kingpin";
    private static final String NAME = "kingpin.log";
    private static final int READ_CAP = 400 * 1024;

    private static boolean installed;

    private GameLog()
    {
    }

    /** The shared log file. Lives in app-external storage so it can also
     *  be grabbed with a file manager, internal storage as fallback. */
    public static File file(Context ctx)
    {
        File dir = ctx.getExternalFilesDir(null);

        if (dir == null)
        {
            dir = ctx.getFilesDir();
        }

        return new File(dir, NAME);
    }

    /** Install the crash logger. Safe to call from every activity. */
    public static synchronized void init(Context ctx)
    {
        cached = file(ctx);

        if (installed)
        {
            return;
        }

        installed = true;

        final Thread.UncaughtExceptionHandler prev =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler()
        {
            @Override
            public void uncaughtException(Thread t, Throwable e)
            {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                log("FATAL " + t.getName() + ": " + sw);

                if (prev != null)
                {
                    prev.uncaughtException(t, e);
                }
            }
        });
    }

    /** Append one timestamped line to the log. */
    public static void log(String msg)
    {
        String line = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date()) + " " + msg + "\n";

        Log.i(TAG, msg);

        if (cached == null)
        {
            /* init() not called yet, keep it in logcat only */
            return;
        }

        try
        {
            FileOutputStream out = new FileOutputStream(cached, true);
            out.write(line.getBytes("UTF-8"));
            out.close();
        }
        catch (Exception ignored)
        {
            /* logging must never break the app */
        }
    }

    /** Whole log as text, newest last. */
    public static String read(Context ctx)
    {
        try
        {
            File f = file(ctx);
            long size = f.length();

            if (size == 0)
            {
                return "";
            }

            FileInputStream in = new FileInputStream(f);

            if (size > READ_CAP)
            {
                /* only the tail is interesting */
                long skipped = 0;

                while (skipped < size - READ_CAP)
                {
                    long n = in.skip(size - READ_CAP - skipped);

                    if (n <= 0)
                    {
                        break;
                    }

                    skipped += n;
                }
            }

            byte[] buf = new byte[(int) Math.min(size, READ_CAP)];
            int off = 0;

            while (off < buf.length)
            {
                int n = in.read(buf, off, buf.length - off);

                if (n < 0)
                {
                    break;
                }

                off += n;
            }

            in.close();
            return new String(buf, 0, off, "UTF-8");
        }
        catch (Exception e)
        {
            return "(failed to read the log: " + e + ")";
        }
    }

    /** Truncate the log. */
    public static void clear(Context ctx)
    {
        try
        {
            new FileOutputStream(file(ctx), false).close();
        }
        catch (Exception ignored)
        {
        }
    }

    /** Copy text into the system clipboard. */
    public static void copy(Context ctx, String text)
    {
        ClipboardManager cm =
                (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);

        if (cm != null)
        {
            cm.setPrimaryClip(ClipData.newPlainText("Kingpin log", text));
        }
    }

    private static File cached;
}
