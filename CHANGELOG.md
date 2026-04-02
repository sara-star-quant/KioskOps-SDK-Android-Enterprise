# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.8.0] - 2026-04-03

Compliance presets, database encryption, reactive APIs, SDK lifecycle, and comprehensive test coverage.

### Added

- `cuiDefaults()` config preset for NIST SP 800-171 / Controlled Unclassified Information deployments
- `cjisDefaults()` config preset for CJIS Security Policy / law enforcement kiosk deployments
- `asdEssentialEightDefaults()` config preset for Australian government (ASD Essential Eight) deployments
- `DatabaseEncryptionPolicy` and `DatabaseEncryptionProvider` for SQLCipher database-at-rest encryption; enabled by default in CUI and CJIS presets
- `DatabaseCorruptionHandler` for corruption recovery with `KioskOpsErrorListener` notification
- `queueDepthFlow()` reactive API for polling-based queue depth observation via Kotlin Flow
- `healthStatusFlow()` reactive API for polling-based health status streaming via Kotlin Flow
- `shutdown()` method for graceful SDK teardown (scope cancellation, singleton cleanup)
- Compliance mapping documents: NIST SP 800-171, CJIS, ASD Essential Eight, BSI IT-Grundschutz, Australian Privacy Act, Data Flow

### Changed

- Package renamed from `com.peterz.kioskops` to `com.sarastarquant.kioskops` (aligns with sarastarquant.com domain)
- Maven group ID changed from `com.peterz.kioskops` to `com.sarastarquant.kioskops`
- `resetForTesting()` now cancels coroutine scopes to prevent test leaks
- Kover coverage threshold raised from 65% to 70%
- CycloneDX plugin upgraded from 2.2.0 to 3.2.3 (fixes CVE-2025-64518, CVE-2025-48924)

### Build

- CUI and CJIS presets enable `DatabaseEncryptionPolicy` by default (requires `sqlcipher-android` on classpath)
- Room databases (`QueueDatabase`, `AuditDatabase`) accept optional `SupportSQLiteOpenHelper.Factory` for encryption

### Test Coverage

- 821 tests (up from 517 in v0.7.0)
- 75.5% line coverage (up from 68% in v0.7.0); threshold enforced at 70%
- Pipeline rejection tests: validation, PII, anomaly, size guardrails, queue overflow, idempotency dedup
- Queue repository tests: overflow strategies (DROP_OLDEST, DROP_NEWEST, BLOCK), byte-based limits, data field preservation
- Transport security tests: certificate pinning (95.3% coverage), CT validation, mTLS
- VersionedCryptoProvider: key rotation, multi-version decrypt, blob validation
- GeofenceManager: state machine, transitions, profiles, listeners

---

## [0.7.0] - 2026-04-02

Pre-1.0 hardening release; Java interop, error observability, supply chain compliance, and test coverage push.

### Added

- `@JvmStatic` on `KioskOpsSdk.init()`, `get()`, `getOrNull()`, `SDK_VERSION` for direct Java access
- `@JvmOverloads` on `enqueue`, `enqueueDetailed`, `quarantinedEvents`, `heartbeat`, `uploadDiagnosticsNow`, `verifyAuditIntegrity`, `processRemoteDiagnosticsTrigger`, `init`, and `KioskOpsConfig` constructor
- Blocking wrapper methods for Java callers: `enqueueBlocking()`, `enqueueDetailedBlocking()`, `syncOnceBlocking()`, `heartbeatBlocking()`, `queueDepthBlocking()`, `healthCheckBlocking()` returning `CompletableFuture`
- `healthCheck()` API returning `HealthCheckResult` (queue depth, sync status, auth state, encryption, SDK version)
- `KioskOpsErrorListener` and `KioskOpsError` sealed class for non-fatal error callbacks via `setErrorListener()`
- `ExperimentalKioskOpsApi` opt-in annotation for experimental APIs
- `DebugLogBroadcastReceiver` for runtime log level toggle via ADB broadcast intent
- `FipsComplianceChecker` for FIPS 140-2/3 runtime detection with documentation of FIPS-compliant SDK crypto operations
- CycloneDX SBOM generation in CI for EO 14028 / FedRAMP supply chain requirements
- Kover 65% line coverage enforcement threshold in CI

