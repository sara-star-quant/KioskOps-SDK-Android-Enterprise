#!/usr/bin/env bash
# Dormant until outside PRs exist. Usage: scripts/check-dco.sh <base-sha> <head-sha>
# Every commit needs a Signed-off-by trailer matching its author, except the maintainer and bots.
set -euo pipefail
base="$1"; head="$2"
exempt='(pzverkov|dependabot|github-actions)'
bad=0
for c in $(git rev-list --no-merges "$base..$head"); do
  author_email=$(git log -1 --format=%ae "$c")
  [[ "$author_email" =~ $exempt ]] && continue
  if ! git log -1 --format=%B "$c" | grep -qi "^Signed-off-by: .*<$author_email>"; then
    echo "no matching sign-off: $(git log -1 --format='%h %s' "$c")"
    bad=$((bad + 1))
  fi
done
[ "$bad" -eq 0 ] || { echo "$bad commit(s) need 'git commit -s'"; exit 1; }
echo "all commits signed off"
