# Security & Compliance Notes

> **Disclaimer:** This document references regulatory frameworks (NIST 800-53,
> FedRAMP, GDPR, ISO 27001, BSI, HIPAA) as engineering context only. Nothing in
> this document or the SDK constitutes legal, compliance, or security advice. The
> SDK has not been independently audited or certified against any standard. The
> authors and contributors accept no responsibility for regulatory outcomes,
> security incidents, or compliance failures. You must conduct your own legal and
> security assessments before deploying in regulated environments. Consult
> qualified legal and security professionals for your specific situation.

This SDK is designed for **enterprise environments** where you usually care about:
- **data minimization** (collect/store the least possible)
- **defense in depth** (multiple independent controls)
- **regional data residency** constraints (you choose where data travels)

The guiding principle is: **local-first by default**, with **explicit opt-in** for any off-device transfer.

---

## Defaults (maximalist presets)

### Data minimization
- **Queue guardrails**: caps payload size and blocks common accidental PII keys (denylist)
- **Telemetry allow-list**: only keys in `TelemetryPolicy.allowedKeys` are persisted
- **No payloads in telemetry/audit**: the SDK never emits queue payload contents into telemetry or audit records

### Encryption at rest (Android Keystore AES-GCM)
When enabled by `SecurityPolicy`:
- **Queued event payloads**: stored encrypted in the Room DB as BLOBs
- **Telemetry files**: day-sliced JSONL with per-line encryption (append-friendly)
- **Audit trail files**: day-sliced JSONL with per-line encryption (append-friendly)
- **Exported logs**: encrypted on export

Diagnostics exports are ZIPs containing minimal entries; many entries may already be encrypted-at-rest.

### Tamper-evident local audit trail
The audit trail stores a simple hash-chain (`prevHash -> hash`) per recorded event. This:
- detects accidental corruption
- makes casual tampering obvious
- is **not** a substitute for server-side signed audit logs

**v0.2.0**: Audit trail is now Room-backed and persistent across app restarts. Optional device attestation signing provides stronger non-repudiation guarantees.

### Transport security (v0.2.0)
When enabled via `TransportSecurityPolicy`:
- **Certificate pinning**: Validates server certificates against pre-configured SHA-256 pins
- **mTLS**: Presents client certificates for mutual authentication
- **Certificate Transparency**: Validates certificates against CT logs (optional)

### Key management (v0.2.0)
When enabled via `SecurityPolicy`:
- **Key rotation**: `VersionedCryptoProvider` supports multiple key versions for seamless rotation
- **Key attestation**: `KeyAttestationReporter` provides hardware-backed key attestation reporting
- **Configurable derivation**: `KeyDerivationConfig` follows OWASP 2023 recommendations

### Remote configuration security (v0.3.0)
When enabled via `RemoteConfigPolicy`:
- **Version monotonicity**: Prevents rollback attacks by enforcing strictly increasing version numbers (BSI APP.4.4.A5)
- **Minimum version floor**: Blocks rollback below a configured threshold for security updates
- **Signature verification**: Optional ECDSA P-256 signature validation for config bundles (BSI APP.4.4.A3)
- **Cooldown period**: Prevents rapid config cycling attacks
- **Audit logging**: All config transitions are recorded (ISO 27001 A.12.4)

### Remote diagnostics security (v0.3.0)
When enabled via `DiagnosticsSchedulePolicy`:
- **Rate limiting**: Maximum triggers per day prevents abuse (BSI APP.4.4.A7)
- **Cooldown period**: Minimum interval between triggers
- **Deduplication**: Prevents replay of trigger requests
- **Audit logging**: All triggers (accepted/rejected) are recorded

### Extended posture privacy (v0.3.0)
Extended device posture collection follows GDPR data minimization:
- **Battery**: Level, status, health only - no device identifiers
- **Storage**: Aggregate metrics only - no file listings or content
- **Connectivity**: Network type and signal level only - no IP addresses, MAC addresses, SSIDs, or cell tower IDs
- **Device groups**: Opaque string identifiers only - no PII in group names

### Event validation (v0.5.0)
When enabled via `ValidationPolicy`:
- **JSON Schema validation**: Draft 2020-12 subset (type, required, properties, pattern, minLength/maxLength, enum, format, minimum/maximum)
- **Strict/permissive modes**: Strict rejects invalid events; permissive flags only
- **Unknown event types**: Configurable ALLOW/FLAG/REJECT for unregistered schemas
- **Fail-safe**: Validation errors never block the pipeline in permissive mode

### PII detection and redaction (v0.5.0)
When enabled via `PiiPolicy`:
- **Regex-based detection**: ~12 compiled patterns for EMAIL, PHONE, SSN, CREDIT_CARD, IP_ADDRESS, DOB, PASSPORT, NATIONAL_ID
- **Configurable actions**: REJECT (block event), REDACT_VALUE (replace with `[REDACTED:TYPE]`), FLAG_AND_ALLOW (pass through with metadata)
- **Pluggable interface**: Implement `PiiDetector` for custom or ML-based detection
- **Field exclusions**: Skip scanning on known-safe paths
- **Scan before persist**: PII detection runs before encryption to satisfy data minimization requirements

Note: The regex-based detector provides reasonable coverage for common PII patterns but is not exhaustive. It may produce false positives or miss obfuscated PII. For high-assurance environments, implement a custom `PiiDetector` with domain-specific logic.

