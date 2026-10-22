#!/usr/bin/env bash
# Build Lumen, install it, make it Home, and only then disable the stock launcher.
# Usage: ANDROID_SERIAL=<tv ip>:5555 scripts/install.sh [--keep-stock]
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
STATE="$HERE/backup"
PKG=dev.arzaroth.lumen
HOME_COMPONENT="$PKG/.LumenActivity"
STOCK="$(sed 's#/.*##' "$STATE/home.txt" 2>/dev/null | tr -d '\r' || true)"
STOCK="${STOCK:-com.google.android.tvlauncher}"
sh() { adb shell "$@" | tr -d '\r'; }

sdk=$(sh getprop ro.build.version.sdk)
if [ "${sdk:-0}" -lt 26 ]; then
  echo "TV is on SDK $sdk; Lumen needs 26 (Android 8.0) or newer. Debloat only." >&2
  exit 1
fi

"$HERE/lumen/build.sh"

adb push "$HERE/lumen/build/lumen.apk" /data/local/tmp/lumen.apk >/dev/null
echo "install $(sh pm install -r -g /data/local/tmp/lumen.apk)"
sh rm -f /data/local/tmp/lumen.apk
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION; do
  sh pm grant "$PKG" "android.permission.$p" 2>/dev/null || true
done

focused_home() {
  sh input keyevent HOME
  sleep 3
  focused=$(sh dumpsys activity activities | grep -E 'mFocusedApp|topResumedActivity' | sed -n 1p || true)
  grep -q "$PKG" <<<"$focused"
}

echo "home    $(sh cmd package set-home-activity --user 0 "$HOME_COMPONENT")"
if [ "$sdk" -ge 29 ]; then
  sh cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" 0 >/dev/null 2>&1 || true
fi
if focused_home; then
  echo "verify  Lumen is Home"
elif [ "${1:-}" = "--keep-stock" ]; then
  echo "Home is still $focused: a system launcher with a higher HOME priority wins while enabled." >&2
  exit 1
else
  echo "note    $STOCK outranks any Home choice while enabled; swapping with a guard"
fi

rivals=$(sh cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME |
  awk '/priority=/ { split($1, a, "="); prio = a[2] } /^ *[a-z][^ ]*\// { if (prio >= 0) print $1 }' |
  sed 's#/.*##' | grep -vx "$PKG" | sort -u || true)
if [ "${1:-}" != "--keep-stock" ] && [ -n "$rivals" ]; then
  wifi=$(sh dumpsys wifi)
  for r in $rivals; do
    if grep -qE "cname=$r([^.a-zA-Z0-9]|$)" <<<"$wifi"; then
      echo "$r claims Home and owns saved Wi-Fi networks; disabling it would delete them." >&2
      echo "Forget and re-join the network in Settings, then run this again." >&2
      exit 1
    fi
  done
  swapped=()
  for r in $rivals; do
    echo "disable $r -> $(sh pm disable-user --user 0 "$r")"
    echo "$r" >> "$STATE/disabled-by-us.txt"
    swapped+=("$r")
  done
  sort -u -o "$STATE/disabled-by-us.txt" "$STATE/disabled-by-us.txt"
  if ! focused_home; then
    echo "Lumen did not take Home after disabling ${swapped[*]} ($focused); re-enabling them." >&2
    for r in "${swapped[@]}"; do sh pm enable --user 0 "$r"; done
    exit 1
  fi
  echo "verify  Lumen is Home"
fi

if ! focused_home; then
  echo "Home is not Lumen ($focused)." >&2
  exit 1
fi

[ -s "$STATE/screensaver.txt" ] || sh settings get secure screensaver_components > "$STATE/screensaver.txt"
sh settings put secure screensaver_components "$PKG/.LumenDream"
echo "dream   Lumen Clock screensaver"

KEYS="$PKG/.LumenKeys"
a11y=$(sh settings get secure enabled_accessibility_services)
[ -e "$STATE/accessibility.txt" ] || echo "$a11y" > "$STATE/accessibility.txt"
if ! grep -q "$KEYS" <<<"$a11y"; then
  if [ -z "$a11y" ] || [ "$a11y" = null ]; then a11y="$KEYS"; else a11y="$a11y:$KEYS"; fi
  sh settings put secure enabled_accessibility_services "$a11y"
  sh settings put secure accessibility_enabled 1
fi
echo "keys    remote Input and app buttons -> Lumen ($(sh settings get secure enabled_accessibility_services))"
