# Release Verification

Pre-tag checklist for KioskOps SDK releases. Keeps release verification reproducible
across versions and across maintainers. Required reading before pushing any
`v*.*.*` tag.

> **Scope.** Engineering gates only. Business gates (release announcement, customer
> outreach) are out of scope.

## How to use this document

Work top-down. Items in **Blocking before tag push** and **Blocking on tag push**
must all pass before the tag goes public. Items in **Post-tag drills** run in the
window between tag push and public announcement; a failure there means revoking
the release (delete the tag, unpublish the GitHub Release, file an advisory) and
cutting a patch.

`scripts/pre-release-verify.sh` automates the shell-runnable subset. Run it first;
use this document as the source of truth for the rest.

## Blocking before tag push

### Code correctness

Already exercised by CI on every PR; re-run locally against the release candidate
commit.

| Check | Command | Pass criteria |
|---|---|---|
| Unit tests | `./gradlew :kiosk-ops-sdk:testDebugUnitTest` | 100% |
| API surface | `./gradlew :kiosk-ops-sdk:apiCheck` | Clean; baseline matches `main` HEAD |
| Static analysis | `./gradlew :kiosk-ops-sdk:detekt` | Zero new issues vs baseline |
| Sample app debug + release | `./gradlew :sample-app:assembleDebug :sample-app:assembleRelease` | Both configurations build |
| Coverage floor | `./gradlew :kiosk-ops-sdk:koverVerifyDebug` | Kover rule `minBound(70)` holds |
| Dokka | `./gradlew :kiosk-ops-sdk:dokkaGeneratePublicationHtml` | Exits clean |

### Release artifact verification

Performed against the local-publish output before tagging.

**Maven Local dry-run.**

```
./gradlew :kiosk-ops-sdk:publishToMavenLocal
```

Inspect `~/.m2/repository/com/sarastarquant/kioskops/kiosk-ops-sdk/<version>/`:

- `kiosk-ops-sdk-<version>.aar` is non-empty.
- `kiosk-ops-sdk-<version>.pom` references verification (SCM, developer, and URL
  fields point at the current repository owner).
- `kiosk-ops-sdk-<version>.module` (Gradle Module Metadata) references the
  transitive deps we intend to ship.
- `kiosk-ops-sdk-<version>-sources.jar` contains `.kt` files.
- `kiosk-ops-sdk-<version>-javadoc.jar` is the Dokka HTML, not an empty
  placeholder.

**SBOM content audit.**

```
./gradlew :kiosk-ops-sdk:cyclonedxBom
jq '.components[] | select(.scope=="required") | .purl' kiosk-ops-sdk/build/reports/bom.json | sort -u
```

Match the expected runtime closure. Flag anything surprising. For v1.2:

- No `org.bouncycastle:*` runtime (build-classpath only; forced to 1.84).
- No `com.appmattus.certificatetransparency:*` (removed in 1.2).
- Kotlin stdlib, kotlinx-serialization-json, androidx.room, okhttp at the
  versions pinned in `gradle/libs.versions.toml`.

**AAR surface audit.**

```
unzip -l kiosk-ops-sdk/build/outputs/aar/kiosk-ops-sdk-release.aar
```

- `classes.jar` contains no `net.zetetic.database.sqlcipher.*` (SQLCipher is
  `compileOnly`).
- `classes.jar` contains no `com.appmattus.*`.
- `AndroidManifest.xml` declares zero `<activity>`, exactly one `<provider>`
  (the androidx.startup InitializationProvider).
- `proguard.txt` is the consumer rules, non-empty.
- No class matching `.*Test.*`, `.*Fake.*`, `.*Mock.*`, `.*Debug.*Only.*`
  reachable from the public surface.

### Dependency hygiene

- Dismiss every open Dependabot alert with a documented rationale.
- Re-submit the dependency graph:
  `gh workflow run build.yml --ref main` (the build job re-runs
  `submit-gradle`).
- Verify the GitHub Insights > Dependencies view shows the expected resolved
  versions (including build-classpath forces).

### Integration coverage

Not covered by default PR CI; must be driven manually or by an opt-in workflow.

