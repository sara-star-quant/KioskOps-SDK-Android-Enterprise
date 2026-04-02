# Features

> References to regulatory frameworks and security standards are engineering
> context only and do not constitute compliance claims, legal advice, or
> certification. See [Security & Compliance](SECURITY_COMPLIANCE.md) for full
> disclaimers.

## Security & Compliance

| Feature | Default | Description |
|---------|---------|-------------|
| Encryption at rest | On | AES-256-GCM via Android Keystore |
| PII detection | On | Detects and handles PII via `PiiPolicy` (email, phone, ssn, credit card, address, DOB, IP, passport, national ID) with configurable actions (REJECT, REDACT, FLAG) |
| Payload size limit | 64 KB | Configurable per deployment |
| Queue pressure control | 10K events / 50 MB | DROP_OLDEST, DROP_NEWEST, or BLOCK |
| Tamper-evident audit | On | Hash-chain with SHA-256, Room-backed |
| Retention controls | 7-30 days | Configurable per data type |

### Transport Security (v0.2.0)

| Feature | Default | Description |
|---------|---------|-------------|
| Certificate pinning | Off | SHA-256 pins with wildcard support |
| mTLS | Off | Client certificate authentication |
| Certificate Transparency | Off | CT log validation |

### Cryptography (v0.2.0)

| Feature | Default | Description |
|---------|---------|-------------|
| Key rotation | Off | Versioned encryption with backward compatibility |
| Key attestation | Available | Hardware-backed key reporting |
| Key derivation | OWASP 2023 | Configurable PBKDF2 parameters |

### Audit Trail (v0.2.0)

| Feature | Default | Description |
|---------|---------|-------------|
| Persistent audit | On | Room-backed, survives restarts |
| Signed entries | Off | Device attestation signatures |
| Integrity verification | Available | Chain verification API |

### Data Quality & Validation (v0.5.0)

| Feature | Default | Description |
|---------|---------|-------------|
| Event validation | Off | JSON Schema Draft 2020-12 subset |
| PII detection | Off | Regex-based scanner; REJECT/REDACT/FLAG actions |
| PII redaction | Off | Replaces values with `[REDACTED:TYPE]` markers |
| Field encryption | Off | Per-field AES-256-GCM encryption envelopes |
| Data classification | Off | PUBLIC/INTERNAL/CONFIDENTIAL/RESTRICTED tagging |
| Anomaly detection | Off | Statistical payload, rate, schema, cardinality analysis |

### Compliance APIs (v0.5.0)

| Feature | Default | Description |
|---------|---------|-------------|
| User data export | Available | GDPR Art. 20 data portability (ZIP) |
| User data deletion | Available | GDPR Art. 17 right to erasure |
| Full device wipe | Available | Removes all SDK data from device |
| Retention enforcement | On | Centralized with 365-day minimum audit retention |
| NIST annotations | Available | @NistControl source-retention markers |
| Config presets | Available | fedRampDefaults(), gdprDefaults() |

### Debug & Development (v0.5.0)

| Feature | Default | Description |
|---------|---------|-------------|
| Debug overlay | Available | Data-only SDK state snapshot |
| Performance profiler | Available | Operation timing for enqueue, validation, PII, etc. |

### Observability & Java Interop (v0.7.0)

| Feature | Default | Description |
|---------|---------|-------------|
| Health check | Available | Structured SDK health snapshot (queue, sync, auth, encryption) |
| Error listener | Off | Non-fatal error callbacks via `setErrorListener()` |
| Java interop | Available | Blocking wrappers returning `CompletableFuture`; `@JvmStatic`, `@JvmOverloads` |
| Debug log toggle | Off | ADB broadcast to change log level at runtime (debug builds only) |
| FIPS 140 detection | Available | Runtime check for FIPS-mode Conscrypt/BoringSSL provider |
| SBOM | CI | CycloneDX BOM generated in CI pipeline |

### Database Encryption & Reactive APIs (v0.8.0)

| Feature | Default | Description |
|---------|---------|-------------|
| Database-at-rest encryption | Off | SQLCipher encryption for Room databases; enabled by default in CUI and CJIS presets |
| Corruption recovery | On | `DatabaseCorruptionHandler` notifies error listener and recreates database |
| Queue depth Flow | Available | `queueDepthFlow()` polling-based reactive observation via Kotlin Flow |
| Health status Flow | Available | `healthStatusFlow()` polling-based health streaming via Kotlin Flow |
| SDK shutdown | Available | `shutdown()` for graceful teardown (scope cancellation, singleton cleanup) |
| CUI preset | Available | `cuiDefaults()` for NIST SP 800-171 / defense contractor deployments |
| CJIS preset | Available | `cjisDefaults()` for law enforcement kiosk deployments |
| ASD Essential Eight preset | Available | `asdEssentialEightDefaults()` for Australian government deployments |

See [Security & Compliance](SECURITY_COMPLIANCE.md) for the full threat model.

## Fleet Operations

- **Policy drift detection** - Detects config changes with hash comparison
- **Device posture snapshot** - Device owner, lock-task mode, security patch level
- **Diagnostics export** - ZIP bundle with health snapshot, logs, telemetry, audit trail
- **Host-controlled upload** - SDK never auto-uploads; you control when/where

### Remote Configuration (v0.3.0)

| Feature | Default | Description |
|---------|---------|-------------|
| Config refresh | Off | Push-based via managed config or FCM |
| Version monotonicity | On | Prevents rollback attacks (BSI APP.4.4.A5) |
| Signed config | Off | ECDSA P-256 signature verification |
| A/B testing | Off | Deterministic variant assignment |
| Rollback support | Available | Version history with minimum floor |

### Diagnostics Scheduling (v0.3.0)

| Feature | Default | Description |
|---------|---------|-------------|
| Scheduled collection | Off | Daily/weekly with WorkManager |
| Remote trigger | Off | Rate-limited with cooldown (BSI APP.4.4.A7) |
| Auto-upload | Off | Requires uploader configuration |

### Extended Device Posture (v0.3.0)

| Feature | Privacy | Description |
|---------|---------|-------------|
| Battery status | No PII | Level, charging, health, power saver |
| Storage status | No PII | Internal/external metrics, low storage detection |
| Connectivity status | No PII | Network type, signal level (no IP/MAC/SSID) |
| Device groups | Opaque IDs | Fleet segmentation (ISO 27001 A.8.1) |

## Network Sync (Opt-in)

Network synchronization is **disabled by default**. When enabled:

- **Batch ingest** - Configurable batch size with per-event acknowledgements
- **Exponential backoff** - 10s base, 6h max, with jitter
- **HMAC request signing** - Optional integrity protection
- **Poison event quarantine** - Non-retryable events excluded from sync

See [Server API Contract](openapi.yaml) for the ingest endpoint specification.

## Known Limitations

- ~~**Audit chain is process-local**: Restarts from GENESIS on app initialization~~ **Fixed in v0.2.0**
- **Lock-task mode**: Best-effort detection; not a full kiosk controller
- **Request signing**: Client-side only; server verification is your responsibility
- **Room migrations**: Provided for Queue v2->v3->v4 and Audit v1->v2
- **Key attestation**: Requires Android API 24+ for full functionality
- **StrongBox**: Hardware support varies by device
- **PII detection** (v0.5.0): Regex-based; not exhaustive; may produce false positives
- **Anomaly detection** (v0.5.0): Requires baseline period; initial events will not trigger flags
- **GDPR APIs** (v0.5.0): Cover SDK-local data only; host app and backend data is out of scope

## Roadmap

See [ROADMAP.md](../ROADMAP.md) for planned features and improvements.
