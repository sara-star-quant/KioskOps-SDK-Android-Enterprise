# NIST SP 800-171 Rev 2 -- KioskOps SDK Control Mapping

> **Disclaimer:** This document is an engineering reference only. It does not
> constitute a compliance certification, legal advice, or security assessment.
> The SDK has not been independently audited or certified against NIST SP
> 800-171. You must conduct your own assessment with qualified professionals
> before claiming compliance. Many 800-171 controls require organizational
> policy, infrastructure, and host-application measures beyond SDK scope.

## Scope

NIST SP 800-171 protects Controlled Unclassified Information (CUI) in
non-federal systems. This mapping covers the control families where the SDK
provides direct or supporting technical controls. The `cuiDefaults()` config
preset enables the recommended combination.

---

## Access Control (AC)

| Control ID | Control Title | SDK Feature | Implementation | Notes/Limitations |
|------------|--------------|-------------|----------------|-------------------|
| 3.1.1 | Limit system access to authorized users | Data classification tagging | Events tagged PUBLIC/INTERNAL/CONFIDENTIAL/RESTRICTED; host app enforces access decisions | Access control enforcement is host-app responsibility |
| 3.1.2 | Limit system access to authorized functions | Config presets | `cuiDefaults()` restricts SDK to CUI-appropriate settings; debug logs restricted to debug builds | Host app must enforce user-level function restrictions |
| 3.1.3 | Control flow of CUI | Field-level encryption; PII redaction | Sensitive fields encrypted before storage; PII detected and redacted before persist | Network data flow controlled by host app and backend |
| 3.1.22 | Control information posted publicly | Data classification | RESTRICTED/CONFIDENTIAL tags prevent accidental exposure; PII detection flags sensitive content | Host app must enforce publication controls |

## Audit and Accountability (AU)

| Control ID | Control Title | SDK Feature | Implementation | Notes/Limitations |
|------------|--------------|-------------|----------------|-------------------|
| 3.3.1 | Create and retain system audit logs | Tamper-evident audit trail | Room-backed SHA-256 hash chain; persistent across restarts; 365-day minimum retention | Audit covers SDK operations only; host app needs its own audit |
| 3.3.2 | Ensure actions can be traced to individuals | userId tracking | Optional userId column on queue and audit events for data subject identification | Host app must supply userId at event creation time |
| 3.3.3 | Review and update audit events | Audit chain integrity verification | `verifyAuditChain()` API detects tampering or corruption | Automated review/alerting is host-app responsibility |
| 3.3.4 | Alert on audit process failure | Error listener | `setErrorListener()` callback for non-fatal errors including audit failures | Host app must route alerts to monitoring infrastructure |
| 3.3.5 | Correlate audit records | Structured audit entries | Each entry includes timestamp, event type, deviceId, hash chain linkage | Cross-system correlation requires backend aggregation |

## Identification and Authentication (IA)

| Control ID | Control Title | SDK Feature | Implementation | Notes/Limitations |
|------------|--------------|-------------|----------------|-------------------|
| 3.5.2 | Authenticate users, devices, or processes | HMAC request signing; mTLS | HMAC signs sync requests with timestamp + nonce; mTLS provides mutual authentication | User authentication is host-app responsibility |
| 3.5.10 | Store and transmit only cryptographically protected passwords | Encryption at rest | AES-256-GCM via Android Keystore; no plaintext credential storage in SDK | SDK does not manage user passwords |

## Media Protection (MP)

| Control ID | Control Title | SDK Feature | Implementation | Notes/Limitations |
|------------|--------------|-------------|----------------|-------------------|
| 3.8.1 | Protect CUI on digital media | Encryption at rest | Queue payloads, telemetry, audit, and exported logs encrypted with AES-256-GCM | Protection limited to SDK-managed data stores |
| 3.8.3 | Sanitize media before disposal | Data deletion APIs | `wipeAllSdkData()` removes all SDK data; `deleteUserData()` for per-user erasure | Host app must handle its own data sanitization |
| 3.8.9 | Protect CUI at rest | AES-256-GCM; Android Keystore | Hardware-backed key storage; FIPS 140-2/3 runtime detection available | StrongBox support varies by device hardware |

## System and Communications Protection (SC)

| Control ID | Control Title | SDK Feature | Implementation | Notes/Limitations |
|------------|--------------|-------------|----------------|-------------------|
| 3.13.1 | Monitor and protect communications at boundaries | Certificate pinning; mTLS; CT | SHA-256 pin validation; mutual TLS; Certificate Transparency log checks | Only applies when network sync is enabled (opt-in) |
| 3.13.8 | Implement cryptographic mechanisms for CUI in transit | TLS + HMAC signing | Sync requests require TLS; optional HMAC adds integrity layer | Server-side TLS configuration is infrastructure responsibility |
| 3.13.10 | Establish cryptographic keys using approved methods | Key rotation; key attestation | `VersionedCryptoProvider` for rotation; hardware-backed attestation reporting; OWASP 2023 derivation | FIPS 140-2/3 detected at runtime but not enforced by SDK |
| 3.13.11 | Employ FIPS-validated cryptography for CUI | FIPS 140 detection | Runtime check for FIPS-mode Conscrypt/BoringSSL provider; `cuiDefaults()` enables detection | SDK detects but does not bundle a FIPS module; host must provide FIPS-validated provider |

## System and Information Integrity (SI)

| Control ID | Control Title | SDK Feature | Implementation | Notes/Limitations |
|------------|--------------|-------------|----------------|-------------------|
| 3.14.1 | Identify and correct system flaws | CycloneDX SBOM | BOM generated in CI for dependency vulnerability tracking | Host app must monitor SBOM and apply patches |
| 3.14.2 | Provide protection from malicious code | Event validation; anomaly detection | JSON Schema validation rejects malformed events; statistical anomaly detection flags suspicious patterns | SDK validates its own inputs; host app handles broader threat protection |
| 3.14.6 | Monitor the system to detect attacks | Anomaly detection | Payload size z-score, event rate tracking, schema deviation, field cardinality analysis | Requires baseline period; initial events will not trigger flags |
| 3.14.7 | Identify unauthorized use | Signed audit entries | Device attestation signatures provide non-repudiation | Optional feature; requires Android API 24+ |

---

## Controls requiring host-app or infrastructure implementation

The following 800-171 families are largely outside SDK scope:

- **Awareness and Training (AT)** -- organizational responsibility
- **Configuration Management (CM)** -- SDK provides `cuiDefaults()` preset but CM policies are organizational
- **Incident Response (IR)** -- SDK provides error listener and audit trail; IR process is organizational
- **Maintenance (MA)** -- infrastructure responsibility
- **Personnel Security (PS)** -- organizational responsibility
- **Physical Protection (PE)** -- facility responsibility
- **Risk Assessment (RA)** -- organizational responsibility
- **Security Assessment (CA)** -- organizational responsibility
