#!/usr/bin/env bash
# One-way mirror of this repo to the Windows filesystem so Android Studio
# can build/run it natively (Gradle file locking fails on \\wsl$ paths).
# Edit in WSL, run this, hit Run in Studio. Never edit the Windows copy.
set -euo pipefail

SRC="$(cd "$(dirname "$0")/.." && pwd)"
DEST="${PAYCROSS_SDK_WIN_DIR:-/mnt/c/dev/payment-android-sdk}"

sync_once() {
    rsync -a --delete \
        --exclude .git \
        --exclude .gradle \
        --exclude build \
        --exclude .idea \
        --exclude local.properties \
        --exclude scripts/ \
        "$SRC/" "$DEST/"
    echo "synced $(date +%H:%M:%S) -> $DEST"
}

sync_once

if [[ "${1:-}" == "--watch" ]]; then
    echo "watching for changes (poll every 2s, ctrl-c to stop)"
    last=""
    while sleep 2; do
        current=$(find "$SRC" -path "$SRC/.git" -prune -o -path "$SRC/.gradle" -prune \
            -o -name build -prune -o -type f -printf "%T@ %p\n" 2>/dev/null | sort | md5sum)
        if [[ "$current" != "$last" ]]; then
            [[ -n "$last" ]] && sync_once
            last="$current"
        fi
    done
fi
