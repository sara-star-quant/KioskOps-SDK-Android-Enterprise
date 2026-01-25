# Audit Trail Integrity

This guide covers the persistent audit trail and integrity verification features added in v0.2.0.

## Overview

The v0.2.0 audit trail provides:
- **Persistence**: Audit chain survives app restarts
- **Signed entries**: Optional device attestation signatures
- **Verification API**: Check chain integrity programmatically

## Architecture

### Room-Backed Storage

Audit events are stored in a dedicated Room database:
- Separate from the event queue database
- Indexed by timestamp and event name
- Chain state tracked atomically

### Hash Chain

Each event includes:
- `hash`: SHA-256 of event content
- `prevHash`: Hash of the previous event
- Links form a tamper-evident chain

### Signed Entries (Optional)

When `signAuditEntries` is enabled:
- Events are signed with ECDSA P-256
- Signing key is hardware-backed when available
- Attestation chain is stored for verification

## Configuration

### Enable Persistent Audit

```kotlin
val securityPolicy = SecurityPolicy.maximalistDefaults().copy(
  useRoomBackedAudit = true,  // Default: enabled
)
```

### Enable Signed Entries

```kotlin
val securityPolicy = SecurityPolicy.maximalistDefaults().copy(
  signAuditEntries = true,  // Requires hardware-backed keys
)
```

### High-Security Preset

```kotlin
val securityPolicy = SecurityPolicy.highSecurityDefaults()
// Includes: useRoomBackedAudit = true, signAuditEntries = true
```

## Using the API

### Verify Chain Integrity

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

### Get Statistics

```kotlin
val stats = sdk.getAuditStatistics()
println("Total events: ${stats.totalEvents}")
println("Oldest event: ${stats.oldestEventTs}")
println("Newest event: ${stats.newestEventTs}")
println("Chain generation: ${stats.chainGeneration}")
println("Signed events: ${stats.signedEventCount}")
println("Events by name: ${stats.eventsByName}")
```

### Export Signed Range

```kotlin
// Export for compliance audit
val file = sdk.exportSignedAuditRange(
  fromTs = startOfMonth,
  toTs = endOfMonth,
)
// file is a gzipped JSONL containing all events with signatures
```

## Event Format

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

## Verification Results

| Result | Meaning |
|--------|---------|
| `Valid` | All events have valid hashes and links |
| `Broken` | Hash chain has a gap (tampering or corruption) |
| `HashMismatch` | Event content doesn't match its hash |
| `SignatureInvalid` | Device attestation signature is invalid |
| `EmptyRange` | No events in the specified range |

## Chain Generations

Each time the audit trail is initialized fresh (e.g., database cleared), the chain generation increments. This allows you to:
- Detect database resets
- Track audit continuity
- Identify intentional vs accidental chain breaks

## Compliance Use Cases

### SOC 2 Type II

1. Enable signed entries for non-repudiation
2. Export monthly audit ranges for evidence
3. Include verification results in compliance reports

### Internal Audit

1. Verify chain integrity on each heartbeat
2. Alert on any verification failures
3. Include chain generation in diagnostics

### Incident Response

1. Export the relevant time range
2. Verify signatures against known attestation chain
3. Identify exact timestamp of any tampering

## Best Practices

1. **Enable signing** for high-security deployments
2. **Verify on heartbeat** to catch issues early
3. **Export regularly** for off-device backup
4. **Monitor chain generation** for unexpected resets
5. **Include stats in diagnostics** for fleet visibility

## Limitations

- Signing requires hardware-backed keys (API 24+)
- Large audit trails may impact database performance
- Export files can be large for extended ranges
- Chain breaks don't prove malicious tampering (could be corruption)
