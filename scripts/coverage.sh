#!/usr/bin/env bash
# Line coverage of the app, via JaCoCo over the Robolectric unit tests (Android graphics run
# natively, so drawing code is exercised, not stubbed). The tests themselves are the only
# exclusion.
#
#   scripts/coverage.sh              summary, then the files with the most uncovered lines
#   scripts/coverage.sh --check      fail below LUMEN_COVERAGE_FLOOR (98)
#   scripts/coverage.sh --html       write and open the HTML report
#   scripts/coverage.sh --xml        write the XML report and print its path
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TOP="${LUMEN_COVERAGE_TOP:-15}"
FLOOR="${LUMEN_COVERAGE_FLOOR:-98}"
REPORT="$REPO_DIR/lumen/build/reports/coverage/test/debug"

if [[ -t 1 ]]; then B="\033[0;34m"; G="\033[0;32m"; Y="\033[0;33m"; R="\033[0;31m"; Z="\033[0m"; else B=""; G=""; Y=""; R=""; Z=""; fi
info()    { printf '%b\n' "${B}$*${Z}"; }
success() { printf '%b\n' "${G}$*${Z}"; }
warn()    { printf '%b\n' "${Y}$*${Z}"; }
fail()    { printf '%b\n' "${R}$*${Z}" >&2; }

MODE=summary
while [[ $# -gt 0 ]]; do
  case "$1" in
  --check) MODE=check ;;
  --html) MODE=html ;;
  --xml) MODE=xml ;;
  -h | --help)
    sed -n '2,9p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'
    exit 0
    ;;
  *)
    fail "unknown option: $1"
    exit 1
    ;;
  esac
  shift
done

if ! command -v java >/dev/null 2>&1; then
  fail "java not found."
  warn "  mise install   (the repo pins temurin-17 and the Android SDK in mise.toml)"
  exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
  fail "python3 not found; it reads the JaCoCo report."
  exit 1
fi

cd "$REPO_DIR"
info "Running the unit tests with coverage..."
./gradlew -q :lumen:testDebugUnitTest :lumen:createDebugUnitTestCoverageReport

read -r COVERED TOTAL < <(python3 - "$REPORT/report.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
line = next(c for c in root.findall("counter") if c.get("type") == "LINE")
covered, missed = int(line.get("covered")), int(line.get("missed"))
print(covered, covered + missed)
PY
)
PERCENT=$(awk -v c="$COVERED" -v t="$TOTAL" 'BEGIN { printf "%.2f", t ? 100 * c / t : 100 }')

case "$MODE" in
check)
  if awk -v p="$PERCENT" -v f="$FLOOR" 'BEGIN { exit !(p + 0 < f + 0) }'; then
    fail "Line coverage ${PERCENT}% (${COVERED}/${TOTAL}) is below the ${FLOOR}% floor."
    exit 1
  fi
  success "Line coverage ${PERCENT}% (${COVERED}/${TOTAL}) is at or above ${FLOOR}%."
  ;;
html)
  info "Report at $REPORT/index.html"
  xdg-open "$REPORT/index.html" >/dev/null 2>&1 || true
  ;;
xml)
  echo "$REPORT/report.xml"
  ;;
summary)
  info "Line coverage: ${PERCENT}% (${COVERED}/${TOTAL})"
  echo
  info "Most uncovered lines:"
  python3 - "$REPORT/report.xml" "$TOP" <<'PY'
import sys, xml.etree.ElementTree as ET
root, top = ET.parse(sys.argv[1]).getroot(), int(sys.argv[2])
rows = []
for pkg in root.findall("package"):
    for sf in pkg.findall("sourcefile"):
        c = next((c for c in sf.findall("counter") if c.get("type") == "LINE"), None)
        if c is not None and int(c.get("missed")) > 0:
            m, cv = int(c.get("missed")), int(c.get("covered"))
            rows.append((m, 100 * cv / (m + cv), sf.get("name")))
if not rows:
    print("  (nothing uncovered)")
for m, p, name in sorted(rows, reverse=True)[:top]:
    print(f"  {m:6d} uncovered  {p:6.2f}%  {name}")
PY
  ;;
esac
