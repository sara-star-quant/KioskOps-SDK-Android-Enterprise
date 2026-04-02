/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.compliance

import java.security.Security

/**
 * Checks FIPS 140-2/3 mode availability at runtime.
 *
 * On devices with Conscrypt/BoringSSL compiled in FIPS mode (e.g., government-provisioned
 * devices), the JCE security provider reports FIPS compliance. This checker detects that state.
 *
 * SDK crypto operations that are FIPS-compliant when running on a FIPS-mode provider:
 * - AES-256-GCM (queue payload encryption, field-level encryption)
 * - ECDSA P-256 (config signature verification, audit entry signing)
 * - SHA-256 (hash chain, content hashing)
 * - HMAC-SHA256 (request signing)
 *
 * Operations NOT covered by FIPS scope:
 * - Android Keystore wrapping (hardware-backed, not software FIPS)
 * - TLS negotiation (delegated to platform, typically BoringSSL)
 *
 * @since 0.7.0
 */
object FipsComplianceChecker {

  /**
   * FIPS mode detection result.
   */
  data class FipsStatus(
    val isFipsMode: Boolean,
    val providerName: String?,
    val providerVersion: String?,
    val details: String,
  )

  /**
   * Check the current device for FIPS 140 mode.
   *
   * Inspects JCE security providers for Conscrypt with FIPS mode indicators.
   */
  fun check(): FipsStatus {
    val providers = Security.getProviders()

    for (provider in providers) {
      val name = provider.name ?: continue

      // Conscrypt is the default TLS/crypto provider on modern Android
      if (name.equals("Conscrypt", ignoreCase = true) ||
        name.equals("GmsCore_OpenSSL", ignoreCase = true)
      ) {
        val version = provider.version.toString()
        val isFips = detectFipsMode(provider)
        return FipsStatus(
          isFipsMode = isFips,
          providerName = name,
          providerVersion = version,
          details = if (isFips) {
            "Conscrypt provider detected in FIPS mode"
          } else {
            "Conscrypt provider detected; FIPS mode not active"
          },
        )
      }
    }

    return FipsStatus(
      isFipsMode = false,
      providerName = null,
      providerVersion = null,
      details = "No Conscrypt/BoringSSL provider found",
    )
  }

  private fun detectFipsMode(provider: java.security.Provider): Boolean {
    // Check for FIPS mode property (set by FIPS-certified Conscrypt builds)
    val fipsProp = provider.getProperty("FIPS") ?: provider.getProperty("fips.mode")
    if (fipsProp != null && fipsProp.equals("true", ignoreCase = true)) {
      return true
    }

    // Check provider info string for FIPS indicators
    val info = provider.info ?: ""
    return info.contains("FIPS", ignoreCase = true)
  }
}
