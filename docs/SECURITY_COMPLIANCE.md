# Security and Compliance

> **Disclaimer:** This document references regulatory frameworks (NIST 800-53,
> FedRAMP, GDPR, ISO 27001, BSI, HIPAA) as engineering context only. Nothing in
> this document or the SDK constitutes legal, compliance, or security advice. The
> SDK has not been independently audited or certified against any standard. The
> authors and contributors accept no responsibility for regulatory outcomes,
> security incidents, or compliance failures. You must conduct your own legal and
> security assessments before deploying in regulated environments. Consult
> qualified legal and security professionals for your specific situation.

This SDK is designed for **enterprise environments** where you usually care about:
- **data minimization** (collect/store the least possible)
- **defense in depth** (multiple independent controls)
- **regional data residency** constraints (you choose where data travels)

The guiding principle is: **local-first by default**, with **explicit opt-in** for any off-device transfer.

---

## 1. Security Defaults

### Data minimization
- **Queue guardrails**: caps payload size; PII detection via `PiiPolicy` (regex-based with configurable actions: REJECT, REDACT, or FLAG)
- **Telemetry allow-list**: only keys in `TelemetryPolicy.allowedKeys` are persisted
- **No payloads in telemetry/audit**: the SDK never emits queue payload contents into telemetry or audit records

### Encryption at rest (Android Keystore AES-GCM)
When enabled by `SecurityPolicy`:
- **Queued event payloads**: stored encrypted in the Room DB as BLOBs
- **Telemetry files**: day-sliced JSONL with per-line encryption (append-friendly)
- **Audit trail files**: day-sliced JSONL with per-line encryption (append-friendly)
- **Exported logs**: encrypted on export

Diagnostics exports are ZIPs containing minimal entries; many entries may already be encrypted-at-rest.

### Remote configuration security (v0.3.0)
When enabled via `RemoteConfigPolicy`:
- **Version monotonicity**: Prevents rollback attacks by enforcing strictly increasing version numbers (BSI APP.4.4.A5)
- **Minimum version floor**: Blocks rollback below a configured threshold for security updates
- **Signature verification**: Optional ECDSA P-256 signature validation for config bundles (BSI APP.4.4.A3)
- **Cooldown period**: Prevents rapid config cycling attacks
- **Audit logging**: All config transitions are recorded (ISO 27001 A.12.4)

### Remote diagnostics security (v0.3.0)
When enabled via `DiagnosticsSchedulePolicy`:
- **Rate limiting**: Maximum triggers per day prevents abuse (BSI APP.4.4.A7)
- **Cooldown period**: Minimum interval between triggers
- **Deduplication**: Prevents replay of trigger requests
- **Audit logging**: All triggers (accepted/rejected) are recorded

### Extended posture privacy (v0.3.0)
Extended device posture collection follows GDPR data minimization:
- **Battery**: Level, status, health only; no device identifiers
- **Storage**: Aggregate metrics only; no file listings or content
- **Connectivity**: Network type and signal level only; no IP addresses, MAC addresses, SSIDs, or cell tower IDs
- **Device groups**: Opaque string identifiers only; no PII in group names

### Event validation (v0.5.0)
When enabled via `ValidationPolicy`:
- **JSON Schema validation**: Draft 2020-12 subset (type, required, properties, pattern, minLength/maxLength, enum, format, minimum/maximum)
- **Strict/permissive modes**: Strict rejects invalid events; permissive flags only
- **Unknown event types**: Configurable ALLOW/FLAG/REJECT for unregistered schemas
- **Fail-safe**: Validation errors never block the pipeline in permissive mode

### PII detection and redaction (v0.5.0)
When enabled via `PiiPolicy`:
- **Regex-based detection**: ~12 compiled patterns for EMAIL, PHONE, SSN, CREDIT_CARD, IP_ADDRESS, DOB, PASSPORT, NATIONAL_ID
- **Configurable actions**: REJECT (block event), REDACT_VALUE (replace with `[REDACTED:TYPE]`), FLAG_AND_ALLOW (pass through with metadata)
- **Pluggable interface**: Implement `PiiDetector` for custom or ML-based detection
- **Field exclusions**: Skip scanning on known-safe paths
- **Scan before persist**: PII detection runs before encryption to satisfy data minimization requirements

