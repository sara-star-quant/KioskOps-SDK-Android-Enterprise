# Roadmap

This document outlines planned features and improvements for KioskOps SDK, organized by target release.

> **Note**: This roadmap reflects current planning and may change based on community feedback and enterprise customer needs.

---

## v0.2.0 - Security Hardening [RELEASED]

Focus: Strengthen security posture for high-compliance deployments.

### Transport Security
- [x] Certificate pinning with configurable pin sets
- [x] mTLS client certificate support
- [x] Certificate transparency log validation (optional)

### Cryptography
- [x] Key rotation support with versioned encryption
- [x] Hardware-backed key attestation reporting
- [x] Configurable key derivation parameters

### Audit Trail
- [x] Persistent audit chain across app restarts (Room-backed)
- [x] Signed audit entries with device attestation
- [x] Audit trail integrity verification API

---

## v0.3.0 - Fleet Operations [RELEASED]

Focus: Enhanced tooling for managing device fleets at scale.

### Remote Configuration
- [x] Push-based config refresh via managed config or FCM
- [x] Config versioning and rollback support
- [x] A/B testing configuration support

### Diagnostics
- [x] Batch diagnostics collection for fleet-wide analysis
- [x] Remote diagnostics trigger via managed config
- [x] Diagnostic report scheduling (daily/weekly summaries)

### Device Management
- [x] Enhanced device posture reporting (battery, storage, connectivity)
- [x] Geofence-aware policy switching (completed in v0.4.0)
- [x] Device group tagging for fleet segmentation

---

## v0.4.0 - Observability & Developer Experience [RELEASED]

Focus: Better debugging, monitoring, and integration options.

### Logging & Tracing
- [x] OpenTelemetry integration for distributed tracing
- [x] Structured logging with configurable sinks (Logcat, file, remote)
- [x] Correlation IDs across SDK operations

### Debugging
- [x] SDK debug overlay for development builds (completed in v0.5.0)
- [x] Event inspector (view queued events in debug builds)
- [x] Network request/response logging (debug only)

### Metrics
- [x] Prometheus-compatible metrics endpoint
- [x] Custom metrics API for host app instrumentation
- [x] Performance profiling hooks (completed in v0.5.0)

### Geofencing (from v0.3.0)
- [x] Geofence-aware policy switching
- [x] Policy profiles for location-based configuration
- [x] Privacy-preserving location handling (no coordinate storage)

---

## v0.5.0 - Data & Validation [RELEASED]

Focus: Stronger data quality and compliance tooling.

### Event Validation
- [x] JSON Schema-based event validation (Draft 2020-12 subset)
- [x] Schema registry integration (SchemaRegistry)
- [x] Validation failure callbacks and metrics (ValidationListener)

### PII Protection
- [x] Regex-based PII detection (RegexPiiDetector) with pluggable interface
- [x] ML-assisted PII detection stub (HardwareAcceleratedDetector, NNAPI API 27+)
- [x] Field-level encryption for sensitive attributes (FieldLevelEncryptor)
- [x] Data classification tagging (DataClassification)
- [x] PII redaction with configurable actions (REJECT/REDACT/FLAG)

### Anomaly Detection
- [x] Statistical anomaly detector (payload size, event rate, schema deviation, cardinality)
- [x] Pluggable AnomalyDetector interface

### Compliance APIs
- [x] GDPR data export API (`dataRights.exportUserData()`)
- [x] GDPR deletion API (`dataRights.deleteUserData()`)
- [x] Full device wipe (`dataRights.wipeAllSdkData()`)
- [x] Data retention policy enforcement with audit logging (RetentionEnforcer)
- [x] NIST compliance annotations (@NistControl)

### Debug & Development
- [x] SDK debug overlay (DebugOverlay)
- [x] Performance profiling hooks (PerformanceProfiler)
- [x] Config presets (fedRampDefaults, gdprDefaults)

---

## v0.6.0 API Freeze Readiness [RELEASED]

Focus: Harden the API surface, add quality tooling, and prepare for v1.0 API freeze.

