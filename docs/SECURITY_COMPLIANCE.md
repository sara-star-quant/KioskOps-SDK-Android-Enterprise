# Security & Compliance Notes

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

### Retention controls
Retention is configured via `RetentionPolicy` (by days) for:
- telemetry files
- audit files
- exported artifacts (logs/diagnostics)
- queue cleanup (sent/failed lifetimes)

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
- Room migrations are provided for schema v2 -> v3. Versions < v2 were pre-release snapshots.
- Audit chain is **process-local**; after app restart, the chain restarts from a new GENESIS point.
- Sync transport is intentionally minimal (batch ingest + acks). Add mTLS patterns / certificate pinning and server-side audit hardening as needed.
