/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.cert.X509Certificate
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/**
 * Reports on key attestation status for compliance and security auditing.
 *
 * Provides information about whether keys are hardware-backed and
 * can generate attestation challenge responses for remote verification.
 *
 * Usage:
 * ```kotlin
 * val reporter = KeyAttestationReporter(context)
 *
 * // Check key security properties
 * val status = reporter.getAttestationStatus("my_key_alias")
 * if (status.isHardwareBacked) {
 *     // Key is protected by hardware
 * }
 *
 * // Generate attestation for remote verification
 * val challenge = serverProvidedChallenge
 * val response = reporter.generateAttestationChallengeResponse("attestation_key", challenge)
 * // Send response to server for verification
 * ```
 *
 * @property context Android context for Keystore access.
 */
class KeyAttestationReporter(
  private val context: Context,
) {

  private val keyStore: KeyStore by lazy {
    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  }

  /**
   * Get attestation status for a symmetric key (AES).
   *
   * @param alias The key alias in Android Keystore.
   * @return Attestation status, or null if key not found.
   */
  fun getAttestationStatus(alias: String): KeyAttestationStatus? {
    val key = keyStore.getKey(alias, null) as? SecretKey ?: return null

    return try {
      val factory = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
      val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo

      KeyAttestationStatus(
        isHardwareBacked = keyInfo.isInsideSecureHardware,
        securityLevel = determineSecurityLevel(keyInfo),
        keyCreatedAt = getKeyCreationTime(keyInfo),
        attestationChain = null, // Symmetric keys don't have attestation chains
        keyAlias = alias,
        keyAlgorithm = key.algorithm,
        keySize = 256, // Assume 256-bit AES
      )
    } catch (e: Exception) {
      // Key exists but we can't get info
      KeyAttestationStatus(
        isHardwareBacked = false,
        securityLevel = SecurityLevel.UNKNOWN,
        keyCreatedAt = null,
        attestationChain = null,
        keyAlias = alias,
        keyAlgorithm = key.algorithm,
        keySize = null,
      )
    }
  }

  /**
   * Generate an attestation challenge response.
   *
   * Creates a new attestation key (if needed), signs the challenge,
   * and returns the attestation certificate chain for verification.
   *
   * @param alias Alias for the attestation key.
   * @param challenge Server-provided challenge bytes.
   * @return Attestation response, or null if attestation not supported.
   */
  fun generateAttestationChallengeResponse(
    alias: String,
    challenge: ByteArray,
  ): AttestationResponse? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      return null // Key attestation requires API 24+
    }

    return try {
      // Create or get attestation key pair
      val attestationAlias = "${alias}_attestation"
      val keyPair = getOrCreateAttestationKeyPair(attestationAlias, challenge)

      // Get certificate chain
      val chain = keyStore.getCertificateChain(attestationAlias)
        ?.filterIsInstance<X509Certificate>()
        ?: return null

      if (chain.isEmpty()) return null

      // Sign the challenge
      val signature = Signature.getInstance("SHA256withECDSA")
      signature.initSign(keyPair.private)
      signature.update(challenge)
      val signatureBytes = signature.sign()

      AttestationResponse(
        challenge = challenge,
        attestationChain = chain,
        signature = signatureBytes,
      )
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Get attestation status for all SDK keys.
   *
   * @param keyAliases List of key aliases to check.
   * @return Map of alias to attestation status.
   */
  fun getAttestationStatusForKeys(keyAliases: List<String>): Map<String, KeyAttestationStatus?> {
    return keyAliases.associateWith { getAttestationStatus(it) }
  }

  /**
   * Check if the device supports hardware-backed key attestation.
   */
  fun isHardwareAttestationSupported(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

    return try {
      // Try to create an attestation key
      val testAlias = "kioskops_attestation_test"
      val keyPairGenerator = KeyPairGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_EC,
        "AndroidKeyStore"
      )

      val spec = KeyGenParameterSpec.Builder(
        testAlias,
        KeyProperties.PURPOSE_SIGN
      )
        .setDigests(KeyProperties.DIGEST_SHA256)
        .setAttestationChallenge(ByteArray(32)) // Dummy challenge
        .build()

      keyPairGenerator.initialize(spec)
      keyPairGenerator.generateKeyPair()

      // Check if we got an attestation chain
      val chain = keyStore.getCertificateChain(testAlias)
      val supported = chain != null && chain.size > 1

      // Cleanup test key
      keyStore.deleteEntry(testAlias)

      supported
    } catch (e: Exception) {
      false
    }
  }

  private fun getOrCreateAttestationKeyPair(
    alias: String,
    challenge: ByteArray,
  ): java.security.KeyPair {
    // Check if key already exists
    if (keyStore.containsAlias(alias)) {
      val privateKey = keyStore.getKey(alias, null) as? java.security.PrivateKey
      val publicKey = keyStore.getCertificate(alias)?.publicKey
      if (privateKey != null && publicKey != null) {
        return java.security.KeyPair(publicKey, privateKey)
      }
    }

    // Create new attestation key pair
    val keyPairGenerator = KeyPairGenerator.getInstance(
      KeyProperties.KEY_ALGORITHM_EC,
      "AndroidKeyStore"
    )

    val specBuilder = KeyGenParameterSpec.Builder(
      alias,
      KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
    )
      .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
      .setAttestationChallenge(challenge)

    keyPairGenerator.initialize(specBuilder.build())
    return keyPairGenerator.generateKeyPair()
  }

  private fun determineSecurityLevel(keyInfo: KeyInfo): SecurityLevel {
    return when {
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        when (keyInfo.securityLevel) {
          KeyProperties.SECURITY_LEVEL_STRONGBOX -> SecurityLevel.STRONGBOX
          KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> SecurityLevel.TEE
          KeyProperties.SECURITY_LEVEL_SOFTWARE -> SecurityLevel.SOFTWARE
          else -> SecurityLevel.UNKNOWN
        }
      }
      keyInfo.isInsideSecureHardware -> SecurityLevel.TEE
      else -> SecurityLevel.SOFTWARE
    }
  }

  private fun getKeyCreationTime(keyInfo: KeyInfo): Long? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      try {
        keyInfo.keyValidityStart?.time
      } catch (e: Exception) {
        null
      }
    } else {
      null
    }
  }
}
