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
 *
 * =======================================================================
 *
 * JNI bridge between the Android launcher / touch overlay and the
 * engine. All native methods belong to com.yquake2.kingpin.NativeLib
 * and are resolved automatically by name. Everything is forwarded to
 * the thread safe touch injection layer, no client state is touched
 * from the UI thread.
 *
 * =======================================================================
 */

#ifdef __ANDROID__

#include <jni.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include "../../common/header/common.h"
#include "../../client/header/client.h"
#include "../../client/input/header/touch.h"

/* Key name ("MOUSE1", "SPACE", ...) to K_* resolution. */
int Key_StringToKeynum(const char *str);

/* ---- shared diagnostic log ------------------------------------------ */

JNIEXPORT void JNICALL
Java_com_yquake2_kingpin_NativeLib_nativeLogOpen(JNIEnv *env, jclass cls,
		jstring path)
{
	const char *p;
	int fd;

	if (path == NULL)
	{
		return;
	}

	p = (*env)->GetStringUTFChars(env, path, NULL);

	if (p == NULL)
	{
		return;
	}

	/* Point stdout/stderr at the shared log file. The engine also
	   redirects via -logfile (backends/unix/main.c) once SDL_main is
	   running; this captures anything earlier and survives a missing
	   argument. */
	fd = open(p, O_WRONLY | O_CREAT | O_APPEND, 0666);

	if (fd >= 0)
	{
		dup2(fd, 1);
		dup2(fd, 2);

		if (fd > 2)
		{
			close(fd);
		}

		setvbuf(stdout, NULL, _IOLBF, 1024);
		setvbuf(stderr, NULL, _IOLBF, 1024);
	}

	(*env)->ReleaseStringUTFChars(env, path, p);
}

/* --------------------------------------------------------------------- */

JNIEXPORT void JNICALL
Java_com_yquake2_kingpin_NativeLib_nativeInit(JNIEnv *env, jclass cls,
		jstring libdir)
{
	const char *dir;
	char buf[MAX_OSPATH];

	if (libdir == NULL)
	{
		return;
	}

	dir = (*env)->GetStringUTFChars(env, libdir, NULL);

	if (dir == NULL)
	{
		return;
	}

	Q_strlcpy(buf, dir, sizeof(buf));
	(*env)->ReleaseStringUTFChars(env, libdir, dir);

	/* Point Sys_GetBinaryDir() at the APK native library dir,
	   that is where the renderer modules (ref_gles3.so etc.) live. */
	Sys_SetBinaryDir(buf);
}

JNIEXPORT void JNICALL
Java_com_yquake2_kingpin_NativeLib_nativeKey(JNIEnv *env, jclass cls,
		jstring name, jboolean down)
{
	const char *str;
	int key;

	if (name == NULL)
	{
		return;
	}

	str = (*env)->GetStringUTFChars(env, name, NULL);

	if (str == NULL)
	{
		return;
	}

	key = Key_StringToKeynum(str);
	(*env)->ReleaseStringUTFChars(env, name, str);

	if (key > 0)
	{
		TouchInj_Key(key, down ? true : false);
	}
}

JNIEXPORT void JNICALL
Java_com_yquake2_kingpin_NativeLib_nativeLook(JNIEnv *env, jclass cls,
		jfloat yaw, jfloat pitch)
{
	TouchInj_Look(yaw, pitch);
}

JNIEXPORT void JNICALL
Java_com_yquake2_kingpin_NativeLib_nativeStick(JNIEnv *env, jclass cls,
		jfloat forward, jfloat side)
{
	TouchInj_Move(forward, side);
}

JNIEXPORT void JNICALL
Java_com_yquake2_kingpin_NativeLib_nativeAllUp(JNIEnv *env, jclass cls)
{
	TouchInj_AllUp();
}

JNIEXPORT jint JNICALL
Java_com_yquake2_kingpin_NativeLib_nativeKeyDest(JNIEnv *env, jclass cls)
{
	return (jint)TouchInj_KeyDest();
}

JNIEXPORT jint JNICALL
Java_com_yquake2_kingpin_NativeLib_nativeTouchEnabled(JNIEnv *env, jclass cls)
{
	return TouchInj_Enabled() ? 1 : 0;
}

JNIEXPORT void JNICALL
Java_com_yquake2_kingpin_NativeLib_nativeConsoleCmd(JNIEnv *env, jclass cls,
		jstring cmd)
{
	const char *str;
	char buf[256];

	if (cmd == NULL)
	{
		return;
	}

	str = (*env)->GetStringUTFChars(env, cmd, NULL);

	if (str == NULL)
	{
		return;
	}

	Q_strlcpy(buf, str, sizeof(buf));
	(*env)->ReleaseStringUTFChars(env, cmd, str);

	Cbuf_AddText(buf);
	Cbuf_AddText("\n");
}

#endif /* __ANDROID__ */