### Changed

- `HardwareAcceleratedDetector` now annotated with `@ExperimentalKioskOpsApi` (callers must opt in)
- `GeofenceManager` is now `open` with `protected open` extension point methods (`registerGeofences`, `unregisterGeofences`)
- Removed jackson-core force-patch from Dokka V1 build classpath (Dokka V2 does not use Jackson)
- Added Gradle foojay toolchain resolver for automatic JDK provisioning
- Consumer ProGuard rules updated for new types (`HealthCheckResult`, `KioskOpsErrorListener`, `KioskOpsError`, `ExperimentalKioskOpsApi`)
- Error listener notified on sync transient and permanent failures
- SDK library `isMinifyEnabled` set to `false` for release builds (libraries must not self-minify; consumer R8 handles shrinking)
- Threading KDoc added to all public suspend functions (main-safe vs IO dispatcher)
- Sync ordering limitation documented on `SyncEngine`

### Fixed

- `QuarantinedEventSummary` now annotated with `@Serializable` (was missing, caused runtime crash in diagnostics export)
- `RemoteConfigSignatureTest` test isolation: clearing `ConfigDatabase` singleton in tearDown prevents stale database poisoning across tests
- `ConfigDatabase.setInstance` now accepts nullable to allow clearing singleton in tests

### Build

- CycloneDX SBOM plugin added to build; SBOM artifact uploaded in CI and release workflows
- Kover `koverVerifyDebug` step added to CI pipeline
- SBOM artifact uploaded with 90-day retention
- Dokka V2 plugin mode enabled (`V2EnabledWithHelpers`); CI updated to `dokkaGeneratePublicationHtml`
- room-testing dependency added for migration tests
- Test coverage: 68% line coverage (517 tests, up from 52% / 400 tests in v0.6.0)

### Security

- FIPS 140 runtime checker documents which SDK crypto operations are FIPS-compliant (AES-256-GCM, ECDSA P-256, SHA-256, HMAC-SHA256)
- Dependabot already configured for Gradle and GitHub Actions (verified, not new)

### Documentation

- `GeofenceManager` extension point methods documented with subclass integration guidance
- `DebugLogBroadcastReceiver` usage documented for field technician ADB access

### Test Coverage

- PersistentAuditTrail: record/verify round-trip, chain integrity, retention, GDPR user deletion, statistics, export
- QueueDao: state transitions, backoff eligibility, retention purge, user queries, anomaly filtering, quarantine summaries
- RemoteConfigManager: version monotonicity, cooldown enforcement, A/B variants, rollback, minimum version, disabled policy, missing version
- OkHttpTransport: success path, HTTP 4xx/5xx classification, auth header injection, SDK version header, example.invalid guard
- HealthCheck and ErrorListener: structured status, queue depth reflection, heartbeat reason, error types
- FipsComplianceChecker: runtime detection, data class accessibility
- DevicePostureCollector: device info, groups, resilience to system service failures
- DiagnosticsExporter: ZIP creation, manifest content, health snapshot, quarantined summaries
- Room migrations: QueueDatabase v3->v4 column/index verification, AuditDatabase v1->v2 userId column
- ProGuard integration: sample-app release build with R8 minification validates consumer-rules.pro

### Accessibility

- Sample app rewritten with WCAG 2.1 AA patterns: semantic headings, content descriptions, 48dp touch targets, live region announcements, sufficient contrast

---

## [0.6.0] - 2026-04-01

API freeze readiness release; hardening the API surface, adding quality tooling, removing deprecated APIs, and implementing real config signature verification in preparation for v1.0.

