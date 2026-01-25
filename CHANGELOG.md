# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[0.4.0]: https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/releases/tag/v0.4.0
[0.3.0]: https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/releases/tag/v0.3.0
[0.2.0]: https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/releases/tag/v0.2.0
[0.1.0]: https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/releases/tag/v0.1.0
