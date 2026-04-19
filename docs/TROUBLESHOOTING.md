# KioskOps SDK Troubleshooting

Operator-facing runbook for diagnosing common production issues. Each section follows the
same shape: **Symptom** (what you see), **Diagnosis** (how to confirm), **Fix** (what to
do). Expect to use logcat, `KioskOpsSdk.exportDiagnostics()`, and `getAuditStatistics()`.

> **Scope.** This document targets SDK integrators, not end-device users. Some procedures
> require access to device logcat, the host app's managed-config surface, or your
> organization's MDM; none of them require SDK source.

## Quick reference

| Symptom | Most likely cause | Section |
|---|---|---|
| Events in `SENDING` don't ever become `SENT` | Worker timeout, auth failure, or crash mid-flight | [Events stuck in SENDING](#events-stuck-in-sending) |
| `certificate_pin_failure` audit entries | Corporate TLS inspection proxy, pin rotation, or rogue cert | [Pin validation failing](#pin-validation-failing) |
| Sync returns `Unauthorized` | Missing/stale `AuthProvider` token or `requireDataRightsAuthorization` default off | [Unauthorized from authorizer](#unauthorized-from-authorizer) |
| `database_corruption_detected` audit + `KioskOpsError.StorageError` | Disk corruption; SDK recovered by recreating the DB | [Database corruption alert](#database-corruption-alert) |
| Every sync returns `PermanentFailure` | Misconfigured `baseUrl`, invalid schema, or revoked auth | [Sync permanent failure](#sync-permanent-failure) |
| `DuplicateIdempotency` rejections for events you didn't expect | `stableEventId` collision or aggressive retry | [Idempotency collision](#idempotency-collision) |
| `queueDepth()` climbs forever | Sync disabled, worker starved, or transient outage | [Backlog growing](#backlog-growing) |
| Diagnostics export is missing entries | Hit `maxExportBytes`; truncation marker present | [Export truncated](#export-truncated) |
| High-security preset logs an ERROR about missing pins | You used `fedRampDefaults` / `cuiDefaults` / `cjisDefaults` / `asdEssentialEightDefaults` with HTTPS but no pins/CT | [Preset transport warning](#preset-transport-warning) |

---

## Events stuck in SENDING

**Symptom.** `queueDepth()` stays at N forever; `syncOnce()` returns `Success` with
`sent=0`; diagnostics exported `queue/quarantined_summaries.json` shows events that never
left `SENDING`.

**Diagnosis.**

1. Export diagnostics and inspect `queue/quarantined_summaries.json`. Events with
   `state=SENDING` and an `updatedAtEpochMs` older than a few minutes are stranded.
2. Filter logcat for `Queue` tag: `adb logcat -s KioskOps` and look for
   `Reset N stale SENDING rows to PENDING on init`.

**Fix.**

- **Since 1.2.0** the SDK automatically resets `SENDING` rows older than 5 minutes back
  to `PENDING` on `KioskOpsSdk.init()`. If you see stranded rows on a running instance,
  restart the host app to trigger the reconciliation pass.
- If the stranded rows are younger than 5 minutes, the worker is still in flight; wait.
- If a crash-loop caused the issue, check `getAuditStatistics()` for repeated
  `sdk_initialized` entries; the crash root cause is a separate problem.

---

## Pin validation failing

**Symptom.** `SSLPeerUnverifiedException` in logcat during sync; audit trail shows
`certificate_pin_failure` entries with a hostname; `KioskOpsErrorListener.onError` called
with `TransportError`.

**Diagnosis.**

1. Inspect the audit entry: the `hostname` field tells you which host failed.
2. Run `openssl s_client -connect host:443 -showcerts < /dev/null | openssl x509 -noout
   -fingerprint -sha256` from the network perspective of the affected device (or a jump
   host on the same network). Compare the SHA-256 hash against
   `TransportSecurityPolicy.certificatePins[].sha256Pins`.
3. If the observed fingerprint differs from your pin list, the chain is either being
   intercepted (corp TLS proxy) or your pinned certificate has rotated.

**Fix.**

- **Corporate TLS inspection.** If the deployment requires passing through a TLS
  inspection proxy (Zscaler, Netskope, Palo Alto GlobalProtect), add the proxy's
  interception CA fingerprint to `certificatePins[].sha256Pins`, or exclude the host
  from inspection on the proxy side. Never disable pinning to accommodate an inspection
  proxy; that defeats the control.
- **Rotated certificate.** Push a config bundle with the new `sha256Pins` via
  `RemoteConfigManager`. Plan rotations with an overlap window where both old and new
  pins are accepted.
- **Rogue certificate (actual attack).** Do not bypass. Escalate. Pin validation
  working is the SDK telling you something is wrong at the network layer.

Since 1.2.0 pin validation runs **during the TLS handshake** via the native
`CertificatePinner`, so a pin failure rejects the connection before the request body is
transmitted. Pre-1.2 the check ran post-response and could leak request bodies.

---

## Unauthorized from authorizer

**Symptom.** `syncOnce()` returns `PermanentFailure` with HTTP 401 or 403; OR
`dataRights.exportUserData()` / `deleteUserData()` returns `Unauthorized`.

**Diagnosis.**

- For sync: check logcat for `OkHttpTransport` entries showing the status code. Confirm
  the `AuthProvider` is providing a current token.
- For data-rights: confirm `requireDataRightsAuthorization` is intentionally `true`
  (it is in `cuiDefaults`, `cjisDefaults`) and that a `DataRightsAuthorizer` has been
  registered via `setDataRightsAuthorizer(...)`.

**Fix.**

- Auth: rotate the token in the host app, then call `syncOnce()` manually or wait for
  the next `KioskOpsEventSyncWorker` cycle. The SDK treats 401/403 as transient so the
  backlog won't be quarantined.
- Data-rights: register an authorizer:

  ```kotlin
  KioskOpsSdk.get().setDataRightsAuthorizer { request ->
      if (myAppIsAuthenticated()) AuthorizationDecision.Authorized
      else AuthorizationDecision.Unauthorized("user_not_signed_in")
  }
  ```

---

## Database corruption alert

**Symptom.** `KioskOpsErrorListener.onError` fires with `KioskOpsError.StorageError`;
audit trail has `database_corruption_detected` entries; queue empties unexpectedly.

**Diagnosis.**

- The SDK has already recovered: Room recreated the database file.
- Host app should surface this as a telemetry incident so you can correlate with device
  firmware or storage issues.
- `adb bugreport` can help confirm if the underlying storage is failing.

**Fix.**

- The immediate recovery is automatic. No manual action needed on the SDK.
- If the same device sees repeated corruption, replace the device; the flash is likely
  failing.
- If a fleet-wide wave of corruptions appears, suspect an OS or firmware rollout; roll
  back if possible and contact the device vendor.

---

## Sync permanent failure

**Symptom.** `syncOnce()` returns `PermanentFailure` every attempt; no events land;
HTTP status is typically 4xx other than 401/403.

**Diagnosis.**

1. `getAuditStatistics()` and look for `sync_batch_permanent_failure` entries in
   recent history.
2. Inspect the `httpStatus` and `reason` fields in those entries.
3. Compare the request body against your server's expected schema.

**Fix.**

- **Misconfigured `baseUrl`.** Fix the host app's `KioskOpsConfig.baseUrl` or push a
  corrected value via `RemoteConfigManager`.
- **Schema mismatch.** Update the server or the host app's event schema. Since 1.1.0
  the SDK validates against registered schemas before enqueue if
  `ValidationPolicy.enabled == true`; enabling that pushes the failure earlier.
- **Retry behavior.** The batch stays in `FAILED` state with a backoff; no attempts
  counter is bumped (the batch, not the event, is at fault). Fix the config and the
  queue drains on the next cycle.

---

## Idempotency collision

**Symptom.** `enqueue()` returns `Rejected.DuplicateIdempotency` for an event you didn't
knowingly submit twice.

**Diagnosis.**

Two causes:

1. **Deterministic collision.** If
   `IdempotencyConfig.deterministicEnabled == true` and two call sites pass the same
   `stableEventId` within the same time bucket (`bucketMs`, default 60s), they produce
   the same idempotency key. That's the design.
2. **Server-side dedup from a prior attempt.** A previous `syncOnce()` may have
   delivered the event, then the network dropped before the ack; the server deduped the
   second delivery.

**Fix.**

- Case 1: make the `stableEventId` more specific. Include timestamp, user id, or an
  operation UUID. The point of deterministic idempotency is that it should be
  collision-free under your business contract.
- Case 2: not actually a bug; the event landed.

---

## Backlog growing

**Symptom.** `queueDepth()` climbs steadily; events aren't syncing.

**Diagnosis.**

1. Confirm `SyncPolicy.enabled == true`; the SDK ships with sync OFF by default.
2. Confirm the device has network: `healthStatusFlow().first().connectivity`.
3. Inspect audit entries for recent `sync_batch_*` events; if none exist, the sync
   worker is not running.
4. Check WorkManager state in `adb shell dumpsys jobscheduler | grep KioskOps`.

**Fix.**

- Sync disabled: set `syncPolicy = SyncPolicy.enabledDefaults()` in config and call
  `applySchedulingFromConfig()`.
- Worker starved: Android may be deferring the worker under Doze. Consider
  `SyncPolicy.requireUnmeteredNetwork = false` if Wi-Fi is scarce.
- Transient outage: retry drains the backlog once connectivity returns. A 30-minute
  outage does **not** quarantine events since 1.2.0 (batch-transient failures no longer
  bump per-event attempts).

---

## Export truncated

**Symptom.** `exportDiagnostics()` ZIP contains a `truncation.txt` entry listing omitted
paths; the ZIP is smaller than expected.

**Diagnosis.**

The device's telemetry/audit/log archive exceeded
`DiagnosticsSchedulePolicy.maxExportBytes` (default 50 MiB). The exporter skipped later
entries and wrote the marker so the truncation is observable.

**Fix.**

- If you need the full export, raise `maxExportBytes`:
  ```kotlin
  diagnosticsSchedulePolicy = DiagnosticsSchedulePolicy.enterpriseDefaults().copy(
      maxExportBytes = 200L * 1024L * 1024L,
  )
  ```
- If the backlog is pathological (deep quarantine queue, months of telemetry), run
  retention (`applyRetentionNow()`) before exporting to shrink the source data.
- Setting `maxExportBytes = 0L` disables the cap entirely; not recommended.

---

## Preset transport warning

**Symptom.** Logcat shows an ERROR-level log tagged `KioskOpsConfig`:

> `fedRampDefaults used with HTTPS baseUrl but no certificate pins or Certificate
> Transparency are configured. ...`

**Diagnosis.**

Since 1.2.0 the high-security presets (`fedRampDefaults`, `cuiDefaults`, `cjisDefaults`,
`asdEssentialEightDefaults`) check that an HTTPS `baseUrl` has either pins or CT
configured. Default `TransportSecurityPolicy` has neither; the preset logs because
relying on the device CA trust store alone is inappropriate for the compliance regime
the preset is named after.

**Fix.**

Configure transport security on the returned config:

```kotlin
val cfg = KioskOpsConfig.fedRampDefaults(
    baseUrl = "https://api.example.com",
    locationId = "STORE-001",
).copy(
    transportSecurityPolicy = TransportSecurityPolicy(
        certificatePins = listOf(
            CertificatePin(
                hostname = "api.example.com",
                sha256Pins = listOf("sha256/AAAA...", "sha256/BBBB..."),
            ),
        ),
        certificateTransparencyEnabled = true,
    ),
)
```

The warning is advisory; the preset still returns a config. For staging or test
environments against intentionally-unpinned endpoints, you can ignore it. For
production, don't.