Note: The regex-based detector provides reasonable coverage for common PII patterns but is not exhaustive. It may produce false positives or miss obfuscated PII. For high-assurance environments, implement a custom `PiiDetector` with domain-specific logic.

### Anomaly detection (v0.5.0)
When enabled via `AnomalyPolicy`:
- **Payload size z-score**: Detects abnormally large or small payloads per event type
- **Event rate tracking**: Sliding window counter detects burst patterns
- **Schema deviation scoring**: Flags unexpected or missing fields
- **Field cardinality tracking**: Detects enumeration or credential stuffing patterns
- **Pluggable interface**: Implement `AnomalyDetector` for custom detection logic

Note: Statistical anomaly detection requires a baseline period to be effective. Initial events will not trigger anomaly flags. The detector uses heuristics and may produce false positives.

### Field-level encryption (v0.5.0)
When enabled via `FieldEncryptionPolicy`:
- **Per-field encryption**: Individual JSON fields encrypted into `{"__enc":"...","__alg":"AES-256-GCM","__kid":"v1"}` envelopes
- **Before document encryption**: Field encryption applies before full payload encryption for defense in depth
- **Configurable per event type**: Different fields can be encrypted for different event types

### GDPR data rights (v0.5.0)
- **Data export**: `exportUserData(userId)` exports queue events, audit events, and telemetry for a specific user (Art. 20)
- **Data deletion**: `deleteUserData(userId)` erases user data from queue and audit databases (Art. 17)
- **Full wipe**: `wipeAllSdkData()` removes all SDK data from the device
- **userId tracking**: Optional userId column on queue and audit events for data subject identification

Note: These APIs cover SDK-local data only. Your host application and backend may store additional data outside SDK scope. Implementing a complete GDPR-compliant system requires coordination across your full stack.

### Data classification (v0.5.0)
When enabled via `DataClassificationPolicy`:
- **Automatic tagging**: Events tagged as PUBLIC, INTERNAL, CONFIDENTIAL, or RESTRICTED
- **PII-aware**: Events with detected PII automatically elevated to configured classification level

### Retention controls
Retention is configured via `RetentionPolicy` (by days) for:
- telemetry files
- audit files
- exported artifacts (logs/diagnostics)
- queue cleanup (sent/failed lifetimes)
- **minimum audit retention** (v0.5.0): 365 days default, enforced independently of `retainAuditDays`

### Device identifiers
- The SDK maintains an **SDK-scoped pseudonymous deviceId** (stable per install).
- The deviceId can be **reset** via `dataRights.resetSdkDeviceId()`.
- Telemetry **does not include** device identifiers by default (`TelemetryPolicy.includeDeviceId=false`).

If you enable **network sync**, the SDK will include `deviceId` in batch requests (because idempotent ingest needs a stable per-device correlation key).

---

## 2. Transport Security

Transport security protects SDK network communications against:
- Man-in-the-middle (MITM) attacks
- Compromised Certificate Authorities
- Rogue certificates and SSL inspection proxies
- Unauthorized server impersonation

### Certificate Pinning

Certificate pinning validates that server certificates match pre-configured SHA-256 pins.

#### Configuration

```kotlin
val config = KioskOpsConfig(
  baseUrl = "https://api.example.com/",
  locationId = "STORE-001",
  transportSecurityPolicy = TransportSecurityPolicy(
    certificatePins = listOf(
      CertificatePin(
        hostname = "api.example.com",
        sha256Pins = listOf(
          "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", // Primary
          "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=", // Backup for rotation
        )
      ),
      CertificatePin(
        hostname = "*.cdn.example.com",
        sha256Pins = listOf("CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=")
      ),
    )
  )
)
```

#### Obtaining Certificate Pins

Use OpenSSL to extract the pin from a certificate:

```bash
# From a live server
openssl s_client -connect api.example.com:443 -servername api.example.com < /dev/null 2>/dev/null | \
  openssl x509 -pubkey -noout | \
  openssl pkey -pubin -outform der | \
  openssl dgst -sha256 -binary | \
  base64

# From a certificate file
openssl x509 -in certificate.pem -pubkey -noout | \
  openssl pkey -pubin -outform der | \
  openssl dgst -sha256 -binary | \
  base64
```

#### Key Rotation for Pins

Always include at least two pins per hostname:
1. **Primary pin**: Current certificate
2. **Backup pin**: Next certificate (for rotation)

This allows seamless certificate rotation without app updates.

#### Wildcard Matching

Wildcard pins (`*.example.com`) match one level of subdomain:
- `*.example.com` matches `api.example.com`
- `*.example.com` does NOT match `a.b.example.com`

### Mutual TLS (mTLS)

mTLS provides two-way authentication where both client and server verify each other.

#### Configuration

```kotlin
val config = KioskOpsConfig(
  baseUrl = "https://api.example.com/",
  locationId = "STORE-001",
  transportSecurityPolicy = TransportSecurityPolicy(
    mtlsConfig = MtlsConfig(
      clientCertificateProvider = MyClientCertificateProvider()
    )
  )
)
```

#### Implementing ClientCertificateProvider

```kotlin
class KeystoreClientCertificateProvider(
  private val context: Context,
  private val alias: String,
) : ClientCertificateProvider {

  override fun getCertificateAndKey(): CertificateCredentials? {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    val privateKey = keyStore.getKey(alias, null) as? PrivateKey
      ?: return null
    val certificate = keyStore.getCertificate(alias) as? X509Certificate
      ?: return null

    return CertificateCredentials(
      certificate = certificate,
      privateKey = privateKey,
    )
  }
}
```

#### Provisioning Client Certificates

Client certificates can be provisioned via:
- **Android Enterprise**: Managed configuration with PKCS#12 bundles
- **MDM/EMM**: Knox Manage, Workspace ONE, Intune
- **On-device generation**: Create key pair and CSR, submit for signing

### Certificate Transparency

CT validation checks that certificates have been logged to public CT logs.

#### Configuration

```kotlin
val config = KioskOpsConfig(
  baseUrl = "https://api.example.com/",
  locationId = "STORE-001",
  transportSecurityPolicy = TransportSecurityPolicy(
    certificateTransparencyEnabled = true
  )
)
```

#### How It Works

1. During TLS handshake, the SDK checks for Signed Certificate Timestamps (SCTs)
2. SCTs prove the certificate was logged to a CT log
3. If no valid SCTs are found, the connection fails

#### Considerations

- CT validation may add latency (first connection to each host)
- Requires network access to CT log servers
- Most modern CAs embed SCTs in certificates by default

### Transport Audit Events

Transport security failures are recorded in the audit trail:

| Event | Description |
|-------|-------------|
| `certificate_pin_failure` | Server certificate does not match pins |
| `certificate_transparency_failure` | No valid SCTs found |

### Transport Error Handling

Transport security failures result in `TransportResult.PermanentFailure`:

```kotlin
when (val result = sdk.syncOnce()) {
  is TransportResult.PermanentFailure -> {
    if (result.message.contains("certificate_pinning_failed")) {
      // Certificate does not match configured pins
      // This is a security event; investigate immediately
    }
  }
  // ...
}
```

### Transport Best Practices

1. **Always use backup pins** for rotation without app updates
2. **Pin to intermediate CA** rather than leaf certificate for easier rotation
3. **Test pin changes** in staging before production rollout
4. **Monitor audit events** for unexpected pin failures
5. **Have a rollback plan** if pins are misconfigured

---

## 3. Key Management

Enterprise deployments require:
- **Key rotation** to limit exposure if keys are compromised
- **Key attestation** to prove keys are hardware-backed
- **Configurable derivation** to meet compliance requirements

### Key Rotation

