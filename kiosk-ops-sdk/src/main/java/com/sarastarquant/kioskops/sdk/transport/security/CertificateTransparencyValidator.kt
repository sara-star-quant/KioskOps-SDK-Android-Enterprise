/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.transport.security

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.security.cert.X509Certificate

/**
 * SCT-presence-only check for Certificate Transparency.
 *
 * Verifies that a Signed Certificate Timestamp (SCT) extension is present on the leaf
 * certificate. Does NOT verify SCT signatures against IANA-approved CT logs, check
 * inclusion proofs, or compare SCT timestamps to the certificate's validity window. Full
 * verification per RFC 6962 is tracked for v1.3; see ROADMAP.md.
 *
 * This partial check still raises the bar vs no CT enforcement at all: every public-trust
 * CA has embedded SCTs since 2018, so a certificate with no SCT extension is either from a
 * private CA (which pin validation should catch first) or a malformed issuance. A rogue
 * certificate from a compromised public CA can still pass this presence check; a full log
 * verification would catch that.
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
   * Checks for Signed Certificate Timestamps (SCTs) embedded in the leaf
   * certificate (X.509v3 extension OID 1.3.6.1.4.1.11129.2.4.2).
   *
   * NOTE: This is an SCT *presence* check. Full CT validation additionally
   * verifies SCT signatures against the public keys of IANA-approved CT
   * logs, checks log inclusion proofs, and compares SCT timestamps against
   * the certificate's validity window. Presence-only is a pragmatic default
   * for modern CAs (all public-trust CAs have embedded SCTs since 2018) but
   * is not a full CT enforcement implementation. Consumers requiring full
   * verification should supplement this interceptor with a library like
   * `com.appmattus.certificatetransparency` on the OkHttpClient.
   *
   * Previous versions contained an `isFromKnownCa` issuer-string bypass
   * which was removed in 1.1.0 because substring matching on the issuer DN
   * is not a security boundary.
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
    return if (hasEmbeddedScts(leafCertificate)) {
      CtValidationResult(isValid = true, reason = "")
    } else {
      CtValidationResult(
        isValid = false,
        reason = "No embedded Signed Certificate Timestamps in leaf certificate",
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
