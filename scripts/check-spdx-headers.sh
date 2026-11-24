#!/usr/bin/env bash
# Every Kotlin and Java source file carries the BUSL-1.1 SPDX header within its first lines.
# The stub that mimics a third-party annotation is exempt.
set -euo pipefail
cd "$(dirname "$0")/.."
bad=0
while IFS= read -r f; do
  if ! head -n 5 "$f" | grep -q 'SPDX-License-Identifier: BUSL-1.1'; then
    echo "missing SPDX header: $f"
    bad=$((bad + 1))
  fi
done < <(git ls-files '*.kt' '*.java' | grep -v '^.clusterfuzzlite/stubs/')
if [ "$bad" -gt 0 ]; then
  echo "$bad file(s) without the header; copy it from any other source file"
  exit 1
fi
echo "all source files carry the SPDX header"
