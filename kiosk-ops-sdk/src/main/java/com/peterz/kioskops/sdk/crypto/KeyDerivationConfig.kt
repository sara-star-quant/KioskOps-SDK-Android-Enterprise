/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.crypto

/**
 * Configuration for password-based key derivation.
 *
 * These parameters control the PBKDF2 (or similar) key derivation used
 * for deriving cryptographic keys from passwords or passphrases.
 *
 * The default values follow OWASP 2023 recommendations for PBKDF2-HMAC-SHA256.
 *
 * @property algorithm The key derivation algorithm. Supported values:
 *           - "PBKDF2WithHmacSHA256" (recommended, default)
 *           - "PBKDF2WithHmacSHA512"
 *           - "PBKDF2WithHmacSHA1" (legacy, not recommended)
 * @property iterationCount Number of iterations for the derivation function.
 *           Higher values increase security but also computation time.
 *           OWASP 2023 recommends 310,000 for SHA-256.
 * @property saltLengthBytes Length of the random salt in bytes.
 *           NIST recommends at least 16 bytes; we default to 32.
 * @property keyLengthBits Length of the derived key in bits.
 *           256 bits is standard for AES-256.
 */
data class KeyDerivationConfig(
  val algorithm: String = "PBKDF2WithHmacSHA256",
  val iterationCount: Int = 310_000,
  val saltLengthBytes: Int = 32,
  val keyLengthBits: Int = 256,
) {
  init {
    require(iterationCount > 0) { "iterationCount must be positive" }
    require(saltLengthBytes >= 16) { "saltLengthBytes must be at least 16" }
    require(keyLengthBits in listOf(128, 192, 256)) { "keyLengthBits must be 128, 192, or 256" }
  }

  companion object {
    /**
     * Default configuration following OWASP 2023 recommendations.
     */
    fun default() = KeyDerivationConfig()

    /**
     * Fast configuration for testing.
     * DO NOT use in production.
     */
    fun fastForTesting() = KeyDerivationConfig(
      iterationCount = 1000,
      saltLengthBytes = 16,
    )

    /**
     * Legacy configuration for migration from older systems.
     * Not recommended for new deployments.
     */
    fun legacy() = KeyDerivationConfig(
      algorithm = "PBKDF2WithHmacSHA1",
      iterationCount = 100_000,
      saltLengthBytes = 16,
      keyLengthBits = 256,
    )

    /**
     * High-security configuration for sensitive environments.
     * Significantly slower but provides maximum security margin.
     */
    fun highSecurity() = KeyDerivationConfig(
      algorithm = "PBKDF2WithHmacSHA512",
      iterationCount = 600_000,
      saltLengthBytes = 32,
      keyLengthBits = 256,
    )
  }
}
