#!/usr/bin/env bash
# Reboot, let the TV settle, and print used RAM plus the PSS of the Home app and its helpers.
# Usage: ANDROID_SERIAL=<tv ip>:5555 scripts/measure.sh <label>
set -euo pipefail
LABEL="${1:?usage: measure.sh <label>}"
adb reboot
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 3; done
sleep 90
adb shell input keyevent HOME
sleep 10
m=$(adb shell dumpsys meminfo | tr -d '\r')
echo "== $LABEL"
grep -E '^ *(Used RAM|Free RAM|Total RAM):' <<<"$m"
pss=$(sed -n '/^Total PSS by process:/,/^$/p' <<<"$m")
grep -E 'K: (com\.google\.android\.(tvlauncher|apps\.tv\.(launcherx|dreamx)|tungsten\.setupwraith|tvrecommendations|backdrop|play\.games|syncadapters\.calendar|federatedcompute|adservices\.api|tv\.bugreportsender)|dev\.arzaroth\.lumen|tv\.anoki\.acr\.anokiacroptin|com\.android\.printspooler)(:[a-z]+)? ' <<<"$pss" || true
echo "processes: $(adb shell ps -A | wc -l)"