### API Stability
- [x] Binary Compatibility Validator (BCV) with CI enforcement
- [x] Initial API dump baseline (`.api` files)
- [x] `@RestrictTo(LIBRARY)` on internal Room DAOs and WorkManager workers
- [x] Real ECDSA P-256 config signature verification (replaces placeholder)
- [x] Removed deprecated `SecurityPolicy.denylistJsonKeys` (replaced by `PiiPolicy`)
- [x] Removed deprecated `DataRightsManager.exportLocalFiles()`
- [x] Removed unused `crypto` parameter from `PersistentAuditTrail`
- [x] Double-init protection (`KioskOpsAlreadyInitializedException`)
- [x] SDK-specific exception types (`KioskOpsException`, `KioskOpsNotInitializedException`)
- [x] Disk-full handling in QueueRepository (distinct from duplicate idempotency)
- [x] Migration guide (`MIGRATION.md`) for v0.5.x to v0.6.0 breaking changes

### Quality Tooling
- [x] Dokka API documentation generation
- [x] Detekt static analysis with baseline
- [x] Kover code coverage reporting (replaced JaCoCo for accurate Kotlin coverage)
- [x] Tests for observability metrics (MetricRegistry, PrometheusExporter)
- [x] Tests for distributed tracing (KioskOpsTracer, span lifecycle)
- [x] Tests for debug profiler (PerformanceProfiler)
- [x] Tests for SDK init (double-init, exceptions, version)
- [x] Tests for config signature verification (ECDSA P-256)
- [x] Tests for enqueue error differentiation (duplicate idempotency, disk full)

### Maintenance
- [x] Consumer ProGuard rules refined from blanket keep to categorized rules
- [x] Dependency updates (core-ktx, work, datastore, junit5)

---

## v0.7.0 Pre-1.0 Hardening

Focus: Java interop, error handling, observability, API contract finalization, federal/gov compliance tooling, accessibility, and quality gates before the 1.0 freeze.

### Java Interop & Developer Experience
- [x] `@JvmStatic` on `KioskOpsSdk.init()`, `get()`, `getOrNull()` companion methods
- [x] `@JvmOverloads` on public functions with default parameters
- [x] Blocking wrapper methods for Java callers (`enqueueBlocking()`, `syncOnceBlocking()`)
- [x] `healthCheck()` API returning structured status (connectivity, queue depth, last sync timestamp, auth state)
- [x] Runtime debug log level toggle via ADB broadcast intent (for field technicians)
- [x] Error callback hooks for consumers (`KioskOpsSdk.setErrorListener()`)

### API Contract
- [x] Mark `HardwareAcceleratedDetector` as `@RequiresOptIn` experimental (stub until companion `kiosk-ops-sdk-ml` artifact ships)
- [x] Document `GeofenceManager` as extension point (stub `registerGeofences`/`unregisterGeofences` are intentional for subclassing)
- [x] Nullability and sealed hierarchy audit (no accidental `open` classes, no mutable collection returns)
- [x] Threading/dispatcher documentation per public `suspend` function (main-safe vs IO)
- [x] Document sync ordering limitation (batches may arrive out of order under retry)

### Build Infrastructure
- [x] Dokka V1 -> V2 migration (eliminates vulnerable jackson-core, netty, woodstox transitive dependencies from build classpath)
- [x] Gradle Daemon toolchain (auto-detect/download JDK, align CLI and IDE, single Daemon)
- [x] GitHub Packages page: description, installation instructions, README rendering, link to docs

### Supply Chain & Compliance
- [x] SBOM generation (CycloneDX) in CI for EO 14028 / FedRAMP supply chain requirements
- [x] Transitive dependency CVE audit and Dependabot/Renovate setup
- [x] FIPS 140 runtime check (detect BoringCrypto/FIPS mode on device) and documentation of which SDK crypto operations are FIPS-compliant

### Accessibility
- [x] DebugOverlay WCAG 2.1 AA pass (content descriptions, minimum 48dp touch targets, sufficient contrast ratios, screen reader compatibility)
- [x] Sample app accessibility review (demonstrate accessible patterns consumers can copy)

### Test Coverage (target: 65% line coverage)
- [x] Audit trail tests: AuditTrail record/verify round-trip, PersistentAuditTrail chain integrity, retention enforcement, GDPR user deletion
- [x] Queue DAO tests: QueueDao with in-memory Room DB (state transitions, backoff eligibility, retention purge, user queries)
- [x] RemoteConfigManager tests: version monotonicity, cooldown, A/B variant assignment, rollback
- [x] Transport security tests: MtlsClientBuilder, CertificateTransparencyValidator, OkHttpTransport error paths
- [x] Fleet tests: DevicePostureCollector with Robolectric shadows, DiagnosticsExporter zip validation
- [x] Room migration tests with `androidx.room:room-testing` MigrationTestHelper
- [x] ProGuard integration test (sample-app release build with minifyEnabled)