`VersionedCryptoProvider` manages multiple key versions:

1. **Encryption**: Always uses the current (latest) key version
2. **Decryption**: Can decrypt any known key version
3. **Rotation**: Creates a new key version when triggered

#### Configuration

```kotlin
val securityPolicy = SecurityPolicy.maximalistDefaults().copy(
  keyRotationPolicy = KeyRotationPolicy(
    maxKeyAgeDays = 365,        // Recommend rotation after 1 year
    autoRotateEnabled = false,  // Manual rotation (default)
    retainOldKeysForDays = 90,  // Keep old keys for backward compatibility
    maxKeyVersions = 5,         // Maximum retained versions
  )
)
```

#### Presets

```kotlin
// Default: Annual rotation, 90-day backward compatibility
KeyRotationPolicy.default()

// Strict: Quarterly rotation, 30-day backward compatibility
KeyRotationPolicy.strict()

// Disabled: No rotation checks
KeyRotationPolicy.disabled()
```

#### Manual Rotation

```kotlin
// Check if rotation is recommended
val provider = VersionedCryptoProvider(context, "my_key")
if (provider.shouldRotate()) {
  when (val result = provider.rotateKey()) {
    is RotationResult.Success -> {
      // New key version: result.newKeyVersion
      // Old version: result.previousKeyVersion
    }
    is RotationResult.Failed -> {
      // Handle error: result.reason
    }
    is RotationResult.NotNeeded -> {
      // Key is not old enough
    }
  }
}
```

#### Re-encryption

After rotation, you can migrate old data to the new key:

```kotlin
val oldBlob = readFromStorage()
val newBlob = provider.reencryptWithCurrentKey(oldBlob)
writeToStorage(newBlob)
```

### Key Attestation

#### Checking Attestation Status

```kotlin
val reporter = KeyAttestationReporter(context)
val status = reporter.getAttestationStatus("kioskops_queue_aesgcm_v1")

if (status != null) {
  println("Hardware-backed: ${status.isHardwareBacked}")
  println("Security level: ${status.securityLevel}")  // SOFTWARE, TEE, or STRONGBOX
  println("Created at: ${status.keyCreatedAt}")
}
```

#### Security Levels

| Level | Description |
|-------|-------------|
| `SOFTWARE` | Key stored in software (less secure) |
| `TEE` | Key stored in Trusted Execution Environment |
| `STRONGBOX` | Key stored in dedicated security chip (highest) |

#### Device Posture

Attestation status is included in device posture:

```kotlin
val posture = sdk.devicePosture()
println("Supports HW attestation: ${posture.supportsHardwareAttestation}")
println("Key security level: ${posture.keySecurityLevel}")
println("Keys hardware-backed: ${posture.keysAreHardwareBacked}")
```

#### Remote Attestation

For remote verification of key attestation:

```kotlin
val challenge = serverProvidedChallengeBytes
val response = reporter.generateAttestationChallengeResponse("attestation_key", challenge)

if (response != null) {
  // Send to server for verification:
  // - response.attestationChain: Certificate chain proving hardware backing
  // - response.signature: Signature over the challenge
  // - response.challenge: Original challenge bytes
}
```

### Key Derivation

#### Configuration

```kotlin
val securityPolicy = SecurityPolicy.maximalistDefaults().copy(
  keyDerivationConfig = KeyDerivationConfig(
    algorithm = "PBKDF2WithHmacSHA256",
    iterationCount = 310_000,  // OWASP 2023 recommendation
    saltLengthBytes = 32,
    keyLengthBits = 256,
  )
)
```

#### Presets

```kotlin
// Default: OWASP 2023 recommended settings
KeyDerivationConfig.default()

// High security: More iterations, SHA-512
KeyDerivationConfig.highSecurity()

// Legacy: For migration from older systems
KeyDerivationConfig.legacy()

// Fast: FOR TESTING ONLY
KeyDerivationConfig.fastForTesting()
```

#### Using Key Derivation

