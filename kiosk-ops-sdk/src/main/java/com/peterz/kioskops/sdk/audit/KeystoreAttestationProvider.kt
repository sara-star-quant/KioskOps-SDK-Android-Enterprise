/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.audit

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import javax.crypto.SecretKeyFactory

/**
 * DeviceAttestationProvider implementation using Android Keystore.
 *
 * Creates an ECDSA P-256 signing key with key attestation.
 * The key is hardware-backed when the device supports it.
 *
 * @property context Android context.
 * @property keyAlias Alias for the signing key in Keystore.
 */
class KeystoreAttestationProvider(
  private val context: Context,
  private val keyAlias: String = "kioskops_audit_signing",
) : DeviceAttestationProvider {

  private val keyStore: KeyStore by lazy {
    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  }

  private var cachedAttestationChain: List<X509Certificate>? = null

  override val isHardwareBacked: Boolean
    get() = checkHardwareBacked()

  override fun signAuditEntry(payload: String): String? {
    return try {
      val privateKey = getOrCreateSigningKey()
      val payloadBytes = payload.toByteArray(Charsets.UTF_8)

      val signature = Signature.getInstance("SHA256withECDSA")
      signature.initSign(privateKey)
      signature.update(payloadBytes)
      val signatureBytes = signature.sign()

      Base64.getEncoder().encodeToString(signatureBytes)
    } catch (e: Exception) {
      null
    }
  }

  override fun verifySignature(payload: String, signature: String): Boolean {
    return try {
      val publicKey = keyStore.getCertificate(keyAlias)?.publicKey ?: return false
      val payloadBytes = payload.toByteArray(Charsets.UTF_8)
      val signatureBytes = Base64.getDecoder().decode(signature)

      val sig = Signature.getInstance("SHA256withECDSA")
      sig.initVerify(publicKey)
      sig.update(payloadBytes)
      sig.verify(signatureBytes)
    } catch (e: Exception) {
      false
    }
  }

  override fun getAttestationChain(): List<X509Certificate>? {
    if (cachedAttestationChain != null) {
      return cachedAttestationChain
    }

    return try {
      // Ensure key exists
      getOrCreateSigningKey()

      val chain = keyStore.getCertificateChain(keyAlias)
        ?.filterIsInstance<X509Certificate>()

      if (!chain.isNullOrEmpty()) {
        cachedAttestationChain = chain
      }

      chain
    } catch (e: Exception) {
      null
    }
  }

  override fun getAttestationBlob(): ByteArray? {
    val chain = getAttestationChain() ?: return null
    if (chain.isEmpty()) return null

    return try {
      val baos = ByteArrayOutputStream()
      val dos = DataOutputStream(baos)

      // Write number of certificates
      dos.writeInt(chain.size)

      // Write each certificate
      for (cert in chain) {
        val encoded = cert.encoded
        dos.writeInt(encoded.size)
        dos.write(encoded)
      }

      dos.flush()
      baos.toByteArray()
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Parse an attestation blob back into a certificate chain.
   */
  fun parseAttestationBlob(blob: ByteArray): List<X509Certificate>? {
    return try {
      val factory = CertificateFactory.getInstance("X.509")
      val certificates = mutableListOf<X509Certificate>()

      var offset = 0

      // Read number of certificates
      val count = ((blob[offset].toInt() and 0xFF) shl 24) or
        ((blob[offset + 1].toInt() and 0xFF) shl 16) or
        ((blob[offset + 2].toInt() and 0xFF) shl 8) or
        (blob[offset + 3].toInt() and 0xFF)
      offset += 4

      repeat(count) {
        // Read certificate length
        val length = ((blob[offset].toInt() and 0xFF) shl 24) or
          ((blob[offset + 1].toInt() and 0xFF) shl 16) or
          ((blob[offset + 2].toInt() and 0xFF) shl 8) or
          (blob[offset + 3].toInt() and 0xFF)
        offset += 4

        // Read certificate bytes
        val certBytes = blob.copyOfRange(offset, offset + length)
        offset += length

        val cert = factory.generateCertificate(certBytes.inputStream()) as X509Certificate
        certificates.add(cert)
      }

      certificates
    } catch (e: Exception) {
      null
    }
  }

  private fun getOrCreateSigningKey(): PrivateKey {
    // Check if key already exists
    val existingKey = keyStore.getKey(keyAlias, null) as? PrivateKey
    if (existingKey != null) {
      return existingKey
    }

    // Create new key pair with attestation
    val keyPairGenerator = KeyPairGenerator.getInstance(
      KeyProperties.KEY_ALGORITHM_EC,
      "AndroidKeyStore"
    )

    val specBuilder = KeyGenParameterSpec.Builder(
      keyAlias,
      KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
    )
      .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
      .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))

    // Request attestation on API 24+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      // Use a device-specific challenge for attestation
      val challenge = generateAttestationChallenge()
      specBuilder.setAttestationChallenge(challenge)
    }

    keyPairGenerator.initialize(specBuilder.build())
    val keyPair = keyPairGenerator.generateKeyPair()

    return keyPair.private
  }

  private fun generateAttestationChallenge(): ByteArray {
    // Generate a challenge that includes device-specific info
    // This helps verify the attestation came from this device
    val deviceInfo = "${Build.MODEL}:${Build.MANUFACTURER}:${System.currentTimeMillis()}"
    return deviceInfo.toByteArray(Charsets.UTF_8).copyOf(32)
  }

  private fun checkHardwareBacked(): Boolean {
    return try {
      val key = keyStore.getKey(keyAlias, null) ?: return false

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val factory = java.security.KeyFactory.getInstance(
          key.algorithm,
          "AndroidKeyStore"
        )
        val keyInfo = factory.getKeySpec(key, KeyInfo::class.java)
        keyInfo.isInsideSecureHardware
      } else {
        false
      }
    } catch (e: Exception) {
      false
    }
  }

  companion object {
    /**
     * Check if the device supports hardware-backed attestation.
     */
    fun isAttestationSupported(context: Context): Boolean {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        return false
      }

      return try {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val testAlias = "kioskops_attestation_support_test"

        val keyPairGenerator = KeyPairGenerator.getInstance(
          KeyProperties.KEY_ALGORITHM_EC,
          "AndroidKeyStore"
        )

        val spec = KeyGenParameterSpec.Builder(
          testAlias,
          KeyProperties.PURPOSE_SIGN
        )
          .setDigests(KeyProperties.DIGEST_SHA256)
          .setAttestationChallenge(ByteArray(32))
          .build()

        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()

        val chain = keyStore.getCertificateChain(testAlias)
        val supported = chain != null && chain.size > 1

        // Cleanup
        keyStore.deleteEntry(testAlias)

        supported
      } catch (e: Exception) {
        false
      }
    }
  }
}
