# Key Management

This guide covers the cryptographic key management features added in v0.2.0: key rotation, attestation reporting, and configurable key derivation.

## Overview

Enterprise deployments require:
- **Key rotation** to limit exposure if keys are compromised
- **Key attestation** to prove keys are hardware-backed
- **Configurable derivation** to meet compliance requirements

## Key Rotation

### How It Works

`VersionedCryptoProvider` manages multiple key versions:

1. **Encryption**: Always uses the current (latest) key version
2. **Decryption**: Can decrypt any known key version
3. **Rotation**: Creates a new key version when triggered

### Configuration

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

### Presets

```kotlin
// Default: Annual rotation, 90-day backward compatibility
KeyRotationPolicy.default()

// Strict: Quarterly rotation, 30-day backward compatibility
KeyRotationPolicy.strict()

// Disabled: No rotation checks
KeyRotationPolicy.disabled()
```

### Manual Rotation

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

### Re-encryption

After rotation, you can migrate old data to the new key:

```kotlin
val oldBlob = readFromStorage()
val newBlob = provider.reencryptWithCurrentKey(oldBlob)
writeToStorage(newBlob)
```

## Key Attestation

### Checking Attestation Status

```kotlin
val reporter = KeyAttestationReporter(context)
val status = reporter.getAttestationStatus("kioskops_queue_aesgcm_v1")

if (status != null) {
  println("Hardware-backed: ${status.isHardwareBacked}")
  println("Security level: ${status.securityLevel}")  // SOFTWARE, TEE, or STRONGBOX
  println("Created at: ${status.keyCreatedAt}")
}
```

### Security Levels

| Level | Description |
|-------|-------------|
| `SOFTWARE` | Key stored in software (less secure) |
| `TEE` | Key stored in Trusted Execution Environment |
| `STRONGBOX` | Key stored in dedicated security chip (highest) |

### Device Posture

Attestation status is included in device posture:

```kotlin
val posture = sdk.devicePosture()
println("Supports HW attestation: ${posture.supportsHardwareAttestation}")
println("Key security level: ${posture.keySecurityLevel}")
println("Keys hardware-backed: ${posture.keysAreHardwareBacked}")
```

### Remote Attestation

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

## Key Derivation

### Configuration

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

### Presets

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

### Using Key Derivation

```kotlin
val derivation = SecureKeyDerivation(config)

// Derive a new key (generates random salt)
val result = derivation.deriveKey("password".toCharArray())
// result.key: The derived SecretKey
// result.salt: Salt to store alongside encrypted data

// Derive the same key later
val sameKey = derivation.deriveKeyWithSalt("password".toCharArray(), result.salt)
```

## Compliance Considerations

### SOC 2

- Use hardware-backed keys when available
- Enable key rotation with documented policy
- Monitor and report attestation status

### FedRAMP

- Require TEE or StrongBox security level
- Implement key rotation every 90 days
- Generate attestation challenge responses for verification

### HIPAA

- Encrypt all PHI with hardware-backed keys
- Document key management procedures
- Maintain audit trail of key operations

## Best Practices

1. **Check attestation on startup** to verify hardware backing
2. **Include attestation in diagnostics** for fleet visibility
3. **Plan key rotation** during low-traffic periods
4. **Test backward compatibility** before deleting old keys
5. **Document your key management policy** for auditors