---

## v0.8.0 Compliance Presets, Documentation and Developer Experience

Focus: Compliance presets for government deployments, expanded sample app, comprehensive documentation, and developer experience polish.

### Configuration Presets
- [x] `cuiDefaults()` - NIST SP 800-171 preset for Controlled Unclassified Information (defense contractors)
- [x] `cjisDefaults()` - CJIS Security Policy preset for law enforcement kiosk deployments (session timeout, encryption requirements)
- [x] `asdEssentialEightDefaults()` - ASD Essential Eight preset for Australian government deployments

### Compliance Mapping Documents
- [x] NIST SP 800-171 control mapping (CUI protection families: AC, AU, IA, MP, SC, SI)
- [x] CJIS Security Policy mapping (sections 5.4-5.10: access control, auditing, encryption)
- [x] ASD Essential Eight mapping (application hardening, logging, patching)
- [x] BSI IT-Grundschutz mapping (APP.4.4, SYS.3.2.2 controls already referenced in code)
- [x] Australian Privacy Act / APPs mapping (data export/deletion alignment with Australian Privacy Principles)
- [x] Data flow documentation (what data leaves the device, encryption posture at rest and in transit)
- [ ] VPAT / Accessibility Conformance Report (Section 508 / EN 301 549)

### Developer Experience
- [x] Expanded sample app with real-world scenarios (batch enqueue, error handling, networking, data rights, geofence switching) (completed in v0.9.0)
- [ ] README overhaul (configuration matrix, troubleshooting, dependency impact, quick-start under 5 minutes)
- [ ] Published AAR size and method count in release notes

### Security & Data Protection
- [x] Database-at-rest encryption via SQLCipher (`SupportFactory` for Room) for federal/DoD deployments where device disk encryption alone is insufficient
- [x] Database corruption recovery with `DatabaseErrorHandler` callback and consumer notification via `KioskOpsErrorListener`

### Reactive APIs
- [x] `queueDepthFlow(): Flow<Long>` for reactive queue depth observation
- [x] `healthStatusFlow(): Flow<HealthCheckResult>` for health status streaming
- [x] Config update event flow from `RemoteConfigManager` (completed in v0.9.0)

### SDK Lifecycle
- [x] Cancel `javaInteropScope` on SDK teardown / process death
- [x] `ProcessLifecycleOwner` integration for auto-heartbeat on background (completed in v0.9.0)
- [x] Graceful cleanup on app death (flush queues, close databases)

### Test Coverage (target: 70% line coverage)
- [x] Crypto module: VersionedCryptoProvider key rotation, versioned blob format, multi-version decrypt, cleanup policy
- [x] GeofenceManager state machine (permission checks, transitions, profile switching)
- [x] KioskOpsSdk orchestrator integration tests (enqueue pipeline end-to-end, sync, heartbeat)
- [x] Instrumented test suite for crypto (AndroidKeyStore), Room (on-device SQLite), and WorkManager (real scheduler) (completed in v0.9.0)

### CI & Size Budget
- [x] AAR size and method count regression gate in CI (completed in v0.9.0)
- [ ] Dex method count tracking per release

### Documentation Accessibility
- [ ] Dokka HTML output WCAG compliance review (alt text, heading structure, keyboard navigation)
- [ ] Compliance mapping documents in accessible format

---

## v0.9.0 Lifecycle, Config Flow, Global PII, Baseline Seeding, Maven Central [RELEASED]

Focus: Lifecycle-aware telemetry, reactive config updates, global PII coverage, anomaly baseline seeding, instrumented tests, and Maven Central distribution.

### Lifecycle & Config
- [x] `ProcessLifecycleOwner` integration: auto-heartbeat on app background via `SdkLifecycleObserver`
- [x] `configUpdateFlow()` emitting `Applied`, `Rejected`, `RolledBack` config change events

