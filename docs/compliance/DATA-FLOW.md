# Data Flow Reference -- KioskOps SDK

> **Disclaimer:** This document is an engineering reference describing the
> SDK's data storage and transmission behavior. It does not constitute a
> compliance certification, legal advice, or security assessment. You must
> verify these statements against the SDK source code and conduct your own
> assessment for your specific deployment context.

## Overview

The KioskOps SDK follows a **local-first** architecture. All data is stored
on-device by default. No data leaves the device unless network sync is
explicitly enabled by the host application.

---

## Data stored locally

The SDK maintains four categories of local data:

### 1. Event queue (Room database)

| Attribute | Details |
|-----------|---------|
| Contents | Operational events enqueued by the host application (JSON payloads) |
| Storage format | Room database with BLOB columns |
| Encryption at rest | AES-256-GCM via Android Keystore (when SecurityPolicy encryption is enabled) |
| PII handling | PII detection runs before persist; REJECT blocks the event, REDACT replaces values with `[REDACTED:TYPE]`, FLAG passes through with metadata |
| Field encryption | Sensitive JSON fields individually encrypted into `{"__enc":"...","__alg":"AES-256-GCM","__kid":"v1"}` envelopes before document-level encryption |
| Retention | Configurable; sent events and failed events have separate lifetimes |
| Size limits | 64 KB default payload size; 10K events / 50 MB queue pressure cap |
| userId column | Optional; enables per-user export and deletion |

### 2. Telemetry (JSONL files)

| Attribute | Details |
|-----------|---------|
| Contents | SDK operational metrics (enqueue counts, sync timing, validation stats); day-sliced files |
| Storage format | JSONL (one JSON object per line) |
| Encryption at rest | Per-line AES-256-GCM encryption (append-friendly) |
| PII handling | Telemetry never contains queue payload contents; only allowed keys via TelemetryPolicy.allowedKeys are persisted |
| Device identifiers | Not included by default (TelemetryPolicy.includeDeviceId = false) |
| Retention | Configurable in days via RetentionPolicy |

### 3. Audit trail (Room database)

| Attribute | Details |
|-----------|---------|
| Contents | SDK operation records: event enqueue, sync, config changes, data deletion, errors |
| Storage format | Room database with hash-chain linkage |
| Integrity | SHA-256 hash chain (prevHash -> hash); tamper-evident; persistent across app restarts |
| Signing | Optional device attestation signatures for non-repudiation |
| Encryption at rest | AES-256-GCM via Android Keystore |
| Retention | 365-day minimum enforced; configurable above the minimum |
| Verification | `verifyAuditChain()` API detects chain breaks or modifications |
| userId column | Optional; enables per-user export and deletion |

### 4. Diagnostics exports (ZIP files)

| Attribute | Details |
|-----------|---------|
| Contents | Health snapshot, recent logs, telemetry summary, audit trail excerpt |
| Storage format | ZIP archive |
| Encryption | Exported logs encrypted; many ZIP entries already encrypted at rest |
| Retention | Configurable in days via RetentionPolicy |
| Upload | Never automatic; host app controls when and where to upload |

---

## Data encrypted at rest

When `SecurityPolicy.encryptionEnabled = true` (default: on):

| Data store | Encryption | Key storage |
|-----------|-----------|-------------|
| Queue payloads | AES-256-GCM | Android Keystore (hardware-backed when available) |
| Telemetry JSONL lines | AES-256-GCM | Android Keystore |
| Audit trail entries | AES-256-GCM | Android Keystore |
| Exported logs | AES-256-GCM | Android Keystore |
| Field-level encrypted attributes | AES-256-GCM | Android Keystore; versioned keys via VersionedCryptoProvider |

Key rotation is supported via `VersionedCryptoProvider` with backward-compatible
decryption of older key versions.

---

## Data that leaves the device (opt-in only)

Network sync is **disabled by default** (`SyncPolicy(enabled = false)`). When
the host application explicitly enables sync:

| Data transmitted | Conditions | Transport security |
|-----------------|-----------|-------------------|
| Queue event payloads (batch) | SyncPolicy enabled; events in PENDING state | TLS required; certificate pinning (optional); mTLS (optional); Certificate Transparency (optional) |
| SDK deviceId | Included in batch requests for idempotent ingest correlation | Same transport security as event payloads |
| HMAC signature headers | When RequestSigner is configured | Timestamp + nonce + HMAC-SHA256; prevents tampering and replay |
| Diagnostics ZIP | Only when host app explicitly calls upload | Host app controls transport; SDK provides encrypted ZIP |

The SDK never initiates network connections on its own. All outbound data
transfer is triggered by the host application.

---

## Data encrypted in transit

When network sync is enabled, the following transport security layers are available:

| Layer | Configuration | Details |
|-------|--------------|---------|
| TLS | Required for sync | Standard Android TLS stack |
| Certificate pinning | TransportSecurityPolicy | SHA-256 pins with wildcard support; connection rejected if pin validation fails |
| mTLS | TransportSecurityPolicy | Client certificate presented for mutual authentication |
| Certificate Transparency | TransportSecurityPolicy | Validates server certificates against CT logs |
| HMAC request signing | RequestSigner (e.g., HmacRequestSigner) | Timestamp + nonce + HMAC-SHA256 signature headers on batch requests |
| FIPS 140-2/3 | Runtime detection | SDK detects FIPS-mode Conscrypt/BoringSSL; host must supply FIPS provider |

---

## Data the SDK never collects

The SDK is designed for data minimization. The following data is explicitly
excluded from collection:

| Category | What is NOT collected | Notes |
|----------|----------------------|-------|
| Analytics | No usage analytics, screen tracking, or behavioral profiling | SDK is an operational event pipeline, not an analytics product |
| Device identifiers | No Android ID, IMEI, serial number, or advertising ID | SDK uses its own pseudonymous deviceId (resettable) |
| Network identifiers | No IP addresses, MAC addresses, Wi-Fi SSIDs, BSSIDs, or cell tower IDs | Connectivity posture reports network type and signal level only |
| Location | No GPS, cell-based, or Wi-Fi-based location | Geofence evaluation uses host-app-provided coordinates only |
| User credentials | No passwords, tokens, or authentication secrets stored by SDK | HMAC keys and mTLS certificates are managed by the host app |
| Payload contents in telemetry | No queue payload data appears in telemetry or audit records | Telemetry tracks counts and timing only |
| Contact or calendar data | No access to contacts, calendar, SMS, or call logs | SDK requests no permissions for personal data stores |
| Camera or microphone | No media capture | SDK requests no media permissions |
| Clipboard contents | No clipboard access | SDK does not read clipboard |
| Installed applications | No app enumeration | SDK does not scan installed packages |

### Permissions requested

| Permission | When | Purpose |
|-----------|------|---------|
| ACCESS_NETWORK_STATE | Remote diagnostics trigger | Connectivity status for posture reporting (normal permission; auto-granted) |
| READ_PHONE_STATE | Optional | Cellular network type detection; gracefully returns UNKNOWN if not granted |
| INTERNET | When sync is enabled | Batch event upload (only when host app enables sync) |

---

## Data lifecycle summary

```
Host App enqueues event
        |
        v
  PII Detection (REJECT / REDACT / FLAG)
        |
        v
  Event Validation (JSON Schema)
        |
        v
  Anomaly Detection (statistical checks)
        |
        v
  Data Classification tagging
        |
        v
  Field-level encryption (sensitive attributes)
        |
        v
  Document-level encryption (AES-256-GCM)
        |
        v
  Room DB storage (encrypted BLOB)
        |
        v
  Audit trail entry (hash-chained)
        |
        +---> [Local only; no network unless sync enabled]
        |
        v (if sync enabled by host app)
  Batch assembly --> TLS + pinning + mTLS + HMAC --> Backend
```

---

## Data deletion capabilities

| API | Scope | Effect |
|-----|-------|--------|
| `deleteUserData(userId)` | Per-user | Removes matching queue events and audit entries for the specified userId |
| `wipeAllSdkData()` | Full SDK | Removes all queue data, telemetry, audit trail, and diagnostics exports |
| `resetSdkDeviceId()` | Device identity | Generates a new pseudonymous deviceId; breaks correlation with previous identity |
| RetentionPolicy auto-purge | Time-based | Automatically deletes expired telemetry, audit, and queue data per configured retention periods |