```kotlin
val derivation = SecureKeyDerivation(config)

// Derive a new key (generates random salt)
val result = derivation.deriveKey("password".toCharArray())
// result.key: The derived SecretKey
// result.salt: Salt to store alongside encrypted data

// Derive the same key later
val sameKey = derivation.deriveKeyWithSalt("password".toCharArray(), result.salt)
```

### Key Management Compliance Considerations

#### SOC 2

- Use hardware-backed keys when available
- Enable key rotation with documented policy
- Monitor and report attestation status

#### FedRAMP

- Require TEE or StrongBox security level
- Implement key rotation every 90 days
- Generate attestation challenge responses for verification

#### HIPAA

- Encrypt all PHI with hardware-backed keys
- Document key management procedures
- Maintain audit trail of key operations

### Key Management Best Practices

1. **Check attestation on startup** to verify hardware backing
2. **Include attestation in diagnostics** for fleet visibility
3. **Plan key rotation** during low-traffic periods
4. **Test backward compatibility** before deleting old keys
5. **Document your key management policy** for auditors

---

## 4. Audit Trail Integrity

The v0.2.0 audit trail provides:
- **Persistence**: Audit chain survives app restarts
- **Signed entries**: Optional device attestation signatures
- **Verification API**: Check chain integrity programmatically

### Architecture

#### Room-Backed Storage

Audit events are stored in a dedicated Room database:
- Separate from the event queue database
- Indexed by timestamp and event name
- Chain state tracked atomically

#### Hash Chain

Each event includes:
- `hash`: SHA-256 of event content
- `prevHash`: Hash of the previous event
- Links form a tamper-evident chain

#### Signed Entries (Optional)

When `signAuditEntries` is enabled:
- Events are signed with ECDSA P-256
- Signing key is hardware-backed when available
- Attestation chain is stored for verification

### Configuration

#### Enable Persistent Audit

```kotlin
val securityPolicy = SecurityPolicy.maximalistDefaults().copy(
  useRoomBackedAudit = true,  // Default: enabled
)
```

#### Enable Signed Entries

```kotlin
val securityPolicy = SecurityPolicy.maximalistDefaults().copy(
  signAuditEntries = true,  // Requires hardware-backed keys
)
```

#### High-Security Preset

```kotlin
val securityPolicy = SecurityPolicy.highSecurityDefaults()
// Includes: useRoomBackedAudit = true, signAuditEntries = true
```

### Verification API

#### Verify Chain Integrity

```kotlin
// Verify entire chain
when (val result = sdk.verifyAuditIntegrity()) {
  is ChainVerificationResult.Valid -> {
    println("Chain is valid: ${result.eventCount} events")
  }
  is ChainVerificationResult.Broken -> {
    println("Chain broken at event ${result.brokenAtId}")
    println("Expected: ${result.expectedPrevHash}")
    println("Found: ${result.actualPrevHash}")
  }
  is ChainVerificationResult.HashMismatch -> {
    println("Event ${result.eventId} has been modified")
  }
  is ChainVerificationResult.SignatureInvalid -> {
    println("Signature invalid for event ${result.eventId}")
  }
  is ChainVerificationResult.EmptyRange -> {
    println("No events in range")
  }
}

// Verify a time range
val result = sdk.verifyAuditIntegrity(
  fromTs = startOfDay,
  toTs = endOfDay,
)
```

#### Get Statistics

```kotlin
val stats = sdk.getAuditStatistics()
println("Total events: ${stats.totalEvents}")
println("Oldest event: ${stats.oldestEventTs}")
println("Newest event: ${stats.newestEventTs}")
println("Chain generation: ${stats.chainGeneration}")
println("Signed events: ${stats.signedEventCount}")
println("Events by name: ${stats.eventsByName}")
```

#### Export Signed Range

```kotlin
// Export for compliance audit
val file = sdk.exportSignedAuditRange(
  fromTs = startOfMonth,
  toTs = endOfMonth,
)
// file is a gzipped JSONL containing all events with signatures
```

### Event Format

Exported events include:

