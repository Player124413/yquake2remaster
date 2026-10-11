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
 * Touch input injection layer.
 *
 * This is the engine side of the mobile touch controls. A UI layer
 * (the Android overlay, but anything else could be used as well)
 * pushes key events, camera look deltas and virtual stick state into
 * this module. Everything is queued and drained on the client frame,
 * so the producer may live on a different thread.
 *
 * =======================================================================
 */

#include "../header/client.h"
#include "header/input.h"
#include "header/touch.h"

/*
 * Master switch, set from the launcher. When 0 the whole
 * injection layer goes silent.
 */
static cvar_t *touch_inject;

/* ------------------------------------------------------------------ */
/* Tiny portable spinlock, only held for a few stores at a time.       */
/* ------------------------------------------------------------------ */

static volatile long ti_lock_word;

static void
TI_Lock(void)
{
#ifdef _MSC_VER
	while (_InterlockedExchange(&ti_lock_word, 1))
	{
		/* spin */
	}
#else
	while (__sync_lock_test_and_set(&ti_lock_word, 1))
	{
		/* spin */
	}
#endif
}

static void
TI_Unlock(void)
{
#ifdef _MSC_VER
	_InterlockedExchange(&ti_lock_word, 0);
#else
	__sync_lock_release(&ti_lock_word);
#endif
}

/* ------------------------------------------------------------------ */
/* Event queue                                                         */
/* ------------------------------------------------------------------ */

#define TI_QUEUE_SIZE 256

typedef enum
{
	TI_EV_KEY,
	TI_EV_LOOK,
	TI_EV_MOVE,
	TI_EV_ALLUP
} ti_evtype_t;

typedef struct
{
	ti_evtype_t type;
	int key;
	qboolean down;
	float f1;
	float f2;
} ti_event_t;

static ti_event_t ti_queue[TI_QUEUE_SIZE];
static unsigned int ti_qhead; /* next slot to write */
static unsigned int ti_qtail; /* next slot to read */

/* Motion state, only touched while draining the queue. */
static float ti_look_yaw;
static float ti_look_pitch;
static float ti_move_forward;
static float ti_move_side;

/* Keys currently held down through the injection layer. */
#define TI_KEY_TRACK 512
static qboolean ti_key_held[TI_KEY_TRACK];

static void
TI_QueueEvent(const ti_event_t *ev)
{
	if (!touch_inject || !touch_inject->value)
	{
		return;
	}

	TI_Lock();

	if ((ti_qhead - ti_qtail) < TI_QUEUE_SIZE)
	{
		ti_queue[ti_qhead & (TI_QUEUE_SIZE - 1)] = *ev;
		ti_qhead++;
	}

	TI_Unlock();
}

/* Release everything we are holding. Assumes the queue is drained. */
static void
TI_ReleaseAll(void)
{
	int i;

	for (i = 0; i < TI_KEY_TRACK; i++)
	{
		if (ti_key_held[i])
		{
			ti_key_held[i] = false;
			Key_Event(i, false, true);
		}
	}

	ti_look_yaw = 0;
	ti_look_pitch = 0;
	ti_move_forward = 0;
	ti_move_side = 0;
}

/* ------------------------------------------------------------------ */
/* Public API                                                          */
/* ------------------------------------------------------------------ */

void
TouchInj_Init(void)
{
	touch_inject = Cvar_Get("touch_inject", "1", CVAR_ARCHIVE);

	ti_qhead = 0;
	ti_qtail = 0;
	ti_look_yaw = 0;
	ti_look_pitch = 0;
	ti_move_forward = 0;
	ti_move_side = 0;
	memset(ti_key_held, 0, sizeof(ti_key_held));
}

void
TouchInj_Shutdown(void)
{
	TI_Lock();
	ti_qhead = 0;
	ti_qtail = 0;
	TI_Unlock();

	TI_ReleaseAll();
}

void
TouchInj_Key(int key, qboolean down)
{
	ti_event_t ev;

	if ((key <= 0) || (key >= TI_KEY_TRACK))
	{
		return;
	}

	memset(&ev, 0, sizeof(ev));
	ev.type = TI_EV_KEY;
	ev.key = key;
	ev.down = down;

	TI_QueueEvent(&ev);
}

void
TouchInj_Look(float yaw, float pitch)
{
	ti_event_t ev;

	memset(&ev, 0, sizeof(ev));
	ev.type = TI_EV_LOOK;
	ev.f1 = yaw;
	ev.f2 = pitch;

	TI_QueueEvent(&ev);
}

void
TouchInj_Move(float forward, float side)
{
	ti_event_t ev;

	memset(&ev, 0, sizeof(ev));
	ev.type = TI_EV_MOVE;
	ev.f1 = forward;
	ev.f2 = side;

	TI_QueueEvent(&ev);
}

void
TouchInj_AllUp(void)
{
	ti_event_t ev;

	memset(&ev, 0, sizeof(ev));
	ev.type = TI_EV_ALLUP;

	TI_QueueEvent(&ev);
}

void
TouchInj_ApplyCmd(usercmd_t *cmd)
{
	ti_event_t local[TI_QUEUE_SIZE];
	unsigned int count = 0;
	unsigned int i;

	if (!touch_inject)
	{
		return;
	}

	/* Snapshot the queue so we never hold the lock while
	   calling into the client. */
	TI_Lock();

	while ((ti_qtail != ti_qhead) && (count < TI_QUEUE_SIZE))
	{
		local[count++] = ti_queue[ti_qtail & (TI_QUEUE_SIZE - 1)];
		ti_qtail++;
	}

	TI_Unlock();

	if (!touch_inject->value)
	{
		/* Silently swallow everything, but make sure nothing
		   stays stuck down. */
		if (count > 0)
		{
			TI_ReleaseAll();
		}

		return;
	}

	for (i = 0; i < count; i++)
	{
		switch (local[i].type)
		{
			case TI_EV_KEY:
			{
				int key = local[i].key;

				if (key < TI_KEY_TRACK)
				{
					if (ti_key_held[key] != local[i].down)
					{
						ti_key_held[key] = local[i].down;
						Key_Event(key, local[i].down, true);
					}
				}

				break;
			}

			case TI_EV_LOOK:
				ti_look_yaw += local[i].f1;
				ti_look_pitch += local[i].f2;
				break;

			case TI_EV_MOVE:
				ti_move_forward = local[i].f1;
				ti_move_side = local[i].f2;
				break;

			case TI_EV_ALLUP:
				TI_ReleaseAll();
				break;
		}
	}

	/* Look and movement are only applied while actually playing,
	   never while in menu, console or chat. */
	if (cls.key_dest != key_game)
	{
		ti_look_yaw = 0;
		ti_look_pitch = 0;
		return;
	}

	if (ti_look_yaw || ti_look_pitch)
	{
		/* Same conventions as mouse look: yaw delta positive
		   turns right, pitch delta positive looks down. */
		cl.viewangles[YAW] -= ti_look_yaw;
		cl.viewangles[PITCH] += ti_look_pitch;
		ti_look_yaw = 0;
		ti_look_pitch = 0;
	}

	if (cmd)
	{
		cmd->forwardmove += cl_forwardspeed->value * ti_move_forward;
		cmd->sidemove += cl_sidespeed->value * ti_move_side;
	}
}

int
TouchInj_KeyDest(void)
{
	return (int)cls.key_dest;
}

qboolean
TouchInj_Enabled(void)
{
	return (touch_inject && touch_inject->value) ? true : false;
}
