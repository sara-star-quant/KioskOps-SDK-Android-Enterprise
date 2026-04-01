# Migration Guide

## v0.5.x to v0.6.0

v0.6.0 removes deprecated APIs and adds initialization safety. This guide covers all breaking changes.

### 1. SecurityPolicy: `denylistJsonKeys` and `allowRawPayloadStorage` removed

These fields were deprecated in v0.5.0 in favor of `PiiPolicy`. They are now removed.

**Before (v0.5.x):**
```kotlin
val config = KioskOpsConfig(
  baseUrl = "https://api.example.com",
  locationId = "STORE-001",
  kioskEnabled = true,
  securityPolicy = SecurityPolicy.maximalistDefaults().copy(
    denylistJsonKeys = setOf("email", "phone", "ssn"),
    allowRawPayloadStorage = false,
  ),
)
```

**After (v0.6.0):**
```kotlin
val config = KioskOpsConfig(
  baseUrl = "https://api.example.com",
  locationId = "STORE-001",
  kioskEnabled = true,
  piiPolicy = PiiPolicy(
    enabled = true,
    action = PiiAction.REJECT,
  ),
)
```

**Behavior mapping:**
- `allowRawPayloadStorage = false` + `denylistJsonKeys` -> `PiiPolicy(enabled = true, action = PiiAction.REJECT)` rejects events containing PII
- `allowRawPayloadStorage = true` -> `PiiPolicy(enabled = false)` or `PiiPolicy(enabled = true, action = PiiAction.FLAG_AND_ALLOW)`
- For redaction instead of rejection: `PiiPolicy(enabled = true, action = PiiAction.REDACT_VALUE)`

`PiiPolicy` detects more PII types than the old denylist (EMAIL, PHONE, SSN, CREDIT_CARD, ADDRESS, DOB, IP_ADDRESS, PASSPORT, NATIONAL_ID) and supports redaction in addition to rejection.

### 2. EnqueueResult.Rejected.DenylistedKey removed

This result type no longer exists. PII rejections now return `EnqueueResult.Rejected.PiiDetected`.

**Before (v0.5.x):**
```kotlin
when (result) {
  is EnqueueResult.Rejected.DenylistedKey -> {
    log("Denied due to key: ${result.key}")
  }
  // ...
}
```

**After (v0.6.0):**
```kotlin
when (result) {
  is EnqueueResult.Rejected.PiiDetected -> {
    log("PII detected: ${result.findings}")
  }
  // ...
}
```

### 3. DataRightsManager.exportLocalFiles() removed

Use `exportAllLocalData()` instead. The replacement was available since v0.5.0.

**Before (v0.5.x):**
```kotlin
val files = sdk.dataRights.exportLocalFiles()
```

**After (v0.6.0):**
```kotlin
val result = sdk.dataRights.exportAllLocalData()
```

### 4. PersistentAuditTrail: `crypto` constructor parameter removed

If you were constructing `PersistentAuditTrail` directly (unlikely outside of tests), remove the `crypto` parameter.

**Before (v0.5.x):**
```kotlin
val audit = PersistentAuditTrail(
  context = ctx,
  retentionProvider = { retentionPolicy },
  clock = Clock.SYSTEM,
  crypto = NoopCryptoProvider,
)
```

**After (v0.6.0):**
```kotlin
val audit = PersistentAuditTrail(
  context = ctx,
  retentionProvider = { retentionPolicy },
  clock = Clock.SYSTEM,
)
```

### 5. Initialization is now protected against double-init

`KioskOpsSdk.init()` now throws `KioskOpsAlreadyInitializedException` if called more than once. `KioskOpsSdk.get()` throws `KioskOpsNotInitializedException` instead of generic `IllegalStateException`.

**If you relied on re-initialization (e.g., in tests):**
```kotlin
// v0.5.x allowed this silently:
KioskOpsSdk.init(ctx, { config1 })
KioskOpsSdk.init(ctx, { config2 }) // Overwrote first instance

// v0.6.0 throws KioskOpsAlreadyInitializedException
// For tests, use getOrNull() to check state first
```

### 6. New exception types

Catch SDK-specific exceptions instead of generic ones:

```kotlin
try {
  val sdk = KioskOpsSdk.get()
} catch (e: KioskOpsNotInitializedException) {
  // SDK not yet initialized
}
```

### Database migrations

v0.6.0 does not change any database schemas. Existing Room databases (audit, queue, config) are fully compatible. No data migration is needed.
