# KioskOps SDK - Integration (Step-by-step)

KioskOps is designed for **enterprise kiosk / frontline** Android deployments.
It defaults to a **security/compliance-maximalist posture**:
- minimizes stored data
- blocks common accidental PII keys by default
- encrypts queued event payloads at rest by default
- keeps observability **local-first** (your host app controls any data transfer)

---

## 1) Add the dependency

Today this repo is a source-based integration:

```kotlin
dependencies {
  implementation(project(":kiosk-ops-sdk"))
}
```

When publishing, recommended Maven coordinates:
- groupId: `com.peterz.kioskops`
- artifact: `kioskops-sdk`

---

## 2) Initialize in your Application

```kotlin
class MyApp : Application() {
  override fun onCreate() {
    super.onCreate()

    val cfg = KioskOpsConfig(
      baseUrl = "https://your-api.example/",
      locationId = "STORE-123",
      kioskEnabled = true,
      securityPolicy = SecurityPolicy.maximalistDefaults(),
      retentionPolicy = RetentionPolicy.maximalistDefaults(),
      telemetryPolicy = TelemetryPolicy.maximalistDefaults(),
      syncPolicy = SyncPolicy.disabledDefaults(), // opt-in
    )

    KioskOpsSdk.init(this, configProvider = { cfg })

    // Schedules periodic local heartbeat, and (if enabled) periodic network sync.
    KioskOpsSdk.get().applySchedulingFromConfig()
  }
}
```

Notes:
- `applySchedulingFromConfig()` schedules:
  - a periodic **heartbeat** worker (local-only)
  - a periodic **event sync** worker *only when* `cfg.syncPolicy.enabled == true`
- WorkManager periodic work has a platform minimum of **15 minutes**; keep `syncIntervalMinutes >= 15`.

---

## 2.1) Managed configurations (MDM/EMM)

If your fleet uses managed app restrictions (Android Enterprise / Knox / EMM), you can merge MDM values into your defaults:

```kotlin
val defaults = KioskOpsConfig(
  baseUrl = "https://your-api.example/",
  locationId = "STORE-DEFAULT",
  kioskEnabled = true,
)

val cfg = ManagedConfigReader.read(this, defaults)
KioskOpsSdk.init(this, configProvider = { cfg })
KioskOpsSdk.get().applySchedulingFromConfig()
```

Keys are documented in `ManagedConfigReader.Keys`.

Practical pilot tip:
- Use **region-specific** `baseUrl` values (e.g., EU/US/AU) via managed config.
- Use `TelemetryPolicy.regionTag` as a lightweight “what region is this device configured for?” label.

---



## 3) Enqueue events (offline-first)

```kotlin
val ok = KioskOpsSdk.get().enqueue("SCAN", "{\"scan\":\"12345\"}")
```

Recommended (enterprise pilots): use the detailed API to get rejection reasons and quota behavior:

```kotlin
val res = KioskOpsSdk.get().enqueueDetailed(
  type = "SCAN",
  payloadJson = "{\"scan\":\"12345\"}",
  // Optional: stable business id for deterministic idempotency (HMAC)
  stableEventId = "order_2026_01_19_0001"
)

when (res) {
  is EnqueueResult.Accepted -> {
    // res.droppedOldest > 0 means queue pressure forced eviction of oldest events
  }
  is EnqueueResult.Rejected -> {
    // res contains a typed reason (payload too large, denylisted key, queue full, etc.)
  }
}
```

### Guardrails (MVP)
- If payload size exceeds `SecurityPolicy.maxEventPayloadBytes`, enqueue is rejected.
- If payload contains denylisted JSON keys (e.g., `"email"`, `"phone"`), enqueue is rejected unless `allowRawPayloadStorage = true`.

### Queue pressure controls (Step 7)
- Enforced before writing payloads to disk: `QueueLimits.maxActiveEvents` + `QueueLimits.maxActiveBytes`.
- Overflow behavior is configurable: `DROP_OLDEST` (default), `DROP_NEWEST`, or `BLOCK`.

### Poison event quarantine (Step 7)
- If the server responds `retryable=false`, or if the event hits `SyncPolicy.maxAttemptsPerEvent`, the event is **quarantined**.
- Quarantined events are excluded from future sync attempts and can be inspected via:

