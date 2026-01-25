# Architecture

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
- **PII filtering**: Automatic denylist for common PII keys

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