### Added

- Binary Compatibility Validator (BCV) enforcing API stability via `apiCheck` in CI
- Dokka HTML API documentation generation (`dokkaHtml` task)
- Detekt static analysis with baseline configuration
- JaCoCo code coverage reporting (report-only, no enforcement threshold)
- `@RestrictTo(LIBRARY)` annotations on internal Room DAOs, database classes, entities, and WorkManager workers
- Real ECDSA P-256 signature verification for remote config bundles (replaces placeholder stub)

### Changed

- Consumer ProGuard rules refined from blanket keep to categorized rules per API surface, Room, serialization, and WorkManager
- CI pipeline expanded with binary compatibility check, Detekt, JaCoCo coverage, and Dokka doc generation steps
- androidx-core-ktx 1.17.0 -> 1.18.0
- androidx-work 2.11.0 -> 2.11.1
- androidx-datastore 1.2.0 -> 1.2.1
- JUnit 5 5.11.4 -> 5.14.3

### Removed

- `SecurityPolicy.denylistJsonKeys` and `SecurityPolicy.allowRawPayloadStorage` - use `PiiPolicy` for structured PII detection instead
- `EnqueueResult.Rejected.DenylistedKey` - PII detection is now handled by `PiiPolicy` in the enqueue pipeline
- `DataRightsManager.exportLocalFiles()` - use `exportAllLocalData()` instead
- Unused `crypto` parameter from `PersistentAuditTrail` constructor

### Fixed

- `KioskOpsSdk.init()` now throws `KioskOpsAlreadyInitializedException` if called twice (previously silently overwrote the first instance, losing in-memory state)
- `KioskOpsSdk.get()` now throws `KioskOpsNotInitializedException` instead of generic `IllegalStateException`
- `QueueRepository` now distinguishes disk-full (`SQLiteFullException` -> `QueueFull`) from duplicate idempotency key (`SQLiteConstraintException` -> `DuplicateIdempotency`) and unexpected errors (`Unknown`)

### Documentation

- Added `MIGRATION.md` with code examples for all v0.5.x to v0.6.0 breaking changes
- Updated all docs to replace stale "denylist" terminology with `PiiPolicy` references
- Updated CONTRIBUTING.md with new quality tool tasks (Detekt, apiCheck, Dokka, JaCoCo)

### Build

- Switched from JaCoCo to Kover for accurate Kotlin code coverage (JaCoCo underreported by ~40% due to coroutine bytecode)
- `KioskOpsSdk.SDK_VERSION` now reads from `BuildConfig` (injected from `gradle.properties`); version is defined in one place only
- Added `androidx.work:work-testing` for WorkManager test initialization
- Test coverage: 52% line coverage (up from 47% with new tests for metrics, tracing, profiler, init, signature verification, enqueue errors)

### Security

- Internal database classes and WorkManager workers annotated as library-internal to discourage direct consumer access
- Consumer ProGuard rules now explicitly document what is preserved and why
- Remote config signature verification now uses real ECDSA P-256 cryptographic verification (BSI APP.4.4.A3)

---

## [0.5.3] - 2026-03-17

Patch release with documentation, project governance, and release workflow updates.

### Changed

- Copyright holder updated to SARA STAR QUANT LLC across all source files and LICENSE
- Added legal and security disclaimers to README, SECURITY.md, SECURITY_COMPLIANCE.md, FEATURES.md
- Updated documentation with v0.5.0 feature coverage
- Release workflow restructured for immutable release compatibility (workflow_dispatch trigger)
- GitHub Packages publish step is non-fatal on version conflict (safe re-runs)
- Branch protection enabled on main (PR reviews required, build status check)
- SECURITY.md supported versions updated to >= 0.5.x only

---

## [0.5.0] - 2026-03-17

