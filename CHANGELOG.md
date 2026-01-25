# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[0.2.0]: https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/releases/tag/v0.2.0
[0.1.0]: https://github.com/pzverkov/KioskOps-SDK-Android-Enterprise/releases/tag/v0.1.0
