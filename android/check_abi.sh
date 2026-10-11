#!/bin/sh
#
# Verify that the engine headers and the patched Kingpin SDK headers
# agree on the game module ABI (field offsets and struct sizes).
#
# Run this after changing shared.h / game.h or kingpin_sdk_abi.patch.
# Needs a host C compiler and python3. Downloads the SDK on demand (the
# same non-free third party code build_game_lib.sh uses).
#
set -e

cd "$(dirname "$0")"
REPO="$(cd .. && pwd)"
PATCH="$PWD/kingpin_sdk_abi.patch"

WORK="${TMPDIR:-/tmp}/kingpin-abi-check-$$"
mkdir -p "$WORK"
trap 'rm -rf "$WORK"' EXIT

SDK_TAG="${SDK_TAG:-master}"
SDK_URL="${SDK_URL:-https://codeload.github.com/QuakeTools/Kingpin-SDK-v1.21/tar.gz/refs/heads/${SDK_TAG}}"

if [ -d "$PWD/.kingpin-game-build/gamesrc" ] && [ -f "$PWD/.kingpin-game-build/gamesrc/game.h" ]; then
    echo "==> Reusing .kingpin-game-build/gamesrc (already patched)"
    SDK_GAMESRC="$PWD/.kingpin-game-build/gamesrc"
else
    echo "==> Downloading the Kingpin SDK..."
    curl -L -o "$WORK/sdk.tar.gz" "$SDK_URL"
    tar -xzf "$WORK/sdk.tar.gz" -C "$WORK"
    SDK_SRC="$(find "$WORK" -maxdepth 1 -mindepth 1 -type d | head -n 1)"
    ( cd "$SDK_SRC" && patch -p1 < "$PATCH" >/dev/null )
    # lowercase header aliases like build_game_lib.sh does
    ( cd "$SDK_SRC/gamesrc" && for f in *.H *.h; do
        [ -f "$f" ] || continue
        lc="$(printf '%s' "$f" | tr 'A-Z' 'a-z')"
        [ "$lc" = "$f" ] || [ -e "$lc" ] || ln -s "$f" "$lc"
    done )
    SDK_GAMESRC="$SDK_SRC/gamesrc"
fi

echo "==> Generating offsetof probes..."
python3 - "$WORK" << 'PYEOF'
import sys

work = sys.argv[1]

FIELDS = """
entity_state_t number origin angles old_origin modelindex modelindex2 modelindex3 modelindex4 frame skinnum effects renderfx solid sound event renderfx2 num_parts model_parts model_lighting last_lighting_update_pos last_lighting_vec_update_pos flamejunc_head flamejunc_head2 last_time prev_last_time broken_flag alpha scale
player_state_t pmove viewangles gunindex gunframe blend fov rdflags stats num_parts model_parts weapon_usage
pmove_state_t pm_type origin velocity pm_flags pm_time gravity delta_angles runscale
edict_t s client inuse linkcount area num_clusters clusternums headnode areanum areanum2 svflags mins maxs absmin absmax size solid clipmask owner voice_pitch rrs
gclient_t ps ping
usercmd_t msec buttons angles forwardmove sidemove upmove impulse lightlevel
trace_t allsolid startsolid fraction endpos plane surface contents ent
cvar_t name string latched_string flags modified value next
model_part_t modelindex invisible_objects skinnum object_bounds objectbounds_filename baseskin hitpoints hit_scale
model_lighting_t light_indexes light_vecs light_colors light_intensities light_styles num_dir_lights light_orgs
flamejunc_t org vel start_width end_width start_height end_height lifetime fadein_time start_alpha end_alpha aged alpha width height unitvel hit_wall next
game_import_t bprintf dprintf cprintf centerprintf sound positioned_sound configstring error modelindex soundindex imageindex setmodel trace pointcontents inPVS inPHS SetAreaPortalState AreasConnected linkentity unlinkentity BoxEdicts Pmove multicast unicast WriteChar WriteByte WriteShort WriteLong WriteFloat WriteString WritePosition WriteDir WriteAngle TagMalloc TagFree FreeTags cvar cvar_set cvar_forceset argc argv args AddCommandString DebugGraph LoadFile FreeFile Gamedir CreatePath GetConfigString GetModelInfo GetModelFrameInfo PmoveEx TagRealloc LocalizationMessage LocalizationUIMessage skinindex ClearObjectBoundsCached StopRender GetObjectBounds SaveCurrentGame
game_export_t apiversion Init Shutdown SpawnEntities WriteGame ReadGame WriteLevel ReadLevel ClientConnect ClientBegin ClientUserinfoChanged ClientDisconnect ClientCommand ClientThink RunFrame ServerCommand edicts edict_size num_edicts max_edicts GetNumObjectBounds GetObjectBoundsPointer GetNumJuniors
cplane_t normal dist type signbits pad spec_updateframe spec_updateframe_backface spec_render spec_render_back
"""

