# Architecture

## Scope

KioskOps is intended for **enterprise frontline / kiosk-style deployments** where you care about:
- **reliability under bad conditions** (offline, flaky networks, shared devices)
- **operational visibility** ("what happened on this device?")
- **security/compliance posture** (data minimization, encryption-at-rest, explicit data transfer)

### Target buyers / use cases
- Retail and logistics frontline workflows (shared devices, shift work)
- Warehouse scanning / pick-pack-ship pipelines
- Field service checklists and incident capture
- Manufacturing quality checks / audits (minimal, auditable records)

### What this SDK is good at
- **Offline-first event capture** (queue + backoff)
- **Local diagnostics** (encrypted-at-rest telemetry + audit + export bundles)
- **Fleet operability hooks** (policy drift, posture snapshot, host-controlled upload)
- **Fast pilots** with opt-in idempotent batch ingest (server contract in `docs/openapi.yaml`)

### Supported
- Android 13+ required (`minSdk 33`)
- Works on any OEM Android device
- Integrates with **Android Enterprise / Samsung Knox** managed configurations (app restrictions)

### Not a replacement for
- An MDM/EMM (Intune, Workspace ONE, Knox Manage, etc.)
- A full IdP/IAM solution (Okta, Azure AD, etc.)
- A SIEM/SOC platform

### Non-goals (deliberate)
- The SDK does **not** auto-upload telemetry/diagnostics.
- The SDK does **not** decide your lawful basis, consent, or data-residency routing.
  Those decisions live in the host app + backend, where your legal/compliance team can control them.

---

## Overview

KioskOps SDK is structured as a singleton with four main subsystems:

```
+-----------------------------------------------------------------------------------+
|                                   Host Application                                 |
+-----------------------------------------------------------------------------------+
        |                    |                    |                    |
        v                    v                    v                    v
+---------------+    +---------------+    +---------------+    +---------------+
|   enqueue()   |    |  heartbeat()  |    |   syncOnce()  |    |exportDiag()   |
+---------------+    +---------------+    +---------------+    +---------------+
        |                    |                    |                    |
        v                    v                    v                    v
+-----------------------------------------------------------------------------------+
|                              KioskOpsSdk (Singleton)                               |
+-----------------------------------------------------------------------------------+
        |                    |                    |                    |
        v                    v                    v                    v
+---------------+    +---------------+    +---------------+    +---------------+
|     Queue     |    |   Telemetry   |    |  SyncEngine   |    | Diagnostics   |
| (Room + AES)  |    | (Encrypted)   |    |  (OkHttp)     |    |  (ZIP Export) |
+---------------+    +---------------+    +---------------+    +---------------+
        |                    |                    |                    |
        v                    v                    v                    v
+-----------------------------------------------------------------------------------+
|                     Android Keystore (AES-GCM Hardware-Backed)                     |
+-----------------------------------------------------------------------------------+
```

## Subsystems

### Queue

The event queue provides durable, encrypted storage for operational events:

- **Storage**: Room database with AES-256-GCM encryption at rest
- **Pressure control**: Configurable limits (default: 10K events / 50 MB)
- **Overflow strategies**: `DROP_OLDEST`, `DROP_NEWEST`, or `BLOCK`
- **PII filtering**: Configurable PII detection via `PiiPolicy` with REJECT, REDACT, or FLAG actions

### Telemetry

Internal SDK metrics stored separately from application events:

- SDK initialization timing
- Queue operations (enqueue/dequeue rates)
- Sync success/failure rates
- Error counts by category

### SyncEngine

Optional network synchronization with your backend:

- **Disabled by default** - No silent off-device transfer
- **Batch processing** - Configurable batch size
- **Retry logic** - Exponential backoff (10s base, 6h max) with jitter
- **Request signing** - Optional HMAC integrity protection
- **Poison events** - Quarantine for non-retryable failures

### Diagnostics

On-demand export for troubleshooting and support:

- Health snapshot (SDK state, config hash, queue depth)
- Recent logs
- Telemetry summary
- Audit trail excerpt
- Exported as ZIP bundle

## Modules

| Module | Description |
|--------|-------------|
| `:kiosk-ops-sdk` | Core SDK (Android library AAR) |
| `:sample-app` | Reference integration |

## Security Architecture

```
+-----------------------------------------------------------------------------------+
|                               Transport Security (v0.2.0)                          |
+-----------------------------------------------------------------------------------+
|  Certificate Pinning  |    mTLS Client Certs   |  Certificate Transparency       |
+-----------------------------------------------------------------------------------+
                                       |
                                       v
+-----------------------------------------------------------------------------------+
|                                 Cryptography Layer                                 |
+-----------------------------------------------------------------------------------+
|    VersionedCryptoProvider    |   KeyAttestationReporter   |  SecureKeyDerivation |
+-----------------------------------------------------------------------------------+
                                       |
                                       v
+-----------------------------------------------------------------------------------+
|                     Android Keystore (AES-GCM Hardware-Backed)                     |
+-----------------------------------------------------------------------------------+
                                       |
                                       v
+-----------------------------------------------------------------------------------+
|                           Secure Hardware (TEE/StrongBox)                          |
+-----------------------------------------------------------------------------------+
```

### Transport Security (v0.2.0)

| Component | Purpose |
|-----------|---------|
| `CertificatePinningInterceptor` | Validates server certs against SHA-256 pins |
| `MtlsClientBuilder` | Configures client cert for mutual TLS |
| `CertificateTransparencyValidator` | Checks certs against CT logs |

### Key Management (v0.2.0)

| Component | Purpose |
|-----------|---------|
| `VersionedCryptoProvider` | Multi-version encryption for key rotation |
| `KeyMetadataStore` | Tracks key versions and creation times |
| `KeyAttestationReporter` | Reports hardware-backed key status |
| `SecureKeyDerivation` | PBKDF2 with OWASP 2023 defaults |

### Audit Trail (v0.2.0)

| Component | Purpose |
|-----------|---------|
| `PersistentAuditTrail` | Room-backed audit with chain continuity |
| `KeystoreAttestationProvider` | Signs audit entries with device attestation |
| `ChainVerificationResult` | Integrity verification result types |

See [Security & Compliance](SECURITY_COMPLIANCE.md) for the full threat model and security controls.
