# Production Hardening Checklist

A walk-through of what to enable when taking KioskOps SDK from a pilot to a fleet in
production. Each item states **why** (not just what), so reviewers evaluating against
FedRAMP / NIST 800-171 / CJIS / ASD Essential Eight / GDPR can justify the choices.

> Complements [SECURE_DESIGN](SECURE_DESIGN.md) (principles) and
> [SECURITY_COMPLIANCE](SECURITY_COMPLIANCE.md) (framework crosswalk). This page is
> operational: what to flip on.

## 1. Pick the right preset

Starting point matters. The SDK ships four high-security preset factories:

| Preset | Use case | Defaults it sets |
|---|---|---|
| `fedRampDefaults` | NIST 800-53 / FedRAMP Moderate aligned deployments | strict validation + PII reject + field encryption + signed audit + 365d retention |
| `cuiDefaults` | NIST 800-171 / CUI / DoD contractors | adds SQLCipher + data-rights authorization required + CONFIDENTIAL default classification |
| `cjisDefaults` | Law-enforcement kiosks | same as CUI + CJIS section 5 alignment |
| `asdEssentialEightDefaults` | Australian government | strict validation + PII redact + field encryption + signed audit |

`gdprDefaults` is a lighter preset (redact instead of reject) for commercial non-federal
workloads.

## 2. Configure transport security

Every high-security preset logs an ERROR when used with an HTTPS `baseUrl` and no
`TransportSecurityPolicy.certificatePins` or `certificateTransparencyEnabled`. The CA
trust store alone is not enough for these regimes. Pin the host against the specific
certificate(s) you operate:

```kotlin
val cfg = KioskOpsConfig.fedRampDefaults(
    baseUrl = "https://api.your-company.example",
    locationId = "STORE-001",
).copy(
    transportSecurityPolicy = TransportSecurityPolicy(
        certificatePins = listOf(
            CertificatePin(
                hostname = "api.your-company.example",
                sha256Pins = listOf(
                    "sha256/AAAA...", // primary leaf or intermediate
                    "sha256/BBBB...", // backup (critical for rotation)
                ),
            ),
        ),
        certificateTransparencyEnabled = true,
    ),
)
```

Plan pin rotations with an overlap window where both old and new pins are valid.
Rotating without overlap will quarantine your fleet.

## 3. Enable database-at-rest encryption

For CUI/CJIS you also get this via the preset, but you can enable it anywhere:

```kotlin
databaseEncryptionPolicy = DatabaseEncryptionPolicy.enabledDefaults()
```

Requires `net.zetetic:sqlcipher-android` on the classpath. The SQLCipher passphrase is
32 random bytes wrapped with an Android Keystore AES-GCM key that never leaves
TEE/StrongBox.

## 4. Gate data-rights operations

Data export, deletion, and wipe can exfiltrate an entire user's history or erase it.
On shared-kiosk devices, require explicit authorization:

```kotlin
KioskOpsSdk.get().setDataRightsAuthorizer { request ->
    if (myHostAppAuthenticatedUser() == request.subjectUserId) {
        AuthorizationDecision.Authorized
    } else {
        AuthorizationDecision.Unauthorized("user_not_signed_in")
    }
}
```

The `cuiDefaults` and `cjisDefaults` presets set `requireDataRightsAuthorization = true`
by default. All decisions are audit-logged.

## 5. Enable signed audit and chain verification

```kotlin
securityPolicy = SecurityPolicy.highSecurityDefaults().copy(
    signAuditEntries = true,
)
```

Periodically call `verifyAuditIntegrity(fromTs, toTs)` and surface the result to your
SOC. `ChainVerificationResult.Broken` is a tamper indicator.

## 6. Size the queue and retention for your fleet

Default `QueueLimits` caps at 100k events / 10 MB. If your workload drives higher
throughput, raise explicitly; don't let the queue silently drop events:

```kotlin
queueLimits = QueueLimits(
    maxActiveEvents = 500_000,
    maxActiveBytes = 50L * 1024L * 1024L,
    overflowStrategy = OverflowStrategy.DROP_OLDEST,
)
retentionPolicy = RetentionPolicy.maximalistDefaults().copy(
    retainSentEventsDays = 14,
    retainFailedEventsDays = 30,
    retainAuditDays = 365,
)
```

For compliance regimes that mandate long audit retention (FedRAMP AU-11 requires one
year), `retainAuditDays` must be set to at least that value; the presets already do.

## 7. Diagnostics export hygiene

- Set `DiagnosticsSchedulePolicy.maxExportBytes` explicitly. 50 MiB default; raise only
  if your upload path is known-resilient.
- If you wire `DiagnosticsUploader`, make sure it runs on constrained networks
  (honors the device's unmetered / battery-not-low state). The SDK schedules
  `DiagnosticsSchedulerWorker` respecting those constraints already.

## 8. Managed configuration (MDM)

Push `KioskOpsConfig` via the device's managed-config path rather than hard-coding in
the APK:

```kotlin
val cfg = ManagedConfigReader.read(context, defaults)
KioskOpsSdk.init(context, configProvider = { cfg })
```

Rotate `TransportSecurityPolicy.certificatePins` through the same channel; no app
update required. Deployments under `RemoteConfigPolicy.enterpriseDefaults()` require
signed config bundles so a compromised managed-config push cannot install a weaker
policy.

## 9. `pilotDefaults` never in production

`RemoteConfigPolicy.pilotDefaults` is `@RequiresOptIn(PilotConfig)` since 1.2.0. If your
production build requires an `@OptIn(PilotConfig::class)` marker, that's a bug; remove
it and switch to `enterpriseDefaults()`. Pilot defaults disable signature verification
and accept any version, which is the wrong posture for a fleet.

## 10. Observability

- Register a `KioskOpsErrorListener` that routes `KioskOpsError.*` to your error-tracking
  backend (Sentry, Datadog, Firebase Crashlytics, etc.). Listener-side exceptions are
  swallowed defensively but logged at `warn` so your own bugs don't go unnoticed.
- Collect the `queueDepthFlow` Room-reactive stream into your UI or dashboard so
  operators can see backlog drift early.
- Periodically call `getAuditStatistics()` on the host side to detect silent failure
  patterns (e.g., no `heartbeat` entries in the last 24h indicates the worker is dead).

## 11. Out-of-band disaster drills

- Document that `DataRightsManager.wipeAllSdkData()` fully clears SDK state. Exercise
  it in a staging environment before trusting it in production.
- Simulate a pin rotation in staging: push a config bundle with the new pin, monitor
  `certificate_pin_failure` audit entries drain to zero.
- Simulate a database corruption on a test device (delete a few database pages) and
  verify `KioskOpsError.StorageError` reaches your error listener and the SDK
  automatically recovers.

## What this doc is not

A replacement for your own threat model, penetration test, or regulatory assessment.
It's an opinionated checklist reflecting the SDK's design constraints. Your fleet's
threat model may add requirements this list doesn't mention (region-specific data
residency, hardware attestation quorum, physical tamper response). Treat this as a
floor, not a ceiling.
