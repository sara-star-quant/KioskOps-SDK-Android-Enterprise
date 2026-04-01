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

## v0.6.0 -- API Freeze Readiness [RELEASED]

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

## v0.7.0 -- Pre-1.0 Hardening

Focus: Java interop, error handling, observability, API contract finalization, federal/gov compliance tooling, accessibility, and quality gates before the 1.0 freeze.

### Java Interop & Developer Experience
- [ ] `@JvmStatic` on `KioskOpsSdk.init()`, `get()`, `getOrNull()` companion methods
- [ ] `@JvmOverloads` on public functions with default parameters
- [ ] Blocking wrapper methods for Java callers (`enqueueBlocking()`, `syncOnceBlocking()`)
- [ ] `healthCheck()` API returning structured status (connectivity, queue depth, last sync timestamp, auth state)
- [ ] Runtime debug log level toggle via ADB broadcast intent (for field technicians)
- [ ] Error callback hooks for consumers (`KioskOpsSdk.setErrorListener()`)

### API Contract
- [ ] Mark `HardwareAcceleratedDetector` as `@RequiresOptIn` experimental (stub until companion `kiosk-ops-sdk-ml` artifact ships)
- [ ] Document `GeofenceManager` as extension point (stub `registerGeofences`/`unregisterGeofences` are intentional for subclassing)
- [ ] Nullability and sealed hierarchy audit (no accidental `open` classes, no mutable collection returns)
- [ ] Threading/dispatcher documentation per public `suspend` function (main-safe vs IO)
- [ ] Document sync ordering limitation (batches may arrive out of order under retry)

### Build Infrastructure
- [ ] Gradle Daemon toolchain (auto-detect/download JDK, align CLI and IDE, single Daemon)

### Supply Chain & Compliance
- [ ] SBOM generation (CycloneDX) in CI for EO 14028 / FedRAMP supply chain requirements
- [ ] Transitive dependency CVE audit and Dependabot/Renovate setup
- [ ] FIPS 140 runtime check (detect BoringCrypto/FIPS mode on device) and documentation of which SDK crypto operations are FIPS-compliant

### Accessibility
- [ ] DebugOverlay WCAG 2.1 AA pass (content descriptions, minimum 48dp touch targets, sufficient contrast ratios, screen reader compatibility)
- [ ] Sample app accessibility review (demonstrate accessible patterns consumers can copy)

### Test Coverage (target: 65% line coverage)
- [ ] Audit trail tests: AuditTrail record/verify round-trip, PersistentAuditTrail chain integrity, retention enforcement, GDPR user deletion
- [ ] Queue DAO tests: QueueDao with in-memory Room DB (state transitions, backoff eligibility, retention purge, user queries)
- [ ] RemoteConfigManager tests: version monotonicity, cooldown, A/B variant assignment, rollback
- [ ] Transport security tests: MtlsClientBuilder, CertificateTransparencyValidator, OkHttpTransport error paths
- [ ] Fleet tests: DevicePostureCollector with Robolectric shadows, DiagnosticsExporter zip validation
- [ ] Room migration tests with `androidx.room:room-testing` MigrationTestHelper
- [ ] ProGuard integration test (sample-app release build with minifyEnabled)

---

## v0.8.0 -- Compliance Presets, Documentation & Developer Experience

Focus: Compliance presets for government deployments, expanded sample app, comprehensive documentation, and developer experience polish.

### Configuration Presets
- [ ] `cuiDefaults()` - NIST SP 800-171 preset for Controlled Unclassified Information (defense contractors)
- [ ] `cjisDefaults()` - CJIS Security Policy preset for law enforcement kiosk deployments (session timeout, encryption requirements)
- [ ] `asdEssentialEightDefaults()` - ASD Essential Eight preset for Australian government deployments

### Compliance Mapping Documents
- [ ] NIST SP 800-171 control mapping (CUI protection families: AC, AU, IA, MP, SC, SI)
- [ ] CJIS Security Policy mapping (sections 5.4-5.10: access control, auditing, encryption)
- [ ] ASD Essential Eight mapping (application hardening, logging, patching)
- [ ] BSI IT-Grundschutz mapping (APP.4.4, SYS.3.2.2 controls already referenced in code)
- [ ] Australian Privacy Act / APPs mapping (data export/deletion alignment with Australian Privacy Principles)
- [ ] Data flow documentation (what data leaves the device, encryption posture at rest and in transit)
- [ ] VPAT / Accessibility Conformance Report (Section 508 / EN 301 549)

### Developer Experience
- [ ] Expanded sample app with real-world scenarios (batch enqueue, error handling, networking, data rights, geofence switching)
- [ ] README overhaul (configuration matrix, troubleshooting, dependency impact, quick-start under 5 minutes)
- [ ] Published AAR size and method count in release notes

### Test Coverage (target: 60% line coverage)
- [ ] Crypto module: ConditionalCryptoProvider, VersionedCryptoProvider, FieldLevelEncryptor
- [ ] GeofenceManager state machine (permission checks, transitions, profile switching)
- [ ] KioskOpsSdk orchestrator integration tests (enqueue pipeline end-to-end, sync, heartbeat)
- [ ] WorkManager worker tests (KioskOpsSyncWorker, KioskOpsEventSyncWorker, DiagnosticsSchedulerWorker)
- [ ] Debug module: DebugOverlay state aggregation, NetworkLoggingInterceptor
- [ ] Structured logging: StructuredLogger multi-sink dispatch, FileSink

### Documentation Accessibility
- [ ] Dokka HTML output WCAG compliance review (alt text, heading structure, keyboard navigation)
- [ ] Compliance mapping documents in accessible format

---

## v1.0.0 -- Stable Release & Platform Expansion

Focus: Production stability, distribution, LTS commitment, and cross-platform support.

### Stability
- [ ] API freeze and semantic versioning commitment
- [ ] Long-term support (LTS) branch
- [ ] Migration guides for all breaking changes

### Distribution
- [ ] Maven Central publication with PGP-signed artifacts
- [ ] BOM artifact (`com.peterz.kioskops:kioskops-bom`) for coordinated version management
- [ ] Gradle Version Catalog snippet for consumers
- [ ] Source JAR and Javadoc JAR published alongside the AAR

### Platform Support
- [ ] Kotlin Multiplatform (KMP) with iOS target
- [ ] React Native bridge package
- [ ] Flutter plugin

### Documentation
- [ ] API reference documentation (Dokka)
- [ ] Video tutorials and integration walkthroughs
- [ ] Sample apps for common use cases (retail, logistics, field service)

### Security
- [ ] Pre-filled SIG (Standardized Information Gathering) questionnaire template
- [ ] Third-party penetration test (annual, recognized firm)

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