### Anomaly detection (v0.5.0)
When enabled via `AnomalyPolicy`:
- **Payload size z-score**: Detects abnormally large or small payloads per event type
- **Event rate tracking**: Sliding window counter detects burst patterns
- **Schema deviation scoring**: Flags unexpected or missing fields
- **Field cardinality tracking**: Detects enumeration or credential stuffing patterns
- **Pluggable interface**: Implement `AnomalyDetector` for custom detection logic

Note: Statistical anomaly detection requires a baseline period to be effective. Initial events will not trigger anomaly flags. The detector uses heuristics and may produce false positives.

### Field-level encryption (v0.5.0)
When enabled via `FieldEncryptionPolicy`:
- **Per-field encryption**: Individual JSON fields encrypted into `{"__enc":"...","__alg":"AES-256-GCM","__kid":"v1"}` envelopes
- **Before document encryption**: Field encryption applies before full payload encryption for defense in depth
- **Configurable per event type**: Different fields can be encrypted for different event types

### GDPR data rights (v0.5.0)
- **Data export**: `exportUserData(userId)` exports queue events, audit events, and telemetry for a specific user (Art. 20)
- **Data deletion**: `deleteUserData(userId)` erases user data from queue and audit databases (Art. 17)
- **Full wipe**: `wipeAllSdkData()` removes all SDK data from the device
- **userId tracking**: Optional userId column on queue and audit events for data subject identification

Note: These APIs cover SDK-local data only. Your host application and backend may store additional data outside SDK scope. Implementing a complete GDPR-compliant system requires coordination across your full stack.

### Data classification (v0.5.0)
When enabled via `DataClassificationPolicy`:
- **Automatic tagging**: Events tagged as PUBLIC, INTERNAL, CONFIDENTIAL, or RESTRICTED
- **PII-aware**: Events with detected PII automatically elevated to configured classification level

### Retention controls
Retention is configured via `RetentionPolicy` (by days) for:
- telemetry files
- audit files
- exported artifacts (logs/diagnostics)
- queue cleanup (sent/failed lifetimes)
- **minimum audit retention** (v0.5.0): 365 days default, enforced independently of `retainAuditDays`

---

## What the SDK intentionally does NOT do
- It does **not** auto-upload telemetry or diagnostics.
- It does **not** infer or enforce your legal basis (GDPR, HIPAA, etc.). That is a product/legal decision.
- It does **not** attempt to be a full kiosk controller or MDM.

---

## Regional regulations and data residency
To stay sane across jurisdictions:
- Treat the SDK as **local-first**.
- If you need to move data off-device, do it through your **host app + backend** where you can:
  - route by region (EU/US/AU)
  - apply DPA / contractual controls
  - implement consent / policy logic
  - implement authenticated endpoints and key rotation

The SDK provides **hooks** (diagnostics upload interface, transport auth hook) rather than making data-flow decisions for you.

---

## Device identifiers
- The SDK maintains an **SDK-scoped pseudonymous deviceId** (stable per install).
- The deviceId can be **reset** via `dataRights.resetSdkDeviceId()`.
- Telemetry **does not include** device identifiers by default (`TelemetryPolicy.includeDeviceId=false`).

If you enable **network sync**, the SDK will include `deviceId` in batch requests (because idempotent ingest needs a stable per-device correlation key).

---

## Data transfer / network sync

By default, the SDK is **local-only**.

If you enable network sync (`SyncPolicy(enabled = true)`), the host app is responsible for:
- choosing the lawful basis / consent strategy
- data residency routing (regional endpoints)
- retention and access controls server-side
- any additional PII minimization or schema redaction

The SDK keeps sync **explicit** and **opt-in** to reduce compliance surprises.

### Optional request signing
For additional defense-in-depth, the host app may provide a `RequestSigner` (e.g. `HmacRequestSigner`) during initialization.
If configured, the SDK adds a timestamp + nonce + HMAC signature headers to the batch ingest request. This helps detect tampering and reduces replay risk.
Server-side verification and key rotation are owned by your backend.

---

## Known limitations
- Room migrations are provided for schema transitions (Queue v2->v3->v4, Audit v1->v2). Versions < v2 (queue) were pre-release snapshots.
- ~~Audit chain is **process-local**; after app restart, the chain restarts from a new GENESIS point.~~ **Fixed in v0.2.0**: Audit chain is now Room-backed and persistent.
- ~~Sync transport is intentionally minimal (batch ingest + acks). Add mTLS patterns / certificate pinning and server-side audit hardening as needed.~~ **Fixed in v0.2.0**: Certificate pinning and mTLS are now supported.
- Key attestation requires Android API 24+ for full functionality.
- StrongBox security level requires hardware support (not all devices).
- Remote diagnostics trigger requires ACCESS_NETWORK_STATE permission for connectivity status (normal permission, auto-granted).
- Cellular network type detection requires READ_PHONE_STATE permission; gracefully returns UNKNOWN if not granted.
- PII regex detection (v0.5.0) covers common patterns but is not exhaustive; it may produce false positives or miss obfuscated PII.
- Anomaly detection (v0.5.0) requires a baseline period; initial events will not trigger flags.
- NIST control annotations (@NistControl) are engineering references, not certification claims.
