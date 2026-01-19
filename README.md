# KioskOps SDK

KioskOps is an **enterprise-oriented Android SDK** for **offline-first operational events**, **local diagnostics**, and **fleet-friendly observability**. It is designed to work on any Android device and integrate cleanly with **Samsung Knox / Android Enterprise** managed deployments.

This repo prioritizes:

1) **Security/compliance maximalist** defaults (local-first, data minimization, encryption-at-rest)
2) **Fleet operations** hooks (posture snapshot, policy drift detection, diagnostics export/upload hook)
3) **Fastest enterprise pilot** via **opt-in network sync** (batch ingest + per-event acks + backoff)

## What’s in this SDK

### Security/compliance maximalist posture
- **Queue guardrails**: payload size cap + denylisted keys (blocks common accidental PII keys unless explicitly allowed)
- **Encryption at rest** (Android Keystore AES-GCM):
  - queued event payloads (DB BLOB)
  - telemetry and audit files (encrypted-at-rest when enabled)
  - optional encryption for exported logs
- **Local-first observability**:
  - allow-listed telemetry keys only
  - tamper-evident audit trail (hash-chain; detects casual tampering)
- **Retention controls** by days (telemetry/audit/exports, plus queue cleanup)

### Fleet operations hooks
- **Policy drift detection**: stores a sanitized config hash (excludes secrets like PIN) and records drift events
- **Device posture snapshot**: minimal posture fields (device owner indicator, lock-task mode best-effort, OS/model/manufacturer/security patch)
- **Diagnostics**:
  - export a ZIP bundle locally
  - optional **host-controlled** upload via `DiagnosticsUploader` (SDK never auto-uploads)

### Opt-in network sync (enterprise pilot)
- **Disabled by default** to avoid silent off-device transfer
- When enabled, the SDK:
  - sends batches to `${baseUrl}/${syncPolicy.endpointPath}`
  - expects per-event acknowledgements (`accepted`, `retryable`)
  - applies exponential backoff for transient failures
  - stops retrying events when server responds `retryable=false`

Server contract is defined in `docs/openapi.yaml`.

## Modules
- `:kiosk-ops-sdk` - the SDK (Android library)
- `:sample-app` - minimal host app (initialization + enqueue)

## Quick start

1) Open the project in Android Studio.
2) Run `:sample-app` on an emulator or device.
3) Check that events are accepted locally (queue) and that diagnostics can be exported.

## Integration docs
- `docs/INTEGRATION_STEP_BY_STEP.md` - practical integration guide
- `docs/SECURITY_COMPLIANCE.md` - threat model stance, defaults, limitations
- `docs/SDK_TARGET.md` - intended use cases / non-goals
- `docs/openapi.yaml` - server API contract for batch ingest

## Build requirements
- **JDK 17+**
- Android Studio (recommended)

## Known limitations 
- Room uses **destructive migration** (MVP). Before production rollout, add schema migrations.
- Audit chain is **process-local**: after app restart, the chain restarts from a new GENESIS point.
- Kiosk/LockTask enforcement is **not** implemented as a full kiosk controller in this snapshot; posture reporting is best-effort.
- Sync transport is intentionally minimal; for hardened deployments add request signing, mTLS patterns, and server-side signed audit logs.

## Roadmap
- Replace destructive migrations with real migrations
- Add optional request signing (HMAC) and mTLS guidance
- Add schema-based event typing (replace denylist heuristics)
- Add per-region endpoint routing helpers (EU/US/AU)