### PII & Anomaly Detection
- [x] Country-specific PII patterns: AU TFN, UK NIN, CA SIN, DE Steuer-ID, JP My Number, IN Aadhaar, BR CPF, ZA ID
- [x] Safe pattern exclusions (UUIDs, timestamps, version strings) to reduce false positives
- [x] Anomaly baseline seeding: learning mode with `baselineEventCount`, `seedBaseline()` API, `BaselineStats`

### Sample App
- [x] `BatchEnqueueActivity` (queue overflow demo)
- [x] `DataRightsActivity` (GDPR Art. 17/20 walkthrough)

### Build & CI
- [x] `minSdk` raised from 26 to 31 (Android 12)
- [x] AAR size CI gate (1.5 MB limit)
- [x] Detekt SARIF upload to GitHub Security tab
- [x] Build attestation summary (test count, coverage, AAR SHA-256, SBOM hash, toolchain versions)

### Distribution
- [x] Maven Central publication with GPG signing
- [x] Sources JAR and Javadoc JAR published alongside the AAR

### Test Coverage (target: 70% line coverage)
- [x] 1003 tests, 76.5% line coverage; Kover threshold at 70%
- [x] Instrumented test suite (`androidTest`): crypto on real AndroidKeyStore, Room on device SQLite, WorkManager on real scheduler
- [x] CI emulator step for instrumented tests (main branch only)

---

## v1.0.0 Stable Release and Platform Expansion [RELEASED]

Focus: Production stability, distribution, LTS commitment, and cross-platform support.

### Stability
- [x] API freeze and semantic versioning commitment
- [ ] Long-term support (LTS) branch (deferred to v1.2.0)
- [x] Migration guides for all breaking changes

### Distribution
- [x] Maven Central publication with PGP-signed artifacts (completed in v0.9.0)
- [ ] BOM artifact (`com.sarastarquant.kioskops:kioskops-bom`) for coordinated version management (deferred to v1.2.0)
- [ ] Gradle Version Catalog snippet for consumers (deferred to v1.2.0)
- [x] Source JAR and Javadoc JAR published alongside the AAR (completed in v0.9.0)

---

## v1.1.0 Security Hardening [RELEASED]

Focus: Fix audit-surfaced defects from the 1.0.0 release, raise the Android
baseline to the current minimum security posture, and close silent-failure
paths in cryptographic and transport code.

### Critical crypto and transport fixes
- [x] `DatabaseEncryptionProvider` now wraps a random 256-bit SQLCipher passphrase
  with the Keystore AES-GCM key; removed the broken `key.encoded ?: key.toString()`
  fallback that produced a non-random passphrase on hardware-backed devices
- [x] `FieldLevelEncryptor` throws `FieldEncryptionException` on failure instead
  of silently falling back to plaintext; event pipeline rejects events with
  `EnqueueResult.Rejected.FieldEncryptionFailed`
- [x] `MtlsClientBuilder` throws `MtlsConfigurationException` on failure instead
  of silently downgrading to unauthenticated TLS
- [x] `CertificateTransparencyValidator.isFromKnownCa` issuer-string bypass removed;
  validation now requires embedded SCTs regardless of issuer DN

### High-severity fixes
- [x] `DataRightsManager.wipeAllSdkData` enumerates all `kioskops*` SharedPreferences
  files and all `kioskops*` Android Keystore aliases
- [x] `KeystoreAttestationProvider` generates 32-byte `SecureRandom` attestation
  challenges (was: predictable device-model + timestamp)
- [x] `VersionedCryptoProvider.deleteKeyVersion` logs Keystore deletion failures
  and retains metadata so a future cleanup retries
- [x] `KioskOpsConfig.toString()` redacts `adminExitPin`

### Platform baseline
- [x] `minSdk` raised from 31 to 33 (Android 13). Drops Android 12 / 12L support
- [x] Simplified `Build.VERSION.SDK_INT >= S` / `>= N` / `>= 27` branches in
  crypto, attestation, and connectivity modules

### Dependency hygiene
- [x] Dependabot PR triage for GitHub Actions bumps (safe minor/patch)
- [x] Open Dependabot alerts (bouncycastle 1.79, plexus-utils) reviewed; all
  occur only in buildscript classpath (AGP, cyclonedx plugin) and do not
  ship in the published AAR; tracked for AGP 9.x evaluation in v1.2.0

