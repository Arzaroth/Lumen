#!/usr/bin/env bash
# Capture the TV's state before any change. Refuses to overwrite an existing backup.
# Usage: ANDROID_SERIAL=<tv ip>:5555 scripts/backup.sh
set -euo pipefail
STATE="$(cd "$(dirname "$0")/.." && pwd)/backup"
if [ -s "$STATE/packages.txt" ]; then
  echo "backup/ already holds a backup; move it away first." >&2
  exit 1
fi
mkdir -p "$STATE"
sh() { adb shell "$@" </dev/null | tr -d '\r'; }

sh pm list packages -f > "$STATE/packages.txt"
sh pm list packages -d > "$STATE/disabled.txt"
sh pm list packages -d | sed 's/^package://' | sort > "$STATE/disabled-before.txt"
sh getprop > "$STATE/getprop.txt"
for n in system secure global; do sh settings list "$n" > "$STATE/settings-$n.txt"; done
sh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 > "$STATE/home.txt"
sh settings get secure screensaver_components > "$STATE/screensaver.txt"
echo "backed up $(wc -l < "$STATE/packages.txt") packages; Home is $(cat "$STATE/home.txt")"
