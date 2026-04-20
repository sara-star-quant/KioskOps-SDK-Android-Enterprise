#!/usr/bin/env bash
#
# Pre-release verification for KioskOps SDK.
#
# Runs the shell-automatable subset of docs/RELEASE_VERIFICATION.md. Intended to be
# executed on a clean checkout at the release candidate commit before pushing the
# `v*.*.*` tag. Fails fast on any gate.
#
# Covered:
#   Code correctness (unit tests, apiCheck, detekt, sample-app, kover, dokka).
#   Release artifact verification (publishToMavenLocal, SBOM inspection, AAR surface).
#   Fresh fuzz pass against the release candidate.
#
# Not covered (manual; see docs/RELEASE_VERIFICATION.md):
#   Instrumented tests on a real device.
#   Cosign verification after tag push.
#   Integrator-perspective drills (host-app smoke, pin rotation, corruption
#   recovery, process-kill mid-sync, in-place upgrade).

set -euo pipefail

die() { printf 'pre-release-verify: %s\n' "$*" >&2; exit 1; }
log() { printf '\n==> %s\n' "$*"; }

# Resolve repo root from the script location.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

VERSION="$(grep '^VERSION_NAME=' gradle.properties | cut -d= -f2)"
[ -n "$VERSION" ] || die "could not read VERSION_NAME from gradle.properties"
log "Verifying release candidate $VERSION at $(git rev-parse --short HEAD)"

# --- code correctness ------------------------------------------------------

log "Running gradle verification tasks"
./gradlew \
  :kiosk-ops-sdk:testDebugUnitTest \
  :kiosk-ops-sdk:apiCheck \
  :kiosk-ops-sdk:detekt \
  :sample-app:assembleDebug \
  :sample-app:assembleRelease \
  :kiosk-ops-sdk:koverVerifyDebug \
  :kiosk-ops-sdk:dokkaGeneratePublicationHtml \
  --no-daemon

# --- artifact verification -------------------------------------------------

log "Publishing to Maven Local for artifact inspection"
./gradlew :kiosk-ops-sdk:publishToMavenLocal --no-daemon

MAVEN_DIR="$HOME/.m2/repository/com/sarastarquant/kioskops/kiosk-ops-sdk/$VERSION"
[ -d "$MAVEN_DIR" ] || die "expected artifacts under $MAVEN_DIR but directory missing"

AAR="$MAVEN_DIR/kiosk-ops-sdk-$VERSION.aar"
POM="$MAVEN_DIR/kiosk-ops-sdk-$VERSION.pom"
SOURCES="$MAVEN_DIR/kiosk-ops-sdk-$VERSION-sources.jar"
JAVADOC="$MAVEN_DIR/kiosk-ops-sdk-$VERSION-javadoc.jar"

for f in "$AAR" "$POM" "$SOURCES" "$JAVADOC"; do
  [ -s "$f" ] || die "missing or empty artifact: $f"
done

log "POM references verification"
grep -q 'github.com/sara-star-quant' "$POM" \
  || die "POM does not reference the expected repo owner"
grep -q 'github.com/pzverkov' "$POM" \
  && die "POM references legacy repo owner; run the org-rename sweep"
true

log "Sources jar carries .kt files"
unzip -l "$SOURCES" | grep -qE '\.kt$' \
  || die "sources jar has no .kt files"

log "AAR surface audit"
AAR_TMP="$(mktemp -d)"
trap 'rm -rf "$AAR_TMP"' EXIT
unzip -q "$AAR" -d "$AAR_TMP"
unzip -q "$AAR_TMP/classes.jar" -d "$AAR_TMP/classes"

if find "$AAR_TMP/classes" -path '*net/zetetic/database/sqlcipher/*.class' | grep -q .; then
  die "AAR bundles SQLCipher classes; SQLCipher should be compileOnly"
fi
if find "$AAR_TMP/classes" -path '*com/appmattus/*' | grep -q .; then
  die "AAR bundles appmattus classes; should have been removed in 1.2"
fi
if find "$AAR_TMP/classes" -name '*Test*.class' -o -name '*Fake*.class' -o -name '*Mock*.class' | grep -q .; then
  die "AAR contains test-support classes"
fi

[ -s "$AAR_TMP/AndroidManifest.xml" ] || die "AAR missing AndroidManifest.xml"
grep -q 'androidx.startup.InitializationProvider' "$AAR_TMP/AndroidManifest.xml" \
  || die "AAR manifest missing androidx.startup provider"
if grep -qE '<activity\b' "$AAR_TMP/AndroidManifest.xml"; then
  die "AAR manifest declares <activity>; library should not ship activities"
fi

[ -s "$AAR_TMP/proguard.txt" ] || die "AAR missing consumer proguard rules"

# --- SBOM audit (best-effort; skip if cyclonedxBom task not configured) ----

if ./gradlew tasks --all --no-daemon 2>/dev/null | grep -q cyclonedxBom; then
  log "SBOM generation and content check"
  ./gradlew :kiosk-ops-sdk:cyclonedxBom --no-daemon
  BOM="$REPO_ROOT/kiosk-ops-sdk/build/reports/bom.json"
  [ -s "$BOM" ] || die "SBOM generation produced no bom.json"

  if command -v jq >/dev/null 2>&1; then
    if jq -e '.components[] | select(.purl | test("pkg:maven/org\\.bouncycastle/"))' "$BOM" >/dev/null; then
      die "SBOM lists BouncyCastle as a runtime dep; should be build-classpath only"
    fi
    if jq -e '.components[] | select(.purl | test("pkg:maven/com\\.appmattus\\.certificatetransparency/"))' "$BOM" >/dev/null; then
      die "SBOM lists appmattus certificatetransparency; should be absent in 1.2"
    fi
  else
    printf 'pre-release-verify: jq not installed; SBOM content audit skipped\n' >&2
  fi
else
  printf 'pre-release-verify: cyclonedxBom task not available; SBOM step skipped\n' >&2
fi

# --- fresh fuzz pass -------------------------------------------------------

log "Fresh fuzz pass (Jazzer-backed fuzzTest)"
./gradlew :kiosk-ops-sdk:fuzzTest --no-daemon

log "All automated release gates passed for $VERSION."
log "Manual gates remaining (see docs/RELEASE_VERIFICATION.md): instrumented"
log "tests on device, cosign verification on tag push, integrator drills."
