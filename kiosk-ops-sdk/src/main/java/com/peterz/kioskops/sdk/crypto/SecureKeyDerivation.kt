/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.crypto

import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Secure key derivation using PBKDF2.
 *
 * This class provides password-based key derivation with configurable
 * parameters for compliance with various security standards.
 *
 * Usage:
 * ```kotlin
 * val derivation = SecureKeyDerivation(KeyDerivationConfig.default())
 * val (key, salt) = derivation.deriveKey("password")
 *
 * // Later, to derive the same key:
 * val sameKey = derivation.deriveKeyWithSalt("password", salt)
 * ```
 */
class SecureKeyDerivation(
  private val config: KeyDerivationConfig = KeyDerivationConfig.default(),
) {

  private val secureRandom = SecureRandom()

  /**
   * Derive a cryptographic key from a password.
   *
   * Generates a new random salt and derives a key using PBKDF2.
   *
   * @param password The password or passphrase to derive from.
   * @return A pair of (derived key, salt). The salt must be stored
   *         alongside encrypted data for later decryption.
   */
  fun deriveKey(password: CharArray): DerivedKeyResult {
    val salt = ByteArray(config.saltLengthBytes)
    secureRandom.nextBytes(salt)

    val key = deriveKeyWithSalt(password, salt)

    return DerivedKeyResult(
      key = key,
      salt = salt,
      algorithm = config.algorithm,
      iterationCount = config.iterationCount,
    )
  }

  /**
   * Derive a cryptographic key from a password using an existing salt.
   *
   * Use this method when decrypting data that was encrypted with a
   * previously derived key.
   *
   * @param password The password or passphrase to derive from.
   * @param salt The salt used during the original derivation.
   * @return The derived key as a SecretKey.
   */
  fun deriveKeyWithSalt(password: CharArray, salt: ByteArray): SecretKey {
    val spec = PBEKeySpec(
      password,
      salt,
      config.iterationCount,
      config.keyLengthBits,
    )

    return try {
      val factory = SecretKeyFactory.getInstance(config.algorithm)
      val keyBytes = factory.generateSecret(spec).encoded

      // Convert to AES key for use with ciphers
      SecretKeySpec(keyBytes, "AES")
    } finally {
      spec.clearPassword()
    }
  }

  /**
   * Derive a key from a string password (convenience method).
   *
   * The password string is immediately converted to a char array and
   * the conversion is not cleared from memory. For maximum security,
   * use the CharArray overload directly.
   */
  fun deriveKey(password: String): DerivedKeyResult {
    return deriveKey(password.toCharArray())
  }

  /**
   * Derive a key from a string password with existing salt.
   */
  fun deriveKeyWithSalt(password: String, salt: ByteArray): SecretKey {
    return deriveKeyWithSalt(password.toCharArray(), salt)
  }

  /**
   * Derive a deterministic key from input material.
   *
   * This is used for deterministic idempotency key generation where
   * the same input should always produce the same output.
   *
   * @param inputMaterial The input bytes to derive from.
   * @param context A context string to domain-separate the derivation.
   * @param salt Fixed salt for deterministic output.
   * @return The derived key bytes.
   */
  fun deriveDeterministic(
    inputMaterial: ByteArray,
    context: String,
    salt: ByteArray,
  ): ByteArray {
    // Combine input material with context for domain separation
    val combined = inputMaterial + context.toByteArray(Charsets.UTF_8)

    val spec = PBEKeySpec(
      String(combined, Charsets.ISO_8859_1).toCharArray(),
      salt,
      config.iterationCount,
      config.keyLengthBits,
    )

    return try {
      val factory = SecretKeyFactory.getInstance(config.algorithm)
      factory.generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }
}

/**
 * Result of a key derivation operation.
 *
 * @property key The derived cryptographic key.
 * @property salt The random salt used during derivation. Must be stored
 *           alongside encrypted data.
 * @property algorithm The algorithm used for derivation.
 * @property iterationCount The number of iterations used.
 */
data class DerivedKeyResult(
  val key: SecretKey,
  val salt: ByteArray,
  val algorithm: String,
  val iterationCount: Int,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DerivedKeyResult) return false
    return key == other.key &&
      salt.contentEquals(other.salt) &&
      algorithm == other.algorithm &&
      iterationCount == other.iterationCount
  }

  override fun hashCode(): Int {
    var result = key.hashCode()
    result = 31 * result + salt.contentHashCode()
    result = 31 * result + algorithm.hashCode()
    result = 31 * result + iterationCount
    return result
  }
}
