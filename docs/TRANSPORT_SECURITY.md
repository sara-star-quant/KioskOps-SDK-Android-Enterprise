# Transport Security

This guide covers the transport layer security features added in v0.2.0: certificate pinning, mutual TLS (mTLS), and Certificate Transparency (CT) validation.

## Overview

Transport security protects SDK network communications against:
- Man-in-the-middle (MITM) attacks
- Compromised Certificate Authorities
- Rogue certificates and SSL inspection proxies
- Unauthorized server impersonation

## Certificate Pinning

Certificate pinning validates that server certificates match pre-configured SHA-256 pins.

### Configuration

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

### Obtaining Certificate Pins

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

### Key Rotation

Always include at least two pins per hostname:
1. **Primary pin**: Current certificate
2. **Backup pin**: Next certificate (for rotation)

This allows seamless certificate rotation without app updates.

### Wildcard Matching

Wildcard pins (`*.example.com`) match one level of subdomain:
- `*.example.com` matches `api.example.com`
- `*.example.com` does NOT match `a.b.example.com`

## Mutual TLS (mTLS)

mTLS provides two-way authentication where both client and server verify each other.

### Configuration

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

### Implementing ClientCertificateProvider

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

### Provisioning Client Certificates

Client certificates can be provisioned via:
- **Android Enterprise**: Managed configuration with PKCS#12 bundles
- **MDM/EMM**: Knox Manage, Workspace ONE, Intune
- **On-device generation**: Create key pair and CSR, submit for signing

## Certificate Transparency

CT validation checks that certificates have been logged to public CT logs.

### Configuration

```kotlin
val config = KioskOpsConfig(
  baseUrl = "https://api.example.com/",
  locationId = "STORE-001",
  transportSecurityPolicy = TransportSecurityPolicy(
    certificateTransparencyEnabled = true
  )
)
```

### How It Works

1. During TLS handshake, the SDK checks for Signed Certificate Timestamps (SCTs)
2. SCTs prove the certificate was logged to a CT log
3. If no valid SCTs are found, the connection fails

### Considerations

- CT validation may add latency (first connection to each host)
- Requires network access to CT log servers
- Most modern CAs embed SCTs in certificates by default

## Audit Events

Transport security failures are recorded in the audit trail:

| Event | Description |
|-------|-------------|
| `certificate_pin_failure` | Server certificate doesn't match pins |
| `certificate_transparency_failure` | No valid SCTs found |

## Error Handling

Transport security failures result in `TransportResult.PermanentFailure`:

```kotlin
when (val result = sdk.syncOnce()) {
  is TransportResult.PermanentFailure -> {
    if (result.message.contains("certificate_pinning_failed")) {
      // Certificate doesn't match configured pins
      // This is a security event - investigate immediately
    }
  }
  // ...
}
```

## Best Practices

1. **Always use backup pins** for rotation without app updates
2. **Pin to intermediate CA** rather than leaf certificate for easier rotation
3. **Test pin changes** in staging before production rollout
4. **Monitor audit events** for unexpected pin failures
5. **Have a rollback plan** if pins are misconfigured
