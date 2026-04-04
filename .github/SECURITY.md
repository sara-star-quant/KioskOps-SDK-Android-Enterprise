# Security Policy

> **Important:** This document describes the security features and vulnerability
> reporting process for KioskOps SDK. It is not security advice, and the authors
> make no guarantees about the fitness of this software for any particular
> security requirement. You are responsible for your own security assessments,
> penetration testing, and compliance validation. The authors accept no liability
> for security incidents arising from the use of this software.

## Supported Versions

| Version | Supported |
| ------- | --------- |
| >= 0.5.x | Yes      |
| < 0.5.0  | No       |

Versions prior to 0.5.0 are not supported and will not receive security fixes.
Please upgrade to the latest release.

## Reporting a Vulnerability

We take security vulnerabilities seriously. If you discover a security issue, please report it responsibly.

### How to Report

**Please do NOT report security vulnerabilities through public GitHub issues.**

Instead, please send an email to: **pzverkov@protonmail.com**

Include the following information:
- Type of vulnerability (e.g., injection, data exposure, authentication bypass)
- Full paths of affected source files
- Step-by-step instructions to reproduce the issue
- Proof-of-concept or exploit code (if possible)
- Impact assessment and potential attack scenarios

### Response Timeline

- **Initial Response**: Within 48 hours
- **Status Update**: Within 7 days
- **Resolution Target**: Within 90 days (depending on severity)

### What to Expect

1. Acknowledgment of your report within 48 hours
2. Regular updates on the progress of addressing the vulnerability
3. Credit in the security advisory (if desired) once the issue is resolved
4. Notification when the fix is released

### Scope

This security policy applies to:
- The KioskOps SDK library (`kiosk-ops-sdk` module)
- Official documentation and examples

Out of scope:
- Third-party dependencies (report to their maintainers)
- Sample applications used for demonstration purposes

## Security Best Practices

When using KioskOps SDK in production:

1. **Keep the SDK updated** to the latest version
2. **Enable all security defaults** (`SecurityPolicy.maximalistDefaults()`)
3. **Enable PII detection** (`PiiPolicy.rejectDefaults()` or `PiiPolicy.redactDefaults()`)
4. **Use certificate pinning** for network sync endpoints
5. **Review audit trails** regularly for anomalies
6. **Enable anomaly detection** for production workloads

These recommendations are general guidance. They do not constitute a security
audit or guarantee compliance with any regulation or standard.

## Security Features

KioskOps SDK includes enterprise security features:

### Data Protection
- AES-256-GCM encryption at rest (Android Keystore backed)
- PII detection and redaction via PiiPolicy
- Payload size limits and queue pressure controls

### Transport Security (v0.2.0)
- Certificate pinning with SHA-256 pins
- mTLS client certificate support
- Certificate Transparency validation

### Key Management (v0.2.0)
- Key rotation with versioned encryption
- Hardware-backed key attestation (TEE/StrongBox)
- Configurable key derivation (OWASP 2023)

### Audit & Compliance (v0.2.0)
- Tamper-evident audit chain (SHA-256)
- Persistent audit across app restarts (Room-backed)
- Signed audit entries with device attestation
- Audit chain integrity verification API
- Optional HMAC request signing

### Data Quality & Validation (v0.5.0)
- Event validation with JSON Schema (Draft 2020-12 subset)
- PII detection via regex patterns (EMAIL, PHONE, SSN, CREDIT_CARD, etc.)
- PII redaction with configurable REJECT/REDACT/FLAG actions
- Field-level encryption for sensitive JSON attributes
- Statistical anomaly detection (payload size, event rate, schema deviation)
- GDPR data export (Art. 20) and deletion (Art. 17) APIs
- Data classification tagging (PUBLIC/INTERNAL/CONFIDENTIAL/RESTRICTED)

### Error Observability & Supply Chain (v0.7.0)
- Non-fatal error callbacks via `KioskOpsErrorListener` for operational visibility
- FIPS 140-2/3 runtime detection via `FipsComplianceChecker` (Conscrypt/BoringSSL)
- CycloneDX SBOM generation in CI for EO 14028 / FedRAMP supply chain requirements
- Debug log level toggle restricted to debug builds (ISO 27001 A.14.2)

### Database Encryption & Compliance Presets (v0.8.0)
- SQLCipher database-at-rest encryption via `DatabaseEncryptionPolicy` (Keystore-backed key)
- Database corruption recovery with `DatabaseCorruptionHandler` and error listener notification
- `cuiDefaults()` preset for NIST SP 800-171 / CUI deployments (all encryption, signed audit, PII rejection)
- `cjisDefaults()` preset for CJIS Security Policy / law enforcement deployments
- `asdEssentialEightDefaults()` preset for Australian government (ASD Essential Eight)

### Lifecycle, PII, Anomaly & Build Integrity (v0.9.0)
- Global PII detection patterns for 8 countries (AU TFN, UK NIN, CA SIN, DE Steuer-ID, JP My Number, IN Aadhaar, BR CPF, ZA ID)
- Anomaly baseline seeding via `seedBaseline()` eliminates cold-start blind spot where initial events bypassed anomaly flags
- `ProcessLifecycleOwner` heartbeat on app background transition ensures telemetry delivery even on unexpected backgrounding
- Build attestation with artifact SHA-256 hash in CI job summary for supply chain verification
- Detekt SARIF findings uploaded to GitHub Security tab for proactive static analysis review

### Data Rights Authorization (v1.0.0)
- `DataRightsAuthorizer` callback prevents unauthorized data export, deletion, and wipe on shared kiosk devices
- CUI and CJIS presets require authorization by default
- All authorization decisions audit-logged for compliance visibility
- API surface frozen under semantic versioning

### Disclaimer

References to regulatory frameworks (NIST 800-53, FedRAMP, GDPR, ISO 27001,
BSI) throughout the SDK documentation and source code are engineering references
only. They do not imply certification, endorsement, or compliance. The SDK has
not been independently audited or certified against any standard. Organizations
must conduct their own assessments to determine regulatory applicability.
