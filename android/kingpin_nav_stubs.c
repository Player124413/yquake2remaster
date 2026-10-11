/*
 * Safe Android fallback for the Kingpin Character Navigation
 * System. The original CNS implementation is proprietary and only shipped
 * for x86. These definitions intentionally disable route finding, but keep
 * the game's navigation globals initialized and make every missing function
 * obey its declared C ABI.
 *
 * The old build script emitted generic `void fn(void)` functions for every
 * unresolved NAV symbol. Calls through those mismatched signatures invoked
 * undefined behavior: non-void functions returned indeterminate values, and
 * the first single-player ClientThink could dereference an uninitialized
 * nav_dynamic cvar pointer. GetGameAPI registers the four cvars before game
 * initialization; these stubs then fail closed when navigation is requested.
 */
#include "g_local.h"

#include <string.h>

void
NAV_InitActiveNodes(active_node_data_t *active_node_data)
{
	if (active_node_data)
	{
		memset(active_node_data, 0, sizeof(*active_node_data));
	}
}

void
NAV_PurgeActiveNodes(active_node_data_t *active_node_data)
{
	if (active_node_data)
	{
		memset(active_node_data, 0, sizeof(*active_node_data));
	}
}

node_t *
NAV_CreateNode(edict_t *ent, vec3_t origin, vec3_t jump_vel,
		short node_type, short goal_index, int waterlevel)
{
	(void)ent;
	(void)origin;
	(void)jump_vel;
	(void)node_type;
	(void)goal_index;
	(void)waterlevel;
	return NULL;
}

int
NAV_OptimizeRoutes(active_node_data_t *active_node_data)
{
	(void)active_node_data;
	return 0;
}

void
NAV_CalculateVisible(active_node_data_t *active_node_data, node_t *node)
{
	(void)active_node_data;
	(void)node;
}

int
NAV_CalculateRoutes(active_node_data_t *active_node_data, node_t *node)
{
	(void)active_node_data;
	(void)node;
	return 0;
}

qboolean
NAV_Visible(vec3_t src, vec3_t dest, int vis_type, int ducking)
{
	(void)src;
	(void)dest;
	(void)vis_type;
	(void)ducking;
	return 0;
}

qboolean
NAV_Reachable(vec3_t src, vec3_t dest, byte src_waterlevel,
		byte dest_waterlevel, int ducking, int reachable_type)
{
	(void)src;
	(void)dest;
	(void)src_waterlevel;
	(void)dest_waterlevel;
	(void)ducking;
	(void)reachable_type;
	return 0;
}

qboolean
NAV_ClearSight(edict_t *self, vec3_t dest, edict_t *dest_ent)
{
	(void)self;
	(void)dest;
	(void)dest_ent;
	return 0;
}

void
NAV_CalcNodeSurface(node_t *node)
{
	(void)node;
}

node_t *
NAV_GetClosestNode(edict_t *ent, int vis_type, int ignore_cached,
		qboolean away_from_enemy)
{
	(void)ent;
	(void)vis_type;
	(void)ignore_cached;
	(void)away_from_enemy;
	return NULL;
}

int
NAV_Route_EntityToEntity(edict_t *src, node_t *current_node, edict_t *dest,
		int vis_type, int check_all_nodes, route_t *route_out)
{
	(void)src;
	(void)current_node;
	(void)dest;
	(void)vis_type;
	(void)check_all_nodes;
	if (route_out)
	{
		memset(route_out, 0, sizeof(*route_out));
		route_out->path = -1;
	}
	return 0;
}

int
NAV_Route_NodeToEntity(node_t *node, edict_t *dest, int vis_type,
		route_t *route_out)
{
	(void)node;
	(void)dest;
	(void)vis_type;
	if (route_out)
	{
		memset(route_out, 0, sizeof(*route_out));
		route_out->path = -1;
	}
	return 0;
}

int
NAV_Route_EntityToNode(edict_t *src, node_t *current_node,
		node_t *dest_node, int vis_type, int check_all_nodes,
		int check_direct, route_t *route_out)
{
	(void)src;
	(void)current_node;
	(void)dest_node;
	(void)vis_type;
	(void)check_all_nodes;
	(void)check_direct;
	if (route_out)
	{
		memset(route_out, 0, sizeof(*route_out));
		route_out->path = -1;
	}
	return 0;
}

void
NAV_EvaluateMove(edict_t *ent)
{
	(void)ent;
}

void
NAV_WriteActiveNodes(active_node_data_t *active_node_data, char *unitname)
{
	(void)active_node_data;
	(void)unitname;
}

void
NAV_WriteNode(FILE *f, active_node_data_t *active_node_data, node_t *node)
{
	(void)f;
	(void)active_node_data;
	(void)node;
}

void
NAV_ReadActiveNodes(active_node_data_t *active_node_data, char *unitname)
{
	(void)unitname;
	NAV_InitActiveNodes(active_node_data);
}

void
NAV_ReadNode(FILE *f, active_node_data_t *active_node_data, node_t *node)
{
	(void)f;
	(void)active_node_data;
	(void)node;
}

void
NAV_FindGoalEnt(node_t *node)
{
	(void)node;
}

void
NAV_CalculateDistances(active_node_data_t *active_node_data)
{
	(void)active_node_data;
}

int
NAV_CalculateRouteDistance(active_node_data_t *active_node_data,
		node_t *src, node_t *dest)
{
	(void)active_node_data;
	(void)src;
	(void)dest;
	return 0;
}

int
NAV_GetCellIndexForAxis(float pos)
{
	(void)pos;
	return 0;
}

void
NAV_AddNodeToCells(active_node_data_t *active_node_data, node_t *node)
{
	(void)active_node_data;
	(void)node;
}

void
NAV_DrawLine(vec3_t sorg, vec3_t dorg)
{
	(void)sorg;
	(void)dorg;
}

float
NAV_Debug_DrawPath(edict_t *src, edict_t *dest)
{
	(void)src;
	(void)dest;
	return 0.0f;
}

void
NAV_dprintf(char *fmt, ...)
{
	(void)fmt;
}

void
NAV_RebuildRoutes(active_node_data_t *node_data)
{
	(void)node_data;
}

float *
NAV_GetCombatPos(edict_t *ent, edict_t *enemy, qboolean melee)
{
	(void)ent;
	(void)enemy;
	(void)melee;
	return NULL;
}

float *
NAV_GetHidePos(edict_t *ent, edict_t *enemy, int hidepos_type)
{
	(void)ent;
	(void)enemy;
	(void)hidepos_type;
	return NULL;
}

qboolean
NAV_GetAvoidDirection(edict_t *ent, edict_t *avoid, vec3_t dir)
{
	(void)ent;
	(void)avoid;
	VectorClear(dir);
	return 0;
}

float *
NAV_GetReachableNodeOutsideBounds(edict_t *ent, vec3_t bmins, vec3_t bmaxs)
{
	(void)ent;
	(void)bmins;
	(void)bmaxs;
	return NULL;
}
