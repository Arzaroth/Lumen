#!/usr/bin/env bash
# Put the TV back as it was: re-enable everything disable.sh and install.sh disabled, give Home back
# to the stock launcher, restore the screensaver and remove Lumen. Packages that were already
# disabled before the first run stay disabled.
# Usage: ANDROID_SERIAL=<tv ip>:5555 scripts/restore.sh [--keep-lumen]
set -euo pipefail
STATE="$(cd "$(dirname "$0")/.." && pwd)/backup"
sh() { adb shell "$@" </dev/null | tr -d '\r'; }

STOCK_HOME="$(tr -d '\r' < "$STATE/home.txt" 2>/dev/null || true)"
STOCK_HOME="${STOCK_HOME:-com.google.android.tvlauncher/.MainActivity}"

touch "$STATE/disabled-by-us.txt" "$STATE/disabled-before.txt"
mapfile -t PKGS < <(grep -v '^$' "$STATE/disabled-by-us.txt" || true)
for p in "${PKGS[@]}"; do
  if grep -qxF "$p" "$STATE/disabled-before.txt"; then
    echo "keep    $p (was disabled before)"
    continue
  fi
  echo "enable  $p -> $(sh pm enable --user 0 "$p")"
done

if [ "${1:-}" != "--keep-lumen" ]; then
  echo "remove  com.arzaroth.lumen -> $(sh pm uninstall com.arzaroth.lumen)"
fi
echo "home    $STOCK_HOME -> $(sh cmd package set-home-activity --user 0 "$STOCK_HOME")"

saver="$(tr -d '\r' < "$STATE/screensaver.txt" 2>/dev/null || echo null)"
if [ -z "$saver" ] || [ "$saver" = null ]; then
  sh settings delete secure screensaver_components >/dev/null
else
  sh settings put secure screensaver_components "$saver"
fi
echo "dream   screensaver -> ${saver:-default}"

if [ -e "$STATE/accessibility.txt" ]; then
  a11y="$(tr -d '\r' < "$STATE/accessibility.txt")"
  if [ -z "$a11y" ] || [ "$a11y" = null ]; then
    sh settings delete secure enabled_accessibility_services >/dev/null
    sh settings put secure accessibility_enabled 0
  else
    sh settings put secure enabled_accessibility_services "$a11y"
  fi
  echo "keys    accessibility services -> ${a11y:-none}"
fi

sh input keyevent HOME
sleep 3
echo "focus   $(sh dumpsys activity activities | grep -E 'topResumedActivity|mFocusedApp' | sed -n 's/^ *//; 1p')"
echo "disabled now:"
sh pm list packages -d | sed 's/^package:/  /' | sort
