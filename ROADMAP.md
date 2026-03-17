# Roadmap

This document outlines planned features and improvements for KioskOps SDK, organized by target release.

> **Note**: This roadmap reflects current planning and may change based on community feedback and enterprise customer needs.

---

## v0.2.0 — Security Hardening [RELEASED]

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

## v0.3.0 — Fleet Operations [RELEASED]

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

## v0.4.0 — Observability & Developer Experience [RELEASED]

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

## v0.5.0 — Data & Validation [RELEASED]

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

## v1.0.0 — Stable Release & Platform Expansion

Focus: Production stability, LTS commitment, and cross-platform support.

### Stability
- [ ] API freeze and semantic versioning commitment
- [ ] Long-term support (LTS) branch
- [ ] Migration guides for all breaking changes

### Platform Support
- [ ] Kotlin Multiplatform (KMP) with iOS target
- [ ] React Native bridge package
- [ ] Flutter plugin

### Documentation
- [ ] API reference documentation (Dokka)
- [ ] Video tutorials and integration walkthroughs
- [ ] Sample apps for common use cases (retail, logistics, field service)

---

## Future Considerations

Features under consideration for post-1.0 releases:

### Enterprise Features
- [ ] Multi-tenant event partitioning
- [ ] End-to-end encryption with customer-managed keys (BYOK)
- [ ] Offline-first sync conflict resolution strategies
- [ ] Webhook delivery for real-time event streaming

### Regional & Compliance
- [ ] Per-region endpoint routing helpers (EU/US/AU/APAC)
- [ ] SOC 2 Type II audit trail enhancements
- [ ] FedRAMP-compatible deployment patterns
- [ ] HIPAA BAA-ready configuration presets

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
