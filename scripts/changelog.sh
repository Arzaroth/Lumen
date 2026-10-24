#!/usr/bin/env bash
# Prints the CHANGELOG.md notes for one version, without its heading.
#
#   scripts/changelog.sh 1.1.0
set -euo pipefail

version="${1:?usage: scripts/changelog.sh <version>}"
awk -v v="$version" '
  /^## \[/ { inside = index($0, "## [" v "]") == 1; next }
  /^\[[^]]*\]: / { inside = 0 }
  inside
' "$(dirname "${BASH_SOURCE[0]}")/../CHANGELOG.md" | sed -e '/./,$!d' | sed -e ':a' -e '/^\n*$/{$d;N;ba' -e '}'
