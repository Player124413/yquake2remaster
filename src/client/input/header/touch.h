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
 * Feeds touch based controls (virtual sticks, on screen buttons, swipe
 * look) into the client from an external UI layer, e.g. the Android
 * overlay drawn on top of the OpenGL surface. All entry points are
 * thread safe: events are queued and drained on the client frame in
 * TouchInj_ApplyCmd(), which must be called right after IN_Move().
 *
 * =======================================================================
 */

#ifndef TOUCH_INJECT_H
#define TOUCH_INJECT_H

#include "../../../common/header/shared.h"

/*
 * Called from CL_Init. Sets up internal state and the
 * touch_inject cvar (set to 0 to ignore all injected
 * events, e.g. when the launcher disabled touch input).
 */
void TouchInj_Init(void);

/*
 * Called from CL_Shutdown.
 */
void TouchInj_Shutdown(void);

/*
 * Drains the pending event queue and applies the current
 * look/movement state to the usercmd. Called from the
 * client frame (CL_CreateCmd / CL_RefreshMove), directly
 * after IN_Move().
 */
void TouchInj_ApplyCmd(usercmd_t *cmd);

/*
 * Queue a virtual key press/release. "key" is one of the
 * K_* values from keyboard.h. Thread safe.
 */
void TouchInj_Key(int key, qboolean down);

/*
 * Queue camera look movement. Positive yaw turns right,
 * positive pitch looks down, both in degrees. Thread safe.
 */
void TouchInj_Look(float yaw, float pitch);

/*
 * Set the state of the virtual movement stick. Forward and
 * side are in the range [-1, 1], forward positive is
 * "walk forward", side positive is "strafe right". The
 * state stays active until changed. Thread safe.
 */
void TouchInj_Move(float forward, float side);

/*
 * Release everything that is currently held down via the
 * injection layer and zero all motion state. Thread safe.
 */
void TouchInj_AllUp(void);

/*
 * Returns the current key destination (keydest_t) so a
 * touch overlay can switch between game and menu handling.
 */
int TouchInj_KeyDest(void);

/*
 * Touch input master switch, mirrors the touch_enable cvar.
 */
qboolean TouchInj_Enabled(void);

#endif