**Instrumented tests on a real device.**

```
./gradlew :kiosk-ops-sdk:connectedDebugAndroidTest
```

Target API 33 and API 34 emulators (minSdk + latest). Every test passes;
re-run once to confirm non-flakiness.

**Fuzz run against the release candidate.**

```
./gradlew :kiosk-ops-sdk:fuzzTest
```

Dedicated pass on the exact commit we will tag. No crash artifacts; no
regressions from the previous release.

## Blocking on tag push

**Cosign signature verification.**

After the release workflow publishes on `v*.*.*`:

```
cosign verify-blob \
  --signature kiosk-ops-sdk-release.aar.sig \
  --certificate kiosk-ops-sdk-release.aar.pem \
  --certificate-identity-regexp 'https://github.com/sara-star-quant/KioskOps-SDK-Android-Enterprise' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  kiosk-ops-sdk-release.aar
```

Confirms the artifact on Maven Central matches what GitHub Actions produced.

## Post-tag drills

Run in the window between tag push and public announcement. Document any
findings in the GitHub Release notes.

### Host-app smoke on a clean device

- Wipe a test device.
- Install sample-app `assembleRelease` built from the tag.
- Confirm SDK init succeeds; first heartbeat lands in the audit trail.
- Exercise each `dataRights.*` call with a `DataRightsAuthorizer` returning
  `Authorized`; verify the audit trail records the decision.
- Export diagnostics. Open the ZIP. Confirm `manifest.json`,
  `health_snapshot.json`, `queue/quarantined_summaries.json`, `truncation.txt`
  where applicable; no payloads included.

### Pin rotation drill

- Deploy sample-app against a mock server presenting certificate A, with pin A
  configured.
- Push a managed-config update carrying pin B. Restart. Traffic against cert A
  fails with a `certificate_pin_failure` audit entry and a
  `KioskOpsErrorListener.TransportError` callback.
- Switch mock server to certificate B. Traffic succeeds.

### Database corruption recovery

- On a rooted device or emulator (`adb root`), overwrite a page of
  `kiosk_ops_queue.db` or the encrypted variant with random bytes.
- Restart the app. Verify:
  - `KioskOpsErrorListener.StorageError` fires with the expected message.
  - Audit trail records `database_corruption_detected`.
  - Subsequent `enqueue` calls succeed against the recreated DB.

### Process-kill mid-sync

- Enqueue 100 events against a mock server that delays responses by 30 seconds.
- Call `syncOnce()`. When the server sees the request body, before it responds,
  `adb shell am force-stop` the app.
- Relaunch. Verify:
  - `reconcileStaleSending` (shipped in v1.2.0) flips `SENDING` rows back to
    `PENDING` on init.
  - Next `syncOnce()` drains the backlog.

### In-place upgrade from prior minor

- Install sample-app built against the previous minor (e.g., 1.1.0). Enqueue a
  handful of events. Trigger one heartbeat.
- Without clearing app data, upgrade to the new version.
- Confirm:
  - Queue DB opens without a schema-migration crash.
  - `InstallSecret` migrated from legacy plaintext to Keystore-wrapped format
    (for the 1.2.0 upgrade specifically).
  - Sync path drains the backlog.
  - `KioskOpsErrorListener` receives no `StorageError` or unexpected errors.

## Gaps tracked for future releases

Items on the v1.3.0 roadmap that would tighten this document further:

- Backward-compatibility fixture: a pinned copy of the sample-app at the previous
  minor that can be built against the current AAR. Makes the in-place upgrade
  drill mechanical.
- Mock-server infrastructure (MockWebServer wrapper with cert rotation). Makes
  the pin rotation drill scripted.
- Performance benchmarks (androidx.benchmark). Catches silent regressions in
  `heartbeat`, `enqueue`, and the sync batch-send hot path.
- `scripts/pre-release-verify.sh` extended to drive a Robolectric-based
  subset of the corruption and process-kill drills for CI-time verification.

## History

| Version | First verified against | Notes |
|---|---|---|
| 1.2.0 | This document's first use | Checklist formalised as part of the release itself. |
