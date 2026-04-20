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

log "Producing release artifacts in-tree (bypasses the publishing pipeline to skip signing)"
./gradlew \
  :kiosk-ops-sdk:bundleReleaseAar \
  :kiosk-ops-sdk:sourceReleaseJar \
  :kiosk-ops-sdk:javaDocReleaseJar \
  :kiosk-ops-sdk:generatePomFileForMavenPublication \
  --no-daemon

AAR="$REPO_ROOT/kiosk-ops-sdk/build/outputs/aar/kiosk-ops-sdk-release.aar"
POM="$REPO_ROOT/kiosk-ops-sdk/build/publications/maven/pom-default.xml"
SOURCES="$REPO_ROOT/kiosk-ops-sdk/build/intermediates/source_jar/release/release-sources.jar"
JAVADOC="$REPO_ROOT/kiosk-ops-sdk/build/intermediates/java_doc_jar/release/release-javadoc.jar"

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
# Counting instead of `grep -q`: grep terminates early on first match and closes
# the pipe, which triggers SIGPIPE on unzip; `set -o pipefail` would then fail.
kt_count="$(unzip -l "$SOURCES" | grep -cE '\.kt$' || true)"
[ "$kt_count" -gt 0 ] || die "sources jar has no .kt files"

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

# --- SBOM generation -------------------------------------------------------
# The AAR surface audit above is the ground truth for what ships at runtime.
# CycloneDX reports the full dependency graph including build classpath, which
# makes purl-based scope filtering unreliable across plugin versions. Here we
# only verify the SBOM generates and is non-empty; any "what ships" assertion
# belongs in the AAR audit.

log "SBOM generation"
./gradlew :kiosk-ops-sdk:cyclonedxBom --no-daemon
BOM="$REPO_ROOT/kiosk-ops-sdk/build/reports/cyclonedx/bom.json"
[ -s "$BOM" ] || die "SBOM generation produced no bom.json"

# --- fresh fuzz pass -------------------------------------------------------

log "Fresh fuzz pass (Jazzer-backed fuzzTest)"
./gradlew :kiosk-ops-sdk:fuzzTest --no-daemon

log "All automated release gates passed for $VERSION."
log "Manual gates remaining (see docs/RELEASE_VERIFICATION.md): instrumented"
log "tests on device, cosign verification on tag push, integrator drills."
