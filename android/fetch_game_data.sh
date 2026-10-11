#!/bin/sh
#
# Download the Kingpin game data from a link and prepare it for the
# launcher: the result is a directory that contains main/*.pak.
#
# Usage: fetch_game_data.sh <url> [output-dir]
#
# Supported URL types:
#   * HuggingFace (https://huggingface.co/... or https://hf.co/...):
#     direct download. Set HF_TOKEN for private or gated repositories.
#   * Google Drive (https://drive.google.com/...): understands the
#     usual /file/d/<id>/ and ?id=<id> share links, falls back to
#     gdown when Google serves a confirmation page.
#   * Anything else is downloaded as a direct link.
#
# The file may be a .zip / .7z / .tar.* archive (extracted here) or a
# bare .pak file (placed into main/).
#
set -e

URL="$1"
OUT="${2:-game-data}"

if [ -z "$URL" ]; then
    echo "usage: $0 <url> [output-dir]" >&2
    exit 2
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
DL="$WORK/download"

fail() {
    echo "ERROR: $*" >&2
    exit 1
}

case "$URL" in
    *drive.google.com*|*docs.google.com*) KIND=gdrive ;;
    *huggingface.co*|*hf.co*) KIND=hf ;;
    *) KIND=direct ;;
esac

# file:// links are used for local testing
case "$URL" in
    file://*) KIND=direct ;;
esac

gdrive_id() {
    echo "$URL" | sed -n \
        -e 's|.*/file/d/\([^/]*\)/.*|\1|p' \
        -e 's|.*[?&]id=\([^&]*\).*|\1|p' | head -n 1
}

is_html() {
    [ -f "$1" ] || return 1
    head -c 64 "$1" | grep -qi '<!doctype\|<html'
}

is_archive() {
    [ -s "$1" ] || return 1
    unzip -l "$1" >/dev/null 2>&1 && return 0
    ( 7z l "$1" || 7za l "$1" ) >/dev/null 2>&1 && return 0
    tar -tf "$1" >/dev/null 2>&1 && return 0
    return 1
}

curl_get() {
    curl -fL --retry 3 --retry-delay 2 -o "$DL" "$@"
}

echo "==> Downloading game data ($KIND)..."

if [ "$KIND" = gdrive ]; then
    ID="$(gdrive_id)"
    if [ -n "$ID" ]; then
        curl_get "https://drive.usercontent.google.com/download?id=$ID&export=download&confirm=t" || true
    fi

    if is_html "$DL" || ! [ -s "$DL" ]; then
        echo "    Google served a confirmation page, retrying with gdown..."
        rm -f "$DL"
        python3 -m pip install --user --quiet gdown || \
            python3 -m pip install --user --quiet --break-system-packages gdown
        python3 -m gdown --fuzzy "$URL" -O "$DL" || fail "gdown could not download the file"
    fi
elif [ "$KIND" = hf ]; then
    if [ -n "$HF_TOKEN" ]; then
        curl_get -H "Authorization: Bearer $HF_TOKEN" "$URL" \
            || fail "download failed (check HF_TOKEN and the link)"
    else
        curl_get "$URL" || fail "download failed (private repositories need HF_TOKEN)"
    fi
else
    curl_get "$URL" || fail "download failed"
fi

[ -s "$DL" ] || fail "the downloaded file is empty"
is_html "$DL" && fail "the downloaded file is an HTML page, not data (check the link)"

echo "==> Downloaded $(wc -c < "$DL") bytes."

EX="$WORK/extract"
mkdir -p "$EX"

if is_archive "$DL"; then
    echo "==> Extracting archive..."
    if unzip -l "$DL" >/dev/null 2>&1; then
        unzip -q "$DL" -d "$EX"
    elif ( 7z l "$DL" || 7za l "$DL" ) >/dev/null 2>&1; then
        ( 7z x -y "-o$EX" "$DL" || 7za x -y "-o$EX" "$DL" ) >/dev/null
    else
        tar -xf "$DL" -C "$EX"
    fi
else
    # a bare .pak? (Quake pak files start with "PACK")
    if head -c 4 "$DL" | grep -q 'PACK'; then
        echo "==> Single .pak file, placing into main/..."
        mkdir -p "$EX/main"
        cp "$DL" "$EX/main/pak0.pak"
    else
        fail "unsupported archive format (use zip, 7z or tar)"
    fi
fi

# Find the data root: a directory that contains main/*.pak
ROOT=""
for d in "$EX" "$EX"/*; do
    [ -d "$d/main" ] || continue
    if ls "$d"/main/*.pak >/dev/null 2>&1; then
        ROOT="$d"
        break
    fi
done

if [ -z "$ROOT" ]; then
    # maybe the pak files sit at the top level
    PAKS="$(find "$EX" -maxdepth 2 -name '*.pak' -type f)"
    if [ -n "$PAKS" ]; then
        mkdir -p "$EX/main"
        echo "$PAKS" | while IFS= read -r p; do
            mv "$p" "$EX/main/"
        done
        ROOT="$EX"
    fi
fi

[ -n "$ROOT" ] || fail "no main/*.pak found in the archive"

rm -rf "$OUT"
mkdir -p "$OUT"
cp -R "$ROOT"/. "$OUT"/

ls "$OUT"/main/*.pak >/dev/null 2>&1 || fail "no main/*.pak in the result"

echo "==> Game data ready in: $OUT/main/"
ls -la "$OUT"/main/