Data quality, compliance, and validation release targeting NGO and US federal (NIST 800-53 / FedRAMP / GDPR) deployments.

### Added

#### Event Validation (NIST SI-10)
- **JsonSchemaValidator** with Draft 2020-12 subset (type, required, properties, pattern, minLength/maxLength, enum, format, minimum/maximum, items, additionalProperties)
- **SchemaRegistry** for thread-safe event type schema management
- **ValidationPolicy** with strict/permissive modes and configurable unknown event type handling
- **ValidationListener** callback interface for validation outcomes

#### PII Protection (NIST SI-19)
- **RegexPiiDetector** with compiled patterns for EMAIL, PHONE, SSN, CREDIT_CARD, ADDRESS, DOB, IP_ADDRESS, PASSPORT, NATIONAL_ID
- **PiiRedactor** for JSON tree manipulation; replaces values with `[REDACTED:TYPE]` markers
- **PiiPolicy** with REJECT, REDACT_VALUE, FLAG_AND_ALLOW actions
- **DataClassification** tagging (PUBLIC/INTERNAL/CONFIDENTIAL/RESTRICTED)
- **HardwareAcceleratedDetector** stub for optional NNAPI-backed inference on API 27+
- Pluggable **PiiDetector** interface for custom implementations

#### Anomaly Detection (NIST SI-4)
- **StatisticalAnomalyDetector** with payload size z-score, event rate anomaly, schema deviation scoring, and field cardinality tracking
- **AnomalyPolicy** with LOW/MEDIUM/HIGH sensitivity and configurable flag/reject thresholds
- Pluggable **AnomalyDetector** interface for custom implementations

#### Field-Level Encryption (NIST SC-28)
- **FieldLevelEncryptor** encrypts individual JSON fields into `{"__enc":"...","__alg":"AES-256-GCM","__kid":"v1"}` envelopes
- **FieldEncryptionPolicy** with per-event-type and default field configuration

#### Compliance APIs
- **DataRightsManager** rewrite with full GDPR support:
  - `exportUserData(userId)` - Art. 20 data portability (ZIP export)
  - `deleteUserData(userId)` - Art. 17 right to erasure
  - `wipeAllSdkData()` - full device-level data wipe
  - `exportAllLocalData()` - replaces deprecated `exportLocalFiles()`
- **RetentionEnforcer** for centralized retention across all stores
- **NistControl** source-retention annotation for compliance mapping
- Minimum audit retention (365 days) per NIST AU-11

#### Database Migrations
- **AuditDatabase v1->v2**: non-destructive migration adding userId column (replaces destructive fallback)
- **QueueDatabase v3->v4**: adds userId, dataClassification, anomalyScore columns

#### Audit Trail Unification
- All SDK events now route through PersistentAuditTrail (Room-backed)
- SyncEngine updated to use PersistentAuditTrail
- userId propagation through enqueue and audit pipelines

#### Debug & Development
- **DebugOverlay** data-only state inspector (queue depth, quarantine count, policy hash, feature flags)
- **PerformanceProfiler** for operation timing (enqueue, validation, PII scan, anomaly, encryption, sync)
- **Config presets**: `KioskOpsConfig.fedRampDefaults()` and `KioskOpsConfig.gdprDefaults()`

### Changed

- `KioskOpsConfig` now includes validationPolicy, piiPolicy, fieldEncryptionPolicy, dataClassificationPolicy, anomalyPolicy
- `KioskOpsSdk.enqueue()` and `enqueueDetailed()` now accept optional `userId` parameter
- `RetentionPolicy` now includes `minimumAuditRetentionDays` (default 365)
- `EnqueueResult` sealed class expanded with ValidationFailed, PiiDetected, AnomalyRejected, PiiRedacted
- `KioskOpsSdk.SDK_VERSION` updated to "0.5.0"
- `gradle.properties` VERSION_NAME updated to "0.5.0-SNAPSHOT"
- PolicyDriftDetector projection includes validation, PII, and anomaly policy knobs

