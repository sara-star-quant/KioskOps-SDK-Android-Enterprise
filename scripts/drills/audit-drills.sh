#!/usr/bin/env bash
# Device drills for the audit chain, run on a connected device or emulator.
#   1. upgrade: an older SDK writes a real audit database; this checkout opens it in place.
#   2. erase and key reset: erase a user, then delete the Keystore signing key, then verify.
#   3. process kill: kill the process repeatedly in the middle of record and erase cycles.
# Usage: scripts/drills/audit-drills.sh [OLD_TAG] [KILL_RUNS]    (defaults: v1.6.0, 8)
# Needs: adb with one device, JAVA_HOME on JDK 21, a clean git tree (the old tag runs in a worktree).
set -euo pipefail

OLD_TAG="${1:-v1.6.0}"
KILL_RUNS="${2:-8}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PKG="com.sarastarquant.kioskops.sdk.test"
RUNNER="$PKG/androidx.test.runner.AndroidJUnitRunner"
DRILL_DIR="kiosk-ops-sdk/src/androidTest/java/com/sarastarquant/kioskops/sdk/drill"
APK_PATH="kiosk-ops-sdk/build/outputs/apk/androidTest/debug/kiosk-ops-sdk-debug-androidTest.apk"
WT="$(mktemp -d)/old"

die() { echo "FAIL: $*" >&2; exit 1; }
instrument() { adb shell am instrument -w "$@" "$RUNNER" 2>&1; }

adb get-state >/dev/null 2>&1 || die "no device"
trap 'git -C "$ROOT" worktree remove --force "$WT" >/dev/null 2>&1 || true' EXIT

echo "== build $OLD_TAG test APK with the writer drill"
git -C "$ROOT" worktree add --detach "$WT" "$OLD_TAG" >/dev/null
cp "$ROOT/local.properties" "$WT/" 2>/dev/null || true
mkdir -p "$WT/$DRILL_DIR"
cp "$ROOT/$DRILL_DIR/AuditDrillWriterTest.kt" "$WT/$DRILL_DIR/"
(cd "$WT" && ./gradlew --console=plain -q :kiosk-ops-sdk:assembleDebugAndroidTest)

echo "== $OLD_TAG writes a real database"
adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install -r "$WT/$APK_PATH" >/dev/null
instrument -e drill write -e class com.sarastarquant.kioskops.sdk.drill.AuditDrillWriterTest | grep -q "^OK" \
  || die "writer drill"

echo "== build this checkout and upgrade in place"
(cd "$ROOT" && ./gradlew --console=plain -q :kiosk-ops-sdk:assembleDebugAndroidTest)
adb install -r "$ROOT/$APK_PATH" >/dev/null
adb logcat -c
instrument -e drill upgrade-verify -e class com.sarastarquant.kioskops.sdk.drill.AuditUpgradeDrillTest | grep -q "^OK" \
  || die "upgrade, erase and key reset drill"
adb logcat -d -s DRILL | grep DRILL | sed 's/^.*DRILL *: /  /'

echo "== process kill, $KILL_RUNS runs"
for run in $(seq 1 "$KILL_RUNS"); do
  fresh=""; [ "$run" = 1 ] && fresh="-e fresh true"
  adb logcat -c
  # shellcheck disable=SC2086
  adb shell "am instrument -w -e drill kill-writer $fresh -e class com.sarastarquant.kioskops.sdk.drill.AuditEraseKillDrillTest#writerLoopsUntilKilled $RUNNER >/dev/null 2>&1 &"
  sleep "$(awk -v r="$RANDOM" 'BEGIN{printf "%.1f", 1.5 + (r % 2600) / 1000}')"
  adb shell am force-stop "$PKG"
  sleep 1.5
  adb logcat -c
  instrument -e drill kill-verify -e class com.sarastarquant.kioskops.sdk.drill.AuditEraseKillDrillTest#verifierAfterKill | grep -q "^OK" \
    || die "chain not valid after kill, run $run"
  echo "  run $run: $(adb logcat -d -s DRILL | grep 'after kill' | sed 's/^.*after kill: //')"
done
echo "all drills passed"