rows = []
for line in FIELDS.strip().splitlines():
    parts = line.split()
    t, fs = parts[0], parts[1:]
    for f in fs:
        rows.append((t, f))

def emit(includes):
    s = "#include <stdio.h>\n#include <stddef.h>\n"
    s += includes
    s += "int main(void) {\n"
    for t, f in rows:
        s += 'printf("%s.%s %%zu\\n", (size_t)offsetof(%s, %s));\n' % (t, f, t, f)
    for t in ["entity_state_t","player_state_t","pmove_state_t","edict_t","gclient_t",
              "usercmd_t","trace_t","cvar_t","model_part_t","model_lighting_t",
              "flamejunc_t","game_import_t","game_export_t","pmove_t","cplane_t"]:
        s += 'printf("sizeof(%s) %%zu\\n", (size_t)sizeof(%s));\n' % (t, t)
    s += "return 0; }\n"
    return s

open(work + '/probe_engine.c', 'w').write(emit('#include "shared.h"\n#include "game.h"\n'))
open(work + '/probe_sdk.c', 'w').write(emit('#include "g_local.h"\n'))
print("probes written: %d fields" % len(rows))
PYEOF

echo "==> Compiling probes..."
cc -o "$WORK/probe_engine" "$WORK/probe_engine.c" \
    -I"$REPO/src/common/header" -I"$REPO/src/game/header" -std=gnu99 -w
cc -o "$WORK/probe_sdk" "$WORK/probe_sdk.c" -I"$SDK_GAMESRC" -std=gnu99 -w

"$WORK/probe_engine" > "$WORK/out_engine.txt"
"$WORK/probe_sdk" > "$WORK/out_sdk.txt"

echo "==> Comparing layouts..."
# sizeof(edict_t)/sizeof(gclient_t) differ on purpose (the game module
# appends its private tail), sizeof(cvar_t) is a superset on the engine
# side and sizeof(pmove_t) is a superset on the SDK side. All field
# offsets must match.
STATUS=0
diff "$WORK/out_engine.txt" "$WORK/out_sdk.txt" > "$WORK/diff.txt" || true
if grep '^[<>]' "$WORK/diff.txt" | grep -Fv \
        -e 'sizeof(edict_t)' -e 'sizeof(gclient_t)' \
        -e 'sizeof(cvar_t)' -e 'sizeof(pmove_t)' | grep -q .; then
    echo "!! ABI MISMATCH:"
    grep '^[<>]' "$WORK/diff.txt" | grep -Fv \
        -e 'sizeof(edict_t)' -e 'sizeof(gclient_t)' \
        -e 'sizeof(cvar_t)' -e 'sizeof(pmove_t)'
    STATUS=1
else
    echo "All field offsets match."
    echo "Expected sizeof differences (by design):"
    grep '^[<>]' "$WORK/diff.txt" || true
fi

exit $STATUS
