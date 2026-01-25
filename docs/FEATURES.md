# Features

## Security & Compliance

| Feature | Default | Description |
|---------|---------|-------------|
| Encryption at rest | On | AES-256-GCM via Android Keystore |
| PII denylist | On | Blocks common PII keys (email, phone, ssn, etc.) |
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

See [Security & Compliance](SECURITY_COMPLIANCE.md) for the full threat model.

## Fleet Operations

- **Policy drift detection** - Detects config changes with hash comparison
- **Device posture snapshot** - Device owner, lock-task mode, security patch level
- **Diagnostics export** - ZIP bundle with health snapshot, logs, telemetry, audit trail
- **Host-controlled upload** - SDK never auto-uploads; you control when/where

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
- **Room migrations**: Provided for v2 -> v3 schema
- **Key attestation**: Requires Android API 24+ for full functionality
- **StrongBox**: Hardware support varies by device

## Roadmap

See [ROADMAP.md](../ROADMAP.md) for planned features and improvements.