```json
{
  "id": "uuid-1234",
  "ts": 1706200000000,
  "name": "event_enqueued",
  "fields": "{\"type\":\"button_press\"}",
  "prevHash": "abc123...",
  "hash": "def456...",
  "signature": "base64-ecdsa-signature",
  "chainGeneration": 1
}
```

### Verification Results

| Result | Meaning |
|--------|---------|
| `Valid` | All events have valid hashes and links |
| `Broken` | Hash chain has a gap (tampering or corruption) |
| `HashMismatch` | Event content does not match its hash |
| `SignatureInvalid` | Device attestation signature is invalid |
| `EmptyRange` | No events in the specified range |

### Chain Generations

Each time the audit trail is initialized fresh (e.g., database cleared), the chain generation increments. This allows you to:
- Detect database resets
- Track audit continuity
- Identify intentional vs accidental chain breaks

### Audit Compliance Use Cases

#### SOC 2 Type II

1. Enable signed entries for non-repudiation
2. Export monthly audit ranges for evidence
3. Include verification results in compliance reports

#### Internal Audit

1. Verify chain integrity on each heartbeat
2. Alert on any verification failures
3. Include chain generation in diagnostics

#### Incident Response

1. Export the relevant time range
2. Verify signatures against known attestation chain
3. Identify exact timestamp of any tampering

### Audit Best Practices

1. **Enable signing** for high-security deployments
2. **Verify on heartbeat** to catch issues early
3. **Export regularly** for off-device backup
4. **Monitor chain generation** for unexpected resets
5. **Include stats in diagnostics** for fleet visibility

### Audit Limitations

- Signing requires hardware-backed keys (API 24+)
- Large audit trails may impact database performance
- Export files can be large for extended ranges
- Chain breaks do not prove malicious tampering (could be corruption)

---

## 5. Data Transfer and Network Sync

By default, the SDK is **local-only**.

If you enable network sync (`SyncPolicy(enabled = true)`), the host app is responsible for:
- choosing the lawful basis / consent strategy
- data residency routing (regional endpoints)
- retention and access controls server-side
- any additional PII minimization or schema redaction

The SDK keeps sync **explicit** and **opt-in** to reduce compliance surprises.

### Optional request signing
For additional defense-in-depth, the host app may provide a `RequestSigner` (e.g. `HmacRequestSigner`) during initialization.
If configured, the SDK adds a timestamp + nonce + HMAC signature headers to the batch ingest request. This helps detect tampering and reduces replay risk.
Server-side verification and key rotation are owned by your backend.

### Regional regulations and data residency
To stay sane across jurisdictions:
- Treat the SDK as **local-first**.
- If you need to move data off-device, do it through your **host app + backend** where you can:
  - route by region (EU/US/AU)
  - apply DPA / contractual controls
  - implement consent / policy logic
  - implement authenticated endpoints and key rotation

The SDK provides **hooks** (diagnostics upload interface, transport auth hook) rather than making data-flow decisions for you.

---

## 6. Scope and Limitations

### What the SDK intentionally does NOT do
- It does **not** auto-upload telemetry or diagnostics.
- It does **not** infer or enforce your legal basis (GDPR, HIPAA, etc.). That is a product/legal decision.
- It does **not** attempt to be a full kiosk controller or MDM.

### Known limitations
- Room migrations are provided for schema transitions (Queue v2->v3->v4, Audit v1->v2). Versions < v2 (queue) were pre-release snapshots.
- Key attestation requires Android API 24+ for full functionality.
- StrongBox security level requires hardware support (not all devices).
- Remote diagnostics trigger requires ACCESS_NETWORK_STATE permission for connectivity status (normal permission, auto-granted).
- Cellular network type detection requires READ_PHONE_STATE permission; gracefully returns UNKNOWN if not granted.
- PII regex detection (v0.5.0) covers common patterns but is not exhaustive; it may produce false positives or miss obfuscated PII.
- Anomaly detection (v0.5.0) requires a baseline period; initial events will not trigger flags.
- NIST control annotations (@NistControl) are engineering references, not certification claims.
