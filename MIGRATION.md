# Migration Guide

## v1.0.x to v1.1.0

v1.1.0 is a security-hardening minor release. It contains breaking source and
runtime changes because silent fallbacks in crypto code paths were replaced
with explicit errors. Callers that relied on the prior behavior must update.

### 1. `minSdk` raised from 31 to 33

Consumers must target Android 13+. If your app supports Android 12, either
pin to KioskOps SDK 1.0.x or raise your `minSdk`.

```kotlin
// app/build.gradle.kts
android {
  defaultConfig {
    minSdk = 33 // was 31 or lower
  }
}
```

### 2. `DatabaseEncryptionProvider.createFactory` requires `Context`

```kotlin
// Before (1.0.x)
val factory = DatabaseEncryptionProvider.createFactory()

// After (1.1.0)
val factory = DatabaseEncryptionProvider.createFactory(context)
```

**Databases encrypted under 1.0.x cannot be decrypted under 1.1.0.** The prior
implementation wrote a non-random passphrase on hardware-backed Keystore
devices; the 1.1.0 implementation generates a random 256-bit passphrase and
wraps it with the Keystore key. On first launch after upgrade, callers that
had `databaseEncryptionPolicy.enabled = true` must delete `kiosk_ops_queue.db`
and `kioskops_audit.db` and allow them to be recreated. `DataRightsManager.wipeAllSdkData()`
performs this cleanly.

### 3. `FieldLevelEncryptor` throws on failure

```kotlin
// Before (1.0.x): silent fallback to plaintext if the Keystore key was unavailable
val encrypted = encryptor.encryptFields(payload, fields) // could return plaintext

// After (1.1.0): explicit exception; callers must handle the failure
try {
  val encrypted = encryptor.encryptFields(payload, fields)
} catch (e: FieldEncryptionException) {
  // Do NOT forward the original payload; it may contain PII.
}
```

The SDK's event pipeline now rejects events with
`EnqueueResult.Rejected.FieldEncryptionFailed` when field-level encryption
fails. If you pattern-match on `EnqueueResult.Rejected`, add a branch for
this case.

### 4. `MtlsClientBuilder` throws `MtlsConfigurationException`

```kotlin
// Before (1.0.x): silently returned base client on any error
val client = MtlsClientBuilder.build(baseClient, mtlsConfig)

// After (1.1.0): explicit exception on misconfiguration
try {
  val client = MtlsClientBuilder.build(baseClient, mtlsConfig)
} catch (e: MtlsConfigurationException) {
  // Surface to operator; do NOT proceed with an unauthenticated client.
}
```

### 5. `CertificateTransparencyValidator` requires embedded SCTs

The issuer-DN substring check that previously allowed certificates from
"known CAs" without SCTs has been removed. Certificates must now have the
SCT extension (OID 1.3.6.1.4.1.11129.2.4.2) embedded in the leaf, which all
public-trust CAs have emitted since 2018. Private-CA deployments that relied
on the bypass must either (a) configure their CA to embed SCTs, (b) disable
`transportSecurityPolicy.certificateTransparencyEnabled`, or (c) skip CT
enforcement for internal hosts.

### 6. `KioskOpsConfig.toString()` redacts `adminExitPin`

The PIN now appears as `***` in `toString()` output. Tests that asserted the
exact `toString()` form must be updated.

### 7. `wipeAllSdkData` is more thorough

The wipe now removes every `kioskops*` SharedPreferences file and every
`kioskops*` Android Keystore alias. If you wrote integration tests that
checked for specific surviving preferences after a wipe, update them.

---

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