### Deprecated

- `SecurityPolicy.denylistJsonKeys` - use PiiPolicy for structured PII detection
- `DataRightsManager.exportLocalFiles()` - use `exportAllLocalData()`

### Security

- Enqueue pipeline validates events before persistence (NIST SI-10)
- PII detection runs before encryption to satisfy NIST SI-19 scan-before-persist
- All new pipeline steps are fail-safe (try-catch with fallback to allow-through)
- AuditDatabase migration is non-destructive (compliance hazard resolved)
- Anomaly detection catches data exfiltration patterns (NIST SI-4)
- Field-level encryption protects sensitive attributes independently of document encryption

---

## [0.4.0] - 2026-01-25

Observability and developer experience release with geofence-aware policy switching.

### Added

#### Observability Infrastructure
- **ObservabilityPolicy** for centralized observability configuration
  - Configurable logging, tracing, and metrics collection
  - Development, production, and full observability presets
  - Trace sampling rate control (ISO 27001 A.12.4)
- **CorrelationContext** for distributed tracing correlation
  - Thread-local correlation ID propagation
  - W3C Trace Context compatible trace/span IDs
  - Cross-thread context propagation helpers
- **Correlated operations** with automatic timing and metadata
  - `correlatedOperation()` for suspending operations
  - `correlatedBlock()` for blocking operations
  - `trackedOperation()` with success/failure callbacks

#### Structured Logging
- **Multi-sink StructuredLogger** with automatic context enrichment
  - Correlation IDs, trace IDs, thread info auto-included
  - Level filtering per sink (BSI SYS.3.2.2.A12)
- **LogcatSink** for Android Logcat output
  - Configurable tag prefix and min level
  - Key fields visible in log output
- **FileSink** for in-memory ring buffer logging
  - Configurable max lines with automatic rotation
  - JSON and text export formats
  - Filter by level, tag, or correlation ID

#### Distributed Tracing
- **OpenTelemetry-compatible tracing API**
  - TracerProvider, Tracer, SpanBuilder, Span interfaces
  - SpanContext with W3C traceparent support
  - Span events and exception recording
- **KioskOpsTracer** implementation
  - Configurable sampling rate
  - Automatic correlation context integration
  - No-op fallback when tracing disabled

#### Metrics Collection
- **OpenTelemetry-compatible metrics API**
  - MeterProvider, Meter interfaces
  - Counter, Gauge, Histogram instruments
- **MetricRegistry** for in-memory metric storage
  - Thread-safe concurrent data structures
  - Histogram bucket distribution
- **PrometheusExporter** for metrics export
  - Standard Prometheus text format
  - Debug builds only

#### Debug Features
- **EventInspector** for queue debugging (debug builds only)
  - Paginated event listing with metadata
  - Queue statistics and state breakdown
  - Quarantine management (retry, clear)
- **NetworkLoggingInterceptor** for HTTP debugging
  - Request/response logging with correlation
  - Sensitive header redaction
  - Duration timing

#### Geofence-Aware Policy Switching
- **GeofencePolicy** for location-based configuration
  - Circular geofence regions with Haversine distance
  - Priority-based region overlap handling
  - Dwell time and accuracy configuration
  - Privacy-preserving (no coordinate storage - GDPR Art. 5)
- **GeofenceRegion** with validation and containment checks
  - W3C-compliant coordinate validation
  - Min/max radius enforcement (Android limits)
- **PolicyProfile** for named configuration profiles
  - Sync, telemetry, diagnostics policy overrides
  - Factory methods for common profiles (high connectivity, battery saver, offline-first)
- **GeofenceManager** for monitoring lifecycle
  - Permission and location service checks
  - Region transition handling with audit logging
  - Listener interface for callbacks

### Changed

