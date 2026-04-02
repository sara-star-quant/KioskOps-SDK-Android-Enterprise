# BSI IT-Grundschutz: KioskOps SDK Control Mapping

> **Disclaimer:** This document is an engineering reference only. It does not
> constitute a compliance certification, legal advice, or security assessment.
> The SDK has not been independently audited or certified against BSI
> IT-Grundschutz. You must conduct your own assessment with qualified
> professionals. IT-Grundschutz requirements are organizational-level; an SDK
> addresses only a subset of technical controls.

## Scope

BSI IT-Grundschutz is the German federal standard for information security
management. This mapping covers the two module groups referenced in the SDK
source code: APP.4.4 (Application Development Security) and SYS.3.2.2
(Mobile Device Management). Controls are drawn from the BSI IT-Grundschutz
Compendium (Edition 2023).

---

## APP.4.4 Application Development Security

| Control ID | Control Title | SDK Feature | Implementation | Notes/Limitations |
|------------|--------------|-------------|----------------|-------------------|
| APP.4.4.A1 | Secure software architecture | Offline-first architecture | Local-first data storage with explicit opt-in for network sync; defense-in-depth layering (encryption, validation, audit) | Architecture decisions documented in SECURITY_COMPLIANCE.md |
| APP.4.4.A2 | Input validation | Event validation; PII detection | JSON Schema Draft 2020-12 validation; payload size limits (64 KB default); PII regex detection with REJECT/REDACT/FLAG actions | Validation covers SDK event pipeline; host app must validate its own inputs |
| APP.4.4.A3 | Cryptographic signature verification | Signed remote config | ECDSA P-256 signature verification for remote configuration bundles | Referenced directly in SDK source; config bundles rejected if signature is invalid |
| APP.4.4.A4 | Secure data storage | AES-256-GCM encryption at rest | Android Keystore-backed encryption for queue, telemetry, audit, and exported logs; field-level encryption for sensitive attributes | StrongBox support depends on device hardware |
| APP.4.4.A5 | Version monotonicity / rollback prevention | Remote config version control | Strictly increasing version numbers enforced; minimum version floor blocks rollback below security updates | Referenced directly in SDK source (RemoteConfigPolicy) |
| APP.4.4.A6 | Secure communication | Certificate pinning; mTLS; CT | SHA-256 pin validation; mutual TLS; Certificate Transparency log checks; HMAC request signing | Active only when network sync is enabled (opt-in) |
| APP.4.4.A7 | Rate limiting and abuse prevention | Diagnostics rate limiting | Maximum triggers per day; cooldown period between triggers; deduplication prevents replay | Referenced directly in SDK source (DiagnosticsSchedulePolicy) |
| APP.4.4.A8 | Logging and audit trail | Tamper-evident audit trail | Room-backed SHA-256 hash chain; 365-day minimum retention; optional signed entries with device attestation | Audit covers SDK operations only |
| APP.4.4.A9 | Error handling | Error listener; fail-safe validation | `setErrorListener()` for non-fatal errors; validation errors never block pipeline in permissive mode; no sensitive data in error messages | Debug log output restricted to debug builds |
| APP.4.4.A10 | Dependency management | CycloneDX SBOM | BOM generated in CI pipeline; dependency versions tracked; jackson-core CVE mitigations applied | SDK consumers must monitor SBOM for vulnerability updates |

## SYS.3.2.2 Mobile Device Management

| Control ID | Control Title | SDK Feature | Implementation | Notes/Limitations |
|------------|--------------|-------------|----------------|-------------------|
| SYS.3.2.2.A1 | Device identification | Pseudonymous deviceId | SDK-scoped device identifier stable per install; resettable via `resetSdkDeviceId()` | No hardware identifiers collected; deviceId is SDK-internal |
| SYS.3.2.2.A2 | Device posture assessment | Device posture snapshot | Lock-task mode, device owner status, security patch level, battery, storage, connectivity | Extended posture follows data minimization (no IP, MAC, SSID) |
| SYS.3.2.2.A3 | Encryption of mobile data | AES-256-GCM at rest | All SDK data stores encrypted; hardware-backed key storage via Android Keystore | FIPS 140-2/3 runtime detection available |
| SYS.3.2.2.A4 | Secure communication from devices | TLS; certificate pinning; mTLS | Transport security enforced when sync is enabled; HMAC request signing for integrity | SDK does not auto-upload; sync is opt-in |
| SYS.3.2.2.A5 | Data minimization on devices | PII redaction; telemetry allow-list | PII detected and redacted before persist; only allowed telemetry keys stored; no payload contents in telemetry/audit | Connectivity posture: network type only, no IP/MAC/SSID/cell tower |
| SYS.3.2.2.A6 | Remote wipe capability | Data deletion APIs | `wipeAllSdkData()` removes all SDK data; `deleteUserData()` for per-user erasure | Covers SDK data only; full device wipe is MDM responsibility |
| SYS.3.2.2.A7 | Configuration management | Remote configuration; config presets | Signed remote config with version monotonicity; `fedRampDefaults()`, `gdprDefaults()`, `cjisDefaults()`, `asdEssentialEightDefaults()` | Config distribution infrastructure is host-app responsibility |
| SYS.3.2.2.A8 | Audit and monitoring | Audit trail; error listener; health check | Hash-chain audit trail; structured health snapshot (queue, sync, auth, encryption); error callbacks | Centralized monitoring aggregation is infrastructure responsibility |

---

## Controls outside SDK scope

The following IT-Grundschutz requirements are outside SDK scope:

- **ORP (Organizational)**: security policies, personnel, training
- **CON (Concepts)**: data protection concept, crypto concept (SDK provides technical building blocks)
- **OPS (Operations)**: patch management, incident handling, backup procedures
- **DER (Detection and Response)**: SIEM integration, forensic readiness (SDK provides audit trail as input)
- **INF (Infrastructure)**: physical security, network segmentation
- **NET (Network)**: firewall, VPN, network monitoring
- **SYS.3.2.2 device enrollment and MDM policy enforcement**: MDM/EMM responsibility
