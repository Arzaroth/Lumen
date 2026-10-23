#!/usr/bin/env bash
# Press the D-pad 30 times on Home and report the launcher's frame-time stats from gfxinfo.
# Usage: ANDROID_SERIAL=<tv ip>:5555 scripts/frames.sh <launcher package>
set -euo pipefail
PKG="${1:?usage: frames.sh <launcher package>}"
adb shell input keyevent HOME
sleep 3
adb shell dumpsys gfxinfo "$PKG" reset >/dev/null
for _ in $(seq 1 10); do
  for k in DPAD_RIGHT DPAD_RIGHT DPAD_LEFT; do
    adb shell input keyevent "$k"
    sleep 0.35
  done
done
sleep 1
adb shell dumpsys gfxinfo "$PKG" | tr -d '\r' | grep -E -m8 'Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Slow UI thread|Slow issue draw'
