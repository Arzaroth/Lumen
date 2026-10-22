#!/usr/bin/env bash
# Usage: ANDROID_SERIAL=<tv ip>:5555 scripts/disable.sh debloat.txt
set -euo pipefail
LIST="${1:?usage: disable.sh <debloat list>}"
STATE="$(dirname "$0")/../backup"
mkdir -p "$STATE"

PROTECTED='^(android|com\.android\.(systemui|shell|phone|settings|tv\.settings|permissioncontroller|packageinstaller|providers\.(settings|media|downloads|tv|telephony))|com\.google\.android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata|overlay\.modules.*|gms|gsf|inputmethod\.latin|tv\.remote\.service|bluetooth)|com\.android\.vending)$'

PKGS=$(sed -e 's/#.*//' -e 's/[[:space:]]//g' "$LIST" | grep -v '^$' || true)
if bad=$(printf '%s\n' "$PKGS" | grep -E "$PROTECTED"); then
  echo "refusing: protected package(s) in the list:" >&2
  printf '  %s\n' $bad >&2
  exit 1
fi

[ -s "$STATE/disabled-before.txt" ] || adb shell pm list packages -d | sed 's/^package://' | tr -d '\r' | sort > "$STATE/disabled-before.txt"

for p in $PKGS; do
  if ! adb shell pm path "$p" >/dev/null 2>&1; then
    echo "skip    $p (not installed)"
    continue
  fi
  out=$(adb shell pm disable-user --user 0 "$p" 2>&1 | tr -d '\r') || true
  if ! grep -q 'new state: disabled' <<<"$out"; then
    echo "fail    $p -> $(grep -m1 -E 'Exception:|Error' <<<"$out" | sed 's/^[^:]*: //')"
    continue
  fi
  echo "disable $p -> $out"
  echo "$p" >> "$STATE/disabled-by-us.txt"
done
sort -u -o "$STATE/disabled-by-us.txt" "$STATE/disabled-by-us.txt"

echo
echo "now disabled:"
adb shell pm list packages -d | sed 's/^package:/  /' | tr -d '\r' | sort
