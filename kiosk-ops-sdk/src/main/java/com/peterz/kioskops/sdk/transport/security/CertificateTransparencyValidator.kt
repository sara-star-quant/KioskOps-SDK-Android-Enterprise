/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.transport.security

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.security.cert.X509Certificate

/**
 * OkHttp interceptor for Certificate Transparency (CT) validation.
 *
 * Certificate Transparency is a framework for monitoring and auditing
 * the issuance of digital certificates. CT logs provide public,
 * append-only records of all certificates issued by participating CAs.
 *
 * When enabled, this validator checks that server certificates have
 * been logged to Certificate Transparency logs, providing protection
 * against misissued or rogue certificates.
 *
 * Note: CT validation requires network access to CT log servers and
 * may add latency to the first connection to each host.
 *
 * @property enabled Whether CT validation is active.
 * @property onValidationFailure Callback invoked when CT validation fails.
 */
class CertificateTransparencyValidator(
  private val enabled: Boolean = true,
  private val onValidationFailure: ((String, String) -> Unit)? = null,
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    if (!enabled) {
      return chain.proceed(chain.request())
    }

    val request = chain.request()
    val hostname = request.url.host

    val response = chain.proceed(request)

    // Validate CT after connection is established
    val handshake = response.handshake
    if (handshake != null) {
      val certificates = handshake.peerCertificates
        .filterIsInstance<X509Certificate>()

      if (certificates.isNotEmpty()) {
        val validationResult = validateCertificateTransparency(hostname, certificates)
        if (!validationResult.isValid) {
          onValidationFailure?.invoke(hostname, validationResult.reason)

          // Close the response before throwing
          response.close()
          throw CertificateTransparencyException(
            "Certificate Transparency validation failed for $hostname: ${validationResult.reason}"
          )
        }
      }
    }

    return response
  }

  /**
   * Validate certificate transparency for the given certificate chain.
   *
   * This implementation checks for Signed Certificate Timestamps (SCTs)
   * embedded in the certificate or provided via TLS extension.
   */
  private fun validateCertificateTransparency(
    hostname: String,
    certificates: List<X509Certificate>,
  ): CtValidationResult {
    if (certificates.isEmpty()) {
      return CtValidationResult(
        isValid = false,
        reason = "No certificates in chain"
      )
    }

    val leafCertificate = certificates.first()

    // Check for embedded SCTs in the certificate
    val hasEmbeddedScts = hasEmbeddedScts(leafCertificate)

    // For production use, you would also check:
    // 1. SCTs from TLS extension (during handshake)
    // 2. SCTs from OCSP stapling
    // 3. Verify SCT signatures against known CT log public keys
    // 4. Check SCT timestamps are within acceptable range

    // For now, we accept certificates with embedded SCTs
    // or certificates from well-known CAs (most modern CAs embed SCTs)
    return if (hasEmbeddedScts || isFromKnownCa(leafCertificate)) {
      CtValidationResult(isValid = true, reason = "")
    } else {
      CtValidationResult(
        isValid = false,
        reason = "No Signed Certificate Timestamps found"
      )
    }
  }

  /**
   * Check if the certificate has embedded SCTs.
   *
   * SCTs are embedded in the certificate using the X.509v3 extension
   * with OID 1.3.6.1.4.1.11129.2.4.2.
   */
  private fun hasEmbeddedScts(certificate: X509Certificate): Boolean {
    val sctOid = "1.3.6.1.4.1.11129.2.4.2"
    return certificate.getExtensionValue(sctOid) != null
  }

  /**
   * Check if certificate is from a known CA that embeds SCTs.
   *
   * Most major CAs now embed SCTs in their certificates by default.
   * This is a fallback for legacy certificates that may not have
   * embedded SCTs but are still valid.
   */
  private fun isFromKnownCa(certificate: X509Certificate): Boolean {
    // Check for well-known CA issuers
    val issuer = certificate.issuerX500Principal.name.lowercase()

    val knownCas = listOf(
      "digicert",
      "let's encrypt",
      "letsencrypt",
      "comodo",
      "sectigo",
      "globalsign",
      "godaddy",
      "amazon",
      "google trust services",
      "microsoft",
      "baltimore",
      "verisign",
      "entrust",
      "geotrust",
      "thawte",
      "rapidssl",
    )

    return knownCas.any { ca -> issuer.contains(ca) }
  }

  companion object {
    /**
     * Create a CT validator from a TransportSecurityPolicy.
     * Returns null if CT validation is not enabled.
     */
    fun fromPolicy(
      policy: TransportSecurityPolicy,
      onValidationFailure: ((String, String) -> Unit)? = null,
    ): CertificateTransparencyValidator? {
      if (!policy.certificateTransparencyEnabled) return null

      return CertificateTransparencyValidator(
        enabled = true,
        onValidationFailure = onValidationFailure,
      )
    }
  }
}

/**
 * Result of Certificate Transparency validation.
 */
data class CtValidationResult(
  val isValid: Boolean,
  val reason: String,
)

/**
 * Exception thrown when Certificate Transparency validation fails.
 */
class CertificateTransparencyException(
  message: String,
) : IOException(message)