```kotlin
val quarantined = KioskOpsSdk.get().quarantinedEvents(limit = 50)
```

### Encryption-at-rest
- If `SecurityPolicy.encryptQueuePayloads == true`, payloads are stored encrypted with **AES-GCM** using Android Keystore.
- Payloads are stored as BLOBs with an explicit `payloadEncoding` so mixed fleets / gradual rollouts are safe.

---

## 4) Heartbeat (observability without data transfer)

You can trigger a manual heartbeat (useful for pilot diagnostics):

```kotlin
MainScope().launch {
  KioskOpsSdk.get().heartbeat(reason = "manual")
}
```

A heartbeat:
- writes a redacted telemetry event to local storage
- appends a tamper-evident audit record
- captures a minimal posture snapshot (device owner indicator, OS/model, best-effort lock-task flag)
- applies retention across queue + telemetry + audit + exported artifacts

---

## 5) Export diagnostics (local, safe-by-default)

```kotlin
MainScope().launch {
  val zip: File = KioskOpsSdk.get().exportDiagnostics()
  // You decide where/how to store or send it.
}
```

The diagnostics ZIP contains:
- `manifest.json` + `health_snapshot.json`
- `queue/quarantined_summaries.json` (no payloads; metadata only)
- exported logs (encrypted if enabled)
- local telemetry files (may be encrypted-at-rest)
- local audit files (may be encrypted-at-rest)

Important:
- The SDK does **not** auto-upload.
- The export is designed to be **region-portable**: telemetry is allow-listed, and no payloads are included.

---

## 6) Data-rights hooks

```kotlin
val newId = KioskOpsSdk.get().dataRights.resetSdkDeviceId()
```

This resets the SDK-scoped pseudonymous `deviceId`.
Telemetry does not include device identifiers by default (`TelemetryPolicy.includeDeviceId=false`).

---

## 7) Fleet operations hooks (optional)

### Policy drift detection (automatic)

The SDK computes a sanitized policy hash (excludes secrets like PIN) and detects changes across runs.
- On heartbeat, it stores the latest hash locally.
- If the hash changes, it emits a local telemetry event `policy_drift_detected` and records an audit entry.

The current policy hash is included in:
- `health_snapshot.json` in diagnostics exports

### Host-controlled diagnostics upload

The SDK never uploads data on its own. If you want an “upload diagnostics” button in your app, provide an uploader:

```kotlin
KioskOpsSdk.get().setDiagnosticsUploader(
  DiagnosticsUploader { file, metadata ->
    // Upload to your API / S3 / ticketing system.
    // Keep regional routing & retention decisions in your host app.
  }
)

MainScope().launch {
  val ok = KioskOpsSdk.get().uploadDiagnosticsNow(metadata = mapOf("ticket" to "INC-123"))
}
```

---

## 8) Opt-in network sync (fastest enterprise pilot)

Network sync is **disabled by default** to prevent silent off-device transfer.
To enable it:

1) Set `SyncPolicy(enabled = true)` in your `KioskOpsConfig`.
2) Provide an `AuthProvider` (recommended) or a custom `OkHttpClient`.
3) Provide a server endpoint compatible with the SDK contract.

### Enable sync in config

```kotlin
val cfg = KioskOpsConfig(
  baseUrl = "https://your-api.example/",
  locationId = "STORE-123",
  kioskEnabled = true,
  syncPolicy = SyncPolicy(
    enabled = true,
    endpointPath = "events/batch", // default
    batchSize = 50,
    requireUnmeteredNetwork = false,
    maxAttemptsPerEvent = 12
  )
)

KioskOpsSdk.init(
  this,
  configProvider = { cfg },
  authProvider = AuthProvider { builder ->
    builder.header("Authorization", "Bearer <token>")
  }
)
KioskOpsSdk.get().applySchedulingFromConfig()
```

### Optional: HMAC request signing

For hardened deployments, you can add an additional request signature over the batch ingest request.
This provides defense-in-depth on top of TLS (tamper detection, replay resistance via timestamp + nonce).

**Important:** signing is **off by default**. The host app must provide the shared secret.

