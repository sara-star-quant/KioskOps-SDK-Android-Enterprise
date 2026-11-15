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

### Audit checkpoints

The hash chain alone proves the stored rows are internally consistent. It cannot prove that
nobody deleted the newest rows or rebuilt the whole chain on a rooted device. With
`SecurityPolicy.signedAuditCheckpoints` on (on in the FedRAMP, CUI, CJIS, GDPR and ASD Essential
Eight presets), every sync batch carries an `auditCheckpoint`: head seq and hash, total event
count, chain generation, retention prune point, and a link to the last checkpoint the server
acknowledged. The device stores that acknowledgement (`lastAckedSeq`) only after a batch send
succeeds; a failed send leaves the link where it was, and an older acknowledgement never moves
it back.

Acknowledgement contract: an HTTP 2xx response with a parseable body is the acknowledgement. The
server must durably persist its new pin before it replies. A server that replies 2xx and then
loses the pin sees every later checkpoint as Forked until it re-baselines on trust; the two-pin
rule does not cover that.

Ordering: the server must process each device's checkpoints one at a time. R1 compares against
the latest pin, so a newer checkpoint pinned first makes a concurrently handled older one read
Regressed. The SDK serializes its own syncs (`SyncEngine.flushOnce` holds one lock per engine),
but a slow server can still see a retry overtake a request it has not finished.

Server rules. On first contact the server pins the checkpoint. After that it checks each new
checkpoint against its pin (`AuditChainCore.compareToPin` is the reference implementation) and
pins it when the result is Consistent:

- R1: same `chainGeneration`, and `headSeq` and `eventCount` not below the pin. Otherwise Regressed.
- R2: the link (`linkSeq`, `linkHash`) equals the pinned (`headSeq`, `headHash`) or the
  previous pin's. Otherwise Forked. A null link while a pin exists is Forked. The server keeps the
  last two pins: when it pinned a checkpoint but its response never reached the device, the next
  checkpoint still links to the previous pin. A link to the previous pin is checked under R3
  against the previous pin.
- R3: when `prunedThroughSeq` has advanced since the pin and reached the pinned `headSeq`
  (above the pin's `prunedThroughSeq` and at or above its `headSeq`), the result is Forked unless
  at least the device's audit retention window has passed since the server received the pin.
  After the window, a link that points at the prune point must carry `prunedThroughHash`; any
  other link re-baselines the pin on trust, because the pinned row is gone and nothing is left
  to compare.

Apply a small grace margin to the R3 retention-window comparison: it uses `>=`, so a legitimate
prune that lands exactly at the pin can read Forked when the device clock runs fast or the request
is slow. A server implementation must supply the previous pin's receipt time
(`previousPinReceivedAtMs`), or its lost-ack handling may flag a legitimate prune.

What the rules catch, given a pin: deleting the newest rows (R1); deleting the newest rows and
letting the chain grow back past the pin, or recomputing the whole chain with or without new
events (R2, the hash at the pinned seq changes); forging a prune record at or past the pinned
head the checkpoint links to before retention could have removed it (R3).

What they do not catch:

- Events recorded after the second-to-last acknowledged checkpoint and before a compromise.
  Because a link to the previous pin is accepted, an attacker who takes the device in that window
  can change or drop them undetected.
- A prune record forged below the pinned head the checkpoint links to. It passes and can erase
  older acknowledged history early; that pinned head still binds everything after it.
- Anything about a device that never syncs. The server has nothing to pin.
- An attacker with code execution on the device or with the signing key. Either can build any
  checkpoint the rules accept. Database write access alone is enough to defeat R2 for content at
  or before the pin if the attacker keeps the stored hash at the pinned row unchanged; that edit
  fails local `verifyChainIntegrity`, not the server rules.
- Device clock honesty. Event timestamps are the device's word; ordering comes from `seq`, and
  the R3 window runs on the server clock.
- Truncation before a trust-on-first-use point (below).

Trust on first use: a server trusts the first checkpoint it sees from a device, and R3 trusts a
re-baseline after the retention window. A database whose oldest rows retention removed before
seq existed gets its first remaining row recorded as the prune point when it upgrades.
Truncation before any of these moments is not detectable.

Retention prunes are recorded, not hidden: `applyRetention()` stores the last deleted seq and
hash as `prunedThroughSeq`/`prunedThroughHash` in the same transaction as the delete, and
verification starts from that point. Deleting the oldest rows without touching the chain state
breaks local verification. An attacker who edits both the rows and the chain state passes local
verification. The server rules then catch a forged prune record at or past the pinned head the
checkpoint links to inside the retention window (R3) and any rewrite of the row a link points at
(R2); they do not catch a forged prune record below the pinned head the checkpoint links to.

Retention deletes only the oldest run of events that are all past the cutoff. It never deletes a
row early, but a row written while the device clock ran ahead blocks every later prune until the
cutoff passes that row's timestamp. Data minimization, including under the GDPR preset, is
therefore bounded by the device clock being sane.

Signature key. The checkpoint is signed with the device audit key (`kioskops_audit_signing` in
the Android Keystore). The SDK does not send the public key or its attestation chain, and
`openapi.yaml` defines no enrollment endpoint. A host can read the chain with
`KeystoreAttestationProvider(context).getAttestationChain()` (the leaf certificate carries the
public key; its attestation challenge is generated on the device, so it shows how the key is
stored, not freshness) and deliver it to the server over its own channel. Until a server holds an
enrolled key it must treat checkpoint signatures as unauthenticated; rules R1 to R3 still apply.
A valid signature proves the SDK on that device built the checkpoint from what its database
holds, not that the database is honest.

Host requirement: keep `android:allowBackup="false"` (or exclude the audit database from
backup and device transfer). A restored backup is an older chain; it is indistinguishable from a
rollback and the server will flag it as one.

If the device cannot sign (no key, or signing fails) the checkpoint goes out unsigned and the
SDK records a `checkpoint_unsigned` audit event. It never skips silently.

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
