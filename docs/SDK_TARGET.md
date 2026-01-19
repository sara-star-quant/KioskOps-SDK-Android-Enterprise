# SDK Target

KioskOps is intended for **enterprise frontline / kiosk-style deployments** where you care about:
- **reliability under bad conditions** (offline, flaky networks, shared devices)
- **operational visibility** ("what happened on this device?")
- **security/compliance posture** (data minimization, encryption-at-rest, explicit data transfer)

## Target buyers / use cases
- Retail and logistics frontline workflows (shared devices, shift work)
- Warehouse scanning / pick-pack-ship pipelines
- Field service checklists and incident capture
- Manufacturing quality checks / audits (minimal, auditable records)

## What this SDK is good at
- **Offline-first event capture** (queue + backoff)
- **Local diagnostics** (encrypted-at-rest telemetry + audit + export bundles)
- **Fleet operability hooks** (policy drift, posture snapshot, host-controlled upload)
- **Fast pilots** with opt-in idempotent batch ingest (server contract in `docs/openapi.yaml`)

## Supported
- Android 10+ recommended (this snapshot uses `minSdk 26`)
- Works on any OEM Android device
- Integrates with **Android Enterprise / Samsung Knox** managed configurations (app restrictions)

## Not a replacement for
- An MDM/EMM (Intune, Workspace ONE, Knox Manage, etc.)
- A full IdP/IAM solution (Okta, Azure AD, etc.)
- A SIEM/SOC platform

## Non-goals (deliberate)
- The SDK does **not** auto-upload telemetry/diagnostics.
- The SDK does **not** decide your lawful basis, consent, or data-residency routing.
  Those decisions live in the host app + backend, where your legal/compliance team can control them.