- `KioskOpsConfig` now includes `observabilityPolicy`, `geofencePolicy`, `policyProfiles`
- `gradle.properties` VERSION_NAME updated to "0.4.0-SNAPSHOT"

### Security

- All observability features are opt-in with secure defaults
- Remote logging/tracing endpoints require HTTPS
- Debug features restricted to debug builds via `@RequiresDebugBuild`
- Geofence location processing is local-only (no transmission)

---

## [0.3.0] - 2026-01-25

Fleet operations release focused on enhanced tooling for managing device fleets at scale.

### Added

#### Remote Configuration
- **Push-based config refresh** via managed config or FCM (`RemoteConfigManager`)
  - Version monotonicity enforcement prevents rollback attacks (BSI APP.4.4.A5)
  - Optional ECDSA P-256 signature verification for config bundles
  - Cooldown period prevents rapid config cycling attacks
- **Config versioning and rollback support** (`ConfigVersion`, `ConfigRollbackResult`)
  - Retained version history for safe rollback
  - Minimum version floor prevents security downgrades
  - Audit logging of all version transitions
- **A/B testing configuration support**
  - Deterministic variant assignment based on device ID
  - Sticky assignment across config updates (optional)

#### Diagnostics Scheduling
- **Scheduled diagnostics collection** (`DiagnosticsSchedulerWorker`)
  - Daily or weekly schedule with configurable time (BSI SYS.3.2.2.A8)
  - Power-efficient WorkManager integration
  - Prefers idle device and unmetered network
- **Remote diagnostics trigger** via managed config (`RemoteDiagnosticsTrigger`)
  - Rate limiting (maxRemoteTriggersPerDay) prevents abuse (BSI APP.4.4.A7)
  - Cooldown period between triggers
  - Deduplication of trigger requests
  - All triggers (accepted/rejected) are audit logged
- **Auto-upload option** for scheduled diagnostics (requires uploader configuration)

#### Extended Device Posture
- **Battery status** (`BatteryStatus`)
  - Level, charging state, health, power saver mode
  - Critical/low battery detection
  - No PII collected (GDPR compliant)
- **Storage status** (`StorageStatus`)
  - Internal/external storage metrics
  - Low storage and critical storage detection
  - Human-readable formatting helper
- **Connectivity status** (`ConnectivityStatus`)
  - Network type, validation, metered status
  - WiFi signal level, cellular type
  - VPN and airplane mode detection
  - No IP addresses, MAC addresses, or network identifiers (GDPR compliant)

#### Device Groups
- **Device group tagging** for fleet segmentation (`DeviceGroupProvider`)
  - Groups assignable via managed config or programmatic API
  - Merged view of managed + local assignments
  - ISO 27001 A.8 asset classification support
  - Group-based policy targeting in fleet management

### Changed

- `KioskOpsConfig` now includes `remoteConfigPolicy` and `diagnosticsSchedulePolicy`
- `DevicePosture` now includes `battery`, `storage`, `connectivity`, `deviceGroups` fields
- `KioskOpsSdk.SDK_VERSION` updated to "0.3.0"
- `applySchedulingFromConfig()` now schedules diagnostics collection

### Security

- Config version monotonicity prevents rollback attacks
- Remote trigger rate limiting prevents DoS attacks
- All policy-relevant operations are audit logged
- Extended posture collection follows GDPR privacy principles

---

## [0.2.0] - 2025-01-25

Security hardening release focused on certification readiness (SOC 2, FedRAMP preparation).

### Added

#### Transport Security
- **Certificate pinning** with configurable pin sets (`TransportSecurityPolicy`)
  - Multiple pins per hostname for rotation support
  - Wildcard hostname matching (e.g., `*.example.com`)
  - Audit events on pin validation failure
- **mTLS client certificate support** (`MtlsClientBuilder`, `ClientCertificateProvider`)
  - Hardware-backed client certificates via Android Keystore
  - Flexible provider interface for custom certificate sources
