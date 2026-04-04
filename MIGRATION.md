# Migration Guide

## v0.9.x to v1.0.0

v1.0.0 is the first stable release. The API surface is frozen; breaking changes will only occur in future major versions.

### 1. DataRightsAuthorizer required for CUI and CJIS presets

CUI and CJIS configuration presets now set `requireDataRightsAuthorization = true` by default.
Without a registered `DataRightsAuthorizer`, all data rights operations (`exportUserData`,
`deleteUserData`, `wipeAllSdkData`) return `Unauthorized` instead of proceeding.

**Before (v0.9.x):**
```kotlin
val sdk = KioskOpsSdk.get()
// Data rights operations proceeded without authorization
val result = sdk.dataRights.deleteUserData("user-123")
// result was DataDeletionResult.Success(...)
```

**After (v1.0.0 with CUI or CJIS preset):**
```kotlin
val sdk = KioskOpsSdk.get()

// You must register an authorizer before data rights operations
sdk.setDataRightsAuthorizer(DataRightsAuthorizer { operation, userId ->
  // Verify the caller is authorized (e.g., check session, role, biometric)
  currentSession.userId == userId || currentSession.isAdmin
})

// Now data rights operations proceed when the authorizer returns true
val result = sdk.dataRights.deleteUserData("user-123")
// result is DataDeletionResult.Success(...) if authorized
// result is DataDeletionResult.Unauthorized(DELETE) if denied
```

**If you use GDPR or FedRAMP presets:** No action is needed. These presets do not require
authorization by default. You can opt in by setting `requireDataRightsAuthorization = true`
in your config.

**If you use a custom config:** The default value of `requireDataRightsAuthorization` is `false`,
so existing behavior is preserved unless you explicitly enable it.

### 2. API surface freeze

The 1.0.0 API surface is stable. Public API signatures will not change until the next major
version. This applies to all public classes, interfaces, functions, and properties exported
in the `kiosk-ops-sdk.api` file.

### 3. No other breaking changes from v0.9.0

All other APIs remain source- and binary-compatible with v0.9.0. No database migrations are
required. Existing configurations, audit databases, and queue databases carry forward without
modification.

---

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
