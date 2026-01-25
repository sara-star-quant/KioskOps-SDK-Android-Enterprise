/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.transport.security

import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * OkHttp interceptor for certificate pinning validation.
 *
 * This interceptor validates server certificates against configured pins
 * and emits audit events on pin validation failures.
 *
 * Certificate pinning provides defense-in-depth against:
 * - Compromised Certificate Authorities
 * - Man-in-the-middle attacks with forged certificates
 * - SSL inspection proxies (when not desired)
 *
 * @property pins List of certificate pins per hostname.
 * @property onPinValidationFailure Callback invoked when pin validation fails.
 *           Receives hostname and exception details for audit logging.
 */
class CertificatePinningInterceptor(
  private val pins: List<CertificatePin>,
  private val onPinValidationFailure: ((String, String) -> Unit)? = null,
) : Interceptor {

  private val certificatePinner: CertificatePinner by lazy {
    buildPinner()
  }

  private fun buildPinner(): CertificatePinner {
    val builder = CertificatePinner.Builder()

    for (pin in pins) {
      for (sha256Pin in pin.sha256Pins) {
        // OkHttp expects pins in format: "sha256/BASE64_HASH"
        val formattedPin = if (sha256Pin.startsWith("sha256/")) {
          sha256Pin
        } else {
          "sha256/$sha256Pin"
        }
        builder.add(pin.hostname, formattedPin)
      }
    }

    return builder.build()
  }

  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val hostname = request.url.host

    // Check if we have pins for this hostname
    if (!hasPinsForHost(hostname)) {
      // No pins configured for this host, proceed without validation
      return chain.proceed(request)
    }

    try {
      val response = chain.proceed(request)

      // Validate certificate pins post-connection
      try {
        certificatePinner.check(hostname, response.handshake?.peerCertificates ?: emptyList())
      } catch (e: SSLPeerUnverifiedException) {
        onPinValidationFailure?.invoke(hostname, e.message ?: "Pin validation failed")
        throw CertificatePinningException(
          "Certificate pinning validation failed for $hostname: ${e.message}",
          e
        )
      }

      return response
    } catch (e: SSLPeerUnverifiedException) {
      onPinValidationFailure?.invoke(hostname, e.message ?: "SSL peer unverified")
      throw e
    }
  }

  /**
   * Check if we have pins configured for a given hostname.
   * Supports wildcard matching (e.g., *.example.com).
   */
  private fun hasPinsForHost(hostname: String): Boolean {
    return pins.any { pin ->
      matchesHostname(pin.hostname, hostname)
    }
  }

  /**
   * Match hostname against pattern, supporting wildcard prefixes.
   *
   * - "api.example.com" matches "api.example.com"
   * - "*.example.com" matches "api.example.com" but not "a.b.example.com"
   */
  private fun matchesHostname(pattern: String, hostname: String): Boolean {
    if (pattern == hostname) return true

    if (pattern.startsWith("*.")) {
      val suffix = pattern.substring(1) // ".example.com"
      // Hostname must end with suffix and have exactly one label before it
      if (hostname.endsWith(suffix)) {
        val prefix = hostname.dropLast(suffix.length)
        return prefix.isNotEmpty() && !prefix.contains('.')
      }
    }

    return false
  }

  companion object {
    /**
     * Create an interceptor from a TransportSecurityPolicy.
     * Returns null if no certificate pins are configured.
     */
    fun fromPolicy(
      policy: TransportSecurityPolicy,
      onPinValidationFailure: ((String, String) -> Unit)? = null,
    ): CertificatePinningInterceptor? {
      if (policy.certificatePins.isEmpty()) return null

      return CertificatePinningInterceptor(
        pins = policy.certificatePins,
        onPinValidationFailure = onPinValidationFailure,
      )
    }
  }
}

/**
 * Exception thrown when certificate pinning validation fails.
 */
class CertificatePinningException(
  message: String,
  cause: Throwable? = null,
) : IOException(message, cause)