- **Certificate Transparency validation** (optional)
  - SCT validation for misissued certificate detection
  - Configurable per-deployment

#### Cryptography
- **Key rotation with versioned encryption** (`VersionedCryptoProvider`)
  - Multi-version key management for seamless rotation
  - Backward-compatible decryption of old data
  - Configurable rotation policy (`KeyRotationPolicy`)
- **Hardware-backed key attestation reporting** (`KeyAttestationReporter`)
  - Security level detection (SOFTWARE, TEE, STRONGBOX)
  - Attestation challenge-response for remote verification
  - Device posture now includes attestation status
- **Configurable key derivation parameters** (`KeyDerivationConfig`)
  - OWASP 2023 recommended defaults (310,000 iterations)
  - Configurable algorithm, iteration count, salt length
  - High-security preset available

#### Audit Trail
- **Persistent audit chain across app restarts** (Room-backed)
  - Audit events survive app restarts and updates
  - Chain generation tracking for integrity verification
  - Separate database from event queue for isolation
- **Signed audit entries with device attestation** (`KeystoreAttestationProvider`)
  - ECDSA P-256 signatures with hardware-backed keys
  - Attestation certificate chain for verification
  - Opt-in via `SecurityPolicy.signAuditEntries`
- **Audit trail integrity verification API**
  - `verifyAuditIntegrity()` - Check hash chain validity
  - `getAuditStatistics()` - Event counts and chain status
  - `exportSignedAuditRange()` - Export signed events for compliance

### Changed

- `SecurityPolicy` now includes `keyRotationPolicy`, `keyDerivationConfig`, `useRoomBackedAudit`, `signAuditEntries`
- `KioskOpsConfig` now includes `transportSecurityPolicy`
- `DevicePosture` now includes attestation status fields
- `HealthSnapshot` now includes key attestation information
- Certificate validation failures are treated as permanent transport failures

### Security

- Transport layer now validates server certificates against configured pins
- mTLS provides mutual authentication for high-security deployments
- Key attestation enables verification of hardware-backed key storage
- Signed audit entries provide non-repudiation for compliance audits

## [0.1.0] - 2025-01-23

Initial release of KioskOps SDK for Android Enterprise.

### Added

#### Security & Data Protection
- AES-256-GCM encryption at rest via Android Keystore
- Automatic PII filtering with configurable denylist
- Tamper-evident audit trail with SHA-256 hash chain
- Configurable payload size limits (default: 64 KB)

#### Event Queue
- Durable Room-based storage with encryption
- Queue pressure control (10K events / 50 MB default)
- Overflow strategies: `DROP_OLDEST`, `DROP_NEWEST`, `BLOCK`
- Configurable retention policies (7-30 days)

#### Fleet Operations
- Policy drift detection via config hash comparison
- Device posture snapshots (device owner, lock-task mode, security patch level)
- On-demand diagnostics export (ZIP bundle)
- Managed configuration support via Android Enterprise

#### Network Sync (Opt-in)
- Batch ingest with per-event acknowledgements
- Exponential backoff with jitter (10s base, 6h max)
- Optional HMAC request signing
- Poison event quarantine for non-retryable failures

#### Developer Experience
- Sample application with reference integration
- OpenAPI specification for server API contract
- Comprehensive documentation

### Requirements
- Android API 26+ (Android 8.0)
- Java 17+
- Kotlin 2.1+

[0.6.0]: https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/releases/tag/v0.6.0
[0.5.3]: https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/releases/tag/v0.5.3
[0.5.2]: https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/releases/tag/v0.5.2
[0.5.1]: https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/releases/tag/v0.5.1
[0.5.0]: https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/releases/tag/v0.5.0
[0.4.0]: https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/releases/tag/v0.4.0
[0.3.0]: https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/releases/tag/v0.3.0
[0.2.0]: https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/releases/tag/v0.2.0
[0.1.0]: https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/releases/tag/v0.1.0
