/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.crypto

import java.security.cert.X509Certificate

/**
 * Security level of a cryptographic key.
 *
 * Indicates where the key is stored and processed.
 */
enum class SecurityLevel {
  /**
   * Key is stored in software only.
   * Less secure but available on all devices.
   */
  SOFTWARE,

  /**
   * Key is stored in Trusted Execution Environment.
   * Hardware-backed security, widely available.
   */
  TEE,

  /**
   * Key is stored in StrongBox (dedicated security chip).
   * Highest security level, available on newer devices.
   */
  STRONGBOX,

  /**
   * Security level could not be determined.
   */
  UNKNOWN,
}

/**
 * Attestation status for a cryptographic key.
 *
 * Provides information about key storage, security properties,
 * and optional attestation certificate chain for remote verification.
 *
 * @property isHardwareBacked True if key is stored in secure hardware.
 * @property securityLevel The security level of key storage.
 * @property keyCreatedAt Timestamp when the key was created (epoch ms), if known.
 * @property attestationChain Certificate chain for remote attestation, if available.
 *           The chain starts with the attestation certificate and ends at a root CA.
 * @property keyAlias The key alias in the Keystore.
 * @property keyAlgorithm The key algorithm (e.g., "AES", "EC").
 * @property keySize The key size in bits.
 */
data class KeyAttestationStatus(
  val isHardwareBacked: Boolean,
  val securityLevel: SecurityLevel,
  val keyCreatedAt: Long?,
  val attestationChain: List<X509Certificate>?,
  val keyAlias: String,
  val keyAlgorithm: String?,
  val keySize: Int?,
) {
  /**
   * Check if this key has a valid attestation chain.
   */
  val hasAttestationChain: Boolean
    get() = !attestationChain.isNullOrEmpty()

  /**
   * Check if this key meets minimum security requirements.
   *
   * @param requireHardware If true, key must be hardware-backed.
   * @param minimumSecurityLevel Minimum required security level.
   */
  fun meetsRequirements(
    requireHardware: Boolean = false,
    minimumSecurityLevel: SecurityLevel = SecurityLevel.SOFTWARE,
  ): Boolean {
    if (requireHardware && !isHardwareBacked) return false

    val levelOrder = listOf(
      SecurityLevel.UNKNOWN,
      SecurityLevel.SOFTWARE,
      SecurityLevel.TEE,
      SecurityLevel.STRONGBOX,
    )

    val currentIndex = levelOrder.indexOf(securityLevel)
    val requiredIndex = levelOrder.indexOf(minimumSecurityLevel)

    return currentIndex >= requiredIndex
  }
}

/**
 * Response to an attestation challenge.
 *
 * Used for remote verification of key attestation.
 *
 * @property challenge The original challenge bytes.
 * @property attestationChain The certificate chain proving key attestation.
 * @property signature Signature over the challenge using the attested key.
 */
data class AttestationResponse(
  val challenge: ByteArray,
  val attestationChain: List<X509Certificate>,
  val signature: ByteArray,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AttestationResponse) return false
    return challenge.contentEquals(other.challenge) &&
      attestationChain == other.attestationChain &&
      signature.contentEquals(other.signature)
  }

  override fun hashCode(): Int {
    var result = challenge.contentHashCode()
    result = 31 * result + attestationChain.hashCode()
    result = 31 * result + signature.contentHashCode()
    return result
  }
}