### OpenSSF Scorecard follow-ups
- [x] Binary-Artifacts: documented gradle-wrapper.jar as required-binary
  (gradle-wrapper-validation workflow already present)
- [x] Fuzzing: Jazzer integration already exists; tracked to enable the
  `fuzzTest` task in a scheduled workflow so Scorecard sees it
- [x] Branch-Protection / Code-Review: documented as single-maintainer
  constraint; tracked for CII Best Practices badge application in v1.2.0

---

## v1.2.0 Distribution and Platform Expansion

Focus: Coordinate multi-module version management, expand the Dependabot/
toolchain surface, and prepare for cross-platform consumers.

### Stability
- [ ] Long-term support (LTS) branch (cherry-pick security fixes to v1.1.x)
- [ ] AGP 9.x evaluation - brings Bouncy Castle 1.84+ and closes the
  remaining build-classpath CVE exposure

### Distribution
- [ ] BOM artifact (`com.sarastarquant.kioskops:kioskops-bom`) for coordinated
  version management
- [ ] Gradle Version Catalog snippet for consumers

### Platform Support
- [ ] Kotlin Multiplatform (KMP) with iOS target
- [ ] React Native bridge package
- [ ] Flutter plugin

### Documentation
- [ ] API reference documentation (Dokka) published to GitHub Pages
- [ ] Video tutorials and integration walkthroughs
- [ ] Sample apps for common use cases (retail, logistics, field service)

### Security and assurance
- [ ] Pre-filled SIG (Standardized Information Gathering) questionnaire template
- [ ] Third-party penetration test (annual, recognized firm)
- [ ] OpenSSF CII Best Practices badge (passing)
- [ ] Scheduled weekly fuzzing workflow on `main`
- [ ] Full SCT signature verification in `CertificateTransparencyValidator`
  (integrate `com.appmattus:certificatetransparency` or equivalent)

### Audit follow-ups (from 1.0.0 review, medium-severity)
- [ ] Replace `SecureKeyDerivation.deriveDeterministic` ISO-8859-1 bridge with
  HKDF-based byte construction and explicit length prefixing
- [ ] Lock `FileSink` size counter under a dedicated mutex to prevent negative
  values under concurrent emit
- [ ] Switch `CertificatePinningInterceptor` to OkHttp's native
  `certificatePinner`, so pin validation runs during the TLS handshake
  before the request body is exchanged
- [ ] High-security presets (`fedRampDefaults`, `cuiDefaults`, `cjisDefaults`)
  validate that `baseUrl` HTTPS endpoints have pins configured or CT
  enabled; log `ERROR` if not
- [ ] `RemoteConfigPolicy.pilotDefaults` marked `@RequiresOptIn` /
  `@Deprecated(WARNING)` to prevent accidental production use

---

## Future Considerations

Features under consideration for post-1.0 releases:

### Enterprise Features
- [ ] Multi-tenant event partitioning
- [ ] End-to-end encryption with customer-managed keys (BYOK)
- [ ] Offline-first sync conflict resolution strategies
- [ ] Webhook delivery for real-time event streaming
- [ ] Optional lightweight mode without Room (SQLite-only queue for resource-constrained devices)
- [ ] Module split: `kioskops-core`, `kioskops-compliance`, `kioskops-analytics`

### Observability & Debugging
- [ ] Persistent crash-safe log export (survives process death)
- [ ] SDK health dashboard metrics (events dropped, queue depth, upload failures)
- [ ] Event delivery confirmation callbacks (not fire-and-forget)

### Regional & Compliance
- [ ] Per-region endpoint routing helpers (EU/US/AU/APAC)
- [ ] SOC 2 Type II audit trail enhancements
- [ ] FedRAMP-compatible deployment patterns
- [ ] HIPAA BAA-ready configuration presets
- [ ] DPA (Data Processing Agreement) template for GDPR

### Integrations
- [ ] Firebase Analytics bridge
- [ ] Datadog/New Relic APM integration
- [ ] Splunk/Elastic log shipping
- [ ] MDM vendor integrations (Intune, Workspace ONE, Knox Manage)

---

## Contributing

Have a feature request or want to contribute?

- Open an issue on [GitHub](https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/issues)
- See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidelines
- Enterprise customers: contact support for prioritization discussions