```kotlin
import com.peterz.kioskops.sdk.transport.HmacRequestSigner

val signer = HmacRequestSigner(
  sharedSecret = "<shared-secret-from-your-server>".toByteArray(),
  keyId = "key-2026-01" // optional, for server-side key rotation
)

KioskOpsSdk.init(
  this,
  configProvider = { cfg },
  authProvider = AuthProvider { b -> b.header("Authorization", "Bearer <token>") },
  requestSigner = signer
)
```

Signed headers (v1):
- `X-KioskOps-Signature-Version: 1`
- `X-KioskOps-Timestamp: <epoch-seconds>`
- `X-KioskOps-Nonce: <uuid>`
- `X-KioskOps-Key-Id: ...` (optional)
- `X-KioskOps-Signature: hmac-sha256:<base64url>`

The signature covers: method, path+query, timestamp, nonce, SHA-256(body), and content type.

### Manual sync (for an “Upload now” button)

```kotlin
MainScope().launch {
  val result = KioskOpsSdk.get().syncOnce()
  // result is TransportResult<SyncOnceResult>
}
```

### Server contract

The SDK sends POST `${baseUrl}/${endpointPath}` with JSON:
- `batchId`
- `deviceId` (SDK-scoped, resettable)
- `appVersion`
- `locationId`
- `sentAtEpochMs`
- `events[]`: `{ id, idempotencyKey, type, payloadJson, createdAtEpochMs }`

Server responds with `BatchSendResponse` including `acks[]`:
- `{ id, idempotencyKey, accepted, retryable, error?, serverEventId? }`

See `docs/openapi.yaml`.

### Retry behavior

- Network exceptions, **5xx**, and **429** are treated as **transient** and retried with exponential backoff.
- **401/403** are treated as transient (host usually refreshes auth).
- Other **4xx** are treated as “batch-level permanent”, but the SDK still applies a short backoff so operators can fix config and retry.
- Per-event rejections can be marked `retryable=false` to permanently stop retries for that event.

---

## 9) Transport Security (v0.2.0)

### Certificate Pinning

```kotlin
val cfg = KioskOpsConfig(
  baseUrl = "https://api.example.com/",
  locationId = "STORE-001",
  transportSecurityPolicy = TransportSecurityPolicy(
    certificatePins = listOf(
      CertificatePin(
        hostname = "api.example.com",
        sha256Pins = listOf(
          "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", // Primary
          "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=", // Backup
        )
      )
    )
  )
)
```

### mTLS (Client Certificates)

```kotlin
val cfg = KioskOpsConfig(
  baseUrl = "https://api.example.com/",
  locationId = "STORE-001",
  transportSecurityPolicy = TransportSecurityPolicy(
    mtlsConfig = MtlsConfig(
      clientCertificateProvider = MyKeystoreProvider(context)
    )
  )
)
```

See [Transport Security Guide](TRANSPORT_SECURITY.md) for detailed configuration.

---

## 10) Key Attestation (v0.2.0)

### Check Key Security Level

```kotlin
val posture = KioskOpsSdk.get().devicePosture()
if (posture.keysAreHardwareBacked) {
  // Keys are protected by TEE or StrongBox
}
```

### Enable Signed Audit Entries

```kotlin
val securityPolicy = SecurityPolicy.maximalistDefaults().copy(
  signAuditEntries = true
)
```

See [Key Management Guide](KEY_MANAGEMENT.md) for key rotation and attestation.

---

## 11) Audit Trail Verification (v0.2.0)

### Verify Chain Integrity

```kotlin
when (val result = KioskOpsSdk.get().verifyAuditIntegrity()) {
  is ChainVerificationResult.Valid -> {
    // Chain is valid
  }
  is ChainVerificationResult.Broken -> {
    // Chain has been tampered with or corrupted
  }
}
```

### Export for Compliance

```kotlin
val file = KioskOpsSdk.get().exportSignedAuditRange(startOfMonth, endOfMonth)
// Gzipped JSONL with signatures for external verification
```

See [Audit Integrity Guide](AUDIT_INTEGRITY.md) for verification details.

---

## 12) Testing

The SDK includes JVM tests for:
- payload guardrails (size cap / denylist)
- crypto codec behavior
- retention purge behavior
- sync engine behavior (MockWebServer)

Run tests from Android Studio or via Gradle.
