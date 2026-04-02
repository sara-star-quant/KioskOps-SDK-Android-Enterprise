# CJIS Security Policy v5.9: KioskOps SDK Control Mapping

> **Disclaimer:** This document is an engineering reference only. It does not
> constitute a compliance certification, legal advice, or security assessment.
> The SDK has not been independently audited or certified against the CJIS
> Security Policy. You must conduct your own assessment with qualified
> professionals before deploying in law enforcement or criminal justice
> environments. Many CJIS requirements are organizational, procedural, or
> infrastructure-level and cannot be addressed by an SDK alone.

## Scope

The CJIS Security Policy governs access to Criminal Justice Information (CJI).
This mapping covers policy areas where the SDK provides direct or supporting
technical controls. The `cjisDefaults()` config preset enables the recommended
combination for CJIS-adjacent deployments.

---

## 5.4 Access Control

| Control ID | Control Title | SDK Feature | Implementation | Notes/Limitations |
|------------|--------------|-------------|----------------|-------------------|
| 5.4.1.1 | Account Management | userId tracking | Optional userId on queue and audit events; `deleteUserData()` for account removal | Account lifecycle management is host-app responsibility |
| 5.4.4 | Information Flow Enforcement | Data classification; field encryption | RESTRICTED tag prevents uncontrolled flow; field-level encryption protects CJI fields | Network flow enforcement requires host-app and infrastructure controls |
| 5.4.6 | Least Privilege | Config presets | `cjisDefaults()` enables minimum necessary SDK features; debug logs restricted to debug builds | Application-level privilege enforcement is host-app responsibility |

## 5.5 Authentication

| Control ID | Control Title | SDK Feature | Implementation | Notes/Limitations |
|------------|--------------|-------------|----------------|-------------------|
| 5.5.2 | Identification Policy | HMAC request signing; mTLS | Device-level identification via mTLS client certificates; request signing with timestamp + nonce | User identification is host-app responsibility |
| 5.5.5 | Session Timeout | None | SDK does not manage user sessions | Session timeout must be enforced by the host application; this is outside SDK scope |
| 5.5.6 | Advanced Authentication (AA) | mTLS | Client certificate mutual authentication | Multi-factor authentication is host-app responsibility; SDK provides the transport channel |

## 5.6 Identification

| Control ID | Control Title | SDK Feature | Implementation | Notes/Limitations |
|------------|--------------|-------------|----------------|-------------------|
| 5.6.1 | Uniquely Identify Individuals | userId tracking; pseudonymous deviceId | SDK-scoped deviceId stable per install; optional userId per event | User identity verification is host-app responsibility |
| 5.6.2 | Identifier Management | Device ID reset | `resetSdkDeviceId()` for device re-provisioning; key rotation for crypto identifiers | Host app manages user identifier lifecycle |

## 5.7 Encryption

| Control ID | Control Title | SDK Feature | Implementation | Notes/Limitations |
|------------|--------------|-------------|----------------|-------------------|
| 5.7.1 | Encryption at Rest | AES-256-GCM; Android Keystore | Queue payloads, telemetry, audit, exported logs all encrypted; hardware-backed key storage | StrongBox availability depends on device hardware |
| 5.7.1.2 | Symmetric Keys (128-bit minimum) | AES-256-GCM | 256-bit keys exceed the 128-bit minimum requirement | Key generation uses Android Keystore |
| 5.7.2 | Encryption in Transit | TLS; certificate pinning; mTLS | Certificate pinning with SHA-256 pins; mutual TLS; Certificate Transparency | Only active when network sync is enabled (opt-in) |
| 5.7.2.2 | FIPS 140-2 Compliance | FIPS 140 runtime detection | Detects FIPS-mode Conscrypt/BoringSSL at runtime; `cjisDefaults()` enables detection | SDK does not bundle a FIPS module; host must supply a FIPS 140-2 validated provider |

## 5.8 Media Protection

| Control ID | Control Title | SDK Feature | Implementation | Notes/Limitations |
|------------|--------------|-------------|----------------|-------------------|
| 5.8.1 | Media Storage and Access | Encryption at rest | All SDK data stores encrypted with AES-256-GCM; Android file-system permissions enforced | Physical media protection is infrastructure responsibility |
| 5.8.2 | Media Transport | Encryption in transit | TLS + HMAC signing for sync; exported diagnostics ZIPs contain encrypted data | Physical transport controls are organizational responsibility |
| 5.8.3 | Media Sanitization | Data deletion APIs | `wipeAllSdkData()` removes all SDK data; `deleteUserData()` for targeted erasure | Covers SDK data only; host app handles its own media sanitization |

## 5.9 Physical Protection

| Control ID | Control Title | SDK Feature | Implementation | Notes/Limitations |
|------------|--------------|-------------|----------------|-------------------|
| 5.9.1 | Physically Secure Location | Device posture snapshot | Lock-task mode detection; device owner status reporting | Physical security is facility/deployment responsibility; SDK provides posture awareness only |

## 5.10 Audit and Accountability

| Control ID | Control Title | SDK Feature | Implementation | Notes/Limitations |
|------------|--------------|-------------|----------------|-------------------|
| 5.10.1.1 | Auditable Events | Tamper-evident audit trail | Room-backed SHA-256 hash chain; all SDK operations logged; config transitions recorded | Audit covers SDK operations; host app must audit its own actions |
| 5.10.1.2 | Content of Audit Records | Structured audit entries | Timestamp, event type, userId (optional), deviceId, hash chain, optional device attestation signature | Custom metadata can be attached by host app |
| 5.10.1.3 | Audit Monitoring/Reporting | Integrity verification; error listener | `verifyAuditChain()` detects tampering; `setErrorListener()` for failure callbacks | Real-time monitoring and alerting requires host-app integration |
| 5.10.1.4 | Time Stamps | Audit timestamps | System clock timestamps on all audit entries | NTP synchronization is device/infrastructure responsibility |
| 5.10.1.5 | Protection of Audit Information | Encryption at rest; hash chain | Audit trail encrypted with AES-256-GCM; hash chain detects modification | 365-day minimum retention enforced by SDK |

---

## Controls outside SDK scope

The following CJIS policy areas require host-app, infrastructure, or organizational controls:

- **5.1 (Information Exchange Agreements)**: organizational/contractual
- **5.2 (Security Awareness Training)**: organizational
- **5.3 (Incident Response)**: organizational; SDK provides audit trail and error listener as inputs
- **5.5.5 (Session Timeout)**: must be enforced by the host application
- **5.9 (Physical Protection)**: largely facility/deployment responsibility
- **5.11 (Configuration Management)**: organizational; SDK provides `cjisDefaults()` preset
- **5.12 (Personnel Security)**: organizational
- **5.13 (Mobile Devices)**: MDM/EMM responsibility; SDK provides device posture data
