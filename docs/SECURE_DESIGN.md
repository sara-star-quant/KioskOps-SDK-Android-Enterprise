# Secure Design Principles

This document captures the design principles the SDK is built to. It is intended for
integrators performing a security review and for reviewers evaluating this project
against the [OpenSSF Best Practices](https://bestpractices.coreinfrastructure.org/) Silver
criterion set. It is not a certification. See [LEGAL disclaimer](../README.md#disclaimer).

## Core principles

### 1. Secure-by-default

Public APIs default to the most conservative behavior. Enabling a risky option is always
an explicit call:

- Network sync is **off by default** (`SyncPolicy.disabledDefaults()`). A misconfigured
  host app cannot silently transfer events off-device.
- `PiiPolicy.maximalistDefaults()` rejects events containing likely PII. The host must
  explicitly switch to `redactDefaults()` or `disabledDefaults()` to relax.
- `SecurityPolicy.encryptQueuePayloads` is on by default. Payloads never sit in the
  queue database in the clear unless the host opts out.
- `DatabaseEncryptionPolicy.enabled` controls SQLCipher; when on, the database passphrase
  is wrapped by an Android Keystore AES-GCM key that never leaves TEE/StrongBox.

### 2. Explicit data-transfer boundaries

The SDK never uploads data on its own. The only code path that crosses the host-app
boundary is when `SyncPolicy.enabled == true` **and** a reachable `baseUrl` is configured
**and** an `AuthProvider` is installed (or a custom `OkHttpClient` is provided). A host
can additionally reset all SDK state via `dataRights.wipeAllSdkData()` or delete per-user
data via `dataRights.deleteUserData(userId)`.

Diagnostics export is explicit per invocation; upload is host-controlled via the
`DiagnosticsUploader` interface. The SDK never calls back to a network endpoint the host
did not configure.

### 3. Fail loud, not silent

Cryptographic, storage, and transport failures surface rather than silently degrade:

- `FieldLevelEncryptor` throws `FieldEncryptionException` on failure; the event pipeline
  rejects the event with `EnqueueResult.Rejected.FieldEncryptionFailed`.
- `MtlsClientBuilder` throws `MtlsConfigurationException` on any misconfiguration; it
  does not downgrade to unauthenticated TLS.
- `DatabaseCorruptionHandler` (wired into the Room open-helper factory) surfaces
  corruption to `KioskOpsErrorListener` and lands an audit entry before Room's
  default recreate runs.
- `KioskOpsErrorListener` callbacks swallow `Throwable` defensively (the SDK never
  crashes from a host listener) but log the swallowed exception at `warn` so host
  teams can see the misbehavior in their logcat and diagnostics.

### 4. Local-first observability

Telemetry, audit, and the event queue live on-device only. Nothing ships off-device
without host action. The queue and audit databases are encrypted at rest when the
corresponding policy flag is on. The audit trail is tamper-evident: each entry is
chained via SHA-256 and, when `SecurityPolicy.signAuditEntries` is on, signed with a
Keystore-backed key attested by the device.

### 5. Defense-in-depth for credentials and secrets

- SQLCipher passphrase: wrapped with a Keystore AES-GCM key.
- Per-install secret (`InstallSecret`, used for deterministic idempotency): wrapped the
  same way since 1.2.0, and zeroed after use at the call site.
- Random attestation challenges: 32 bytes of `SecureRandom`.
- Deterministic key derivation: HKDF-SHA256 (RFC 5869); no PBKDF2-over-char-array
  bridge since 1.2.0.
- Certificate pinning: enforced during the TLS handshake by OkHttp's native
  `CertificatePinner`, not as a post-response interceptor, so no request body leaks
  against a rogue server.

### 6. Minimum Android surface

- `minSdk = 33` (Android 13). Keystore-backed cryptography, scoped storage, and the
  modern intent model are assumed; older branches aren't maintained.
- `compileSdk` tracks the latest stable platform.
- No unbounded reflection, no `allowBackup = true` in the SDK manifest, no cleartext
  HTTP targets in the SDK-built `OkHttpClient`.

### 7. Reproducible builds and provenance

Every release artifact is:

- Signed by `sigstore/cosign-installer` with keyless signing.
- Described by a CycloneDX SBOM uploaded as a release asset.
- Verified against the `gradle-wrapper.jar` binary via `gradle-wrapper-validation` on
  every push.
- Produced by a GitHub Actions job whose SHAs are pinned in every workflow in this repo.

## What the SDK does not claim

The SDK ships engineering primitives that make it easier to hit compliance targets
(FedRAMP, NIST 800-171, CJIS, ASD Essential Eight, GDPR). It is not an auditor and it
cannot give you a certification. The regulatory-framework references in KDoc and in
compliance preset names are engineering aids, not legal claims.

Third-party assessments, penetration tests, and continuous-monitoring obligations
remain the host organization's responsibility.

## Related docs

- [HARDENING](HARDENING.md): operational hardening checklist for production.
- [VULNERABILITY_RESPONSE](VULNERABILITY_RESPONSE.md): disclosure and remediation SLA.
- [SECURITY_COMPLIANCE](SECURITY_COMPLIANCE.md): feature-by-framework crosswalk.
- [TROUBLESHOOTING](TROUBLESHOOTING.md): operator runbook for common symptoms.
