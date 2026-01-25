/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Crypto provider with key versioning and rotation support.
 *
 * Manages multiple key versions to support:
 * - Key rotation without data loss
 * - Backward-compatible decryption of old data
 * - Gradual migration to new keys
 *
 * Blob format (backward compatible):
 * ```
 * [version:1 byte] - Key version (1-255)
 * [ivLen:1 byte]   - IV length (12-32)
 * [iv:N bytes]     - Initialization vector
 * [ciphertext]     - Encrypted data with GCM tag
 * ```
 *
 * @property context Android context for metadata storage.
 * @property baseAlias Base alias for key family in Android Keystore.
 * @property rotationPolicy Policy controlling when rotation occurs.
 * @property clock Time provider for testability.
 */
class VersionedCryptoProvider(
  private val context: Context,
  private val baseAlias: String = "kioskops_versioned",
  private val rotationPolicy: KeyRotationPolicy = KeyRotationPolicy.default(),
  private val clock: () -> Long = { System.currentTimeMillis() },
) : CryptoProvider {

  override val isEnabled: Boolean = true

  private val metadataStore = KeyMetadataStore(context, baseAlias)

  init {
    metadataStore.initializeIfNeeded(clock())
  }

  /**
   * Get the current key version.
   */
  fun currentVersion(): Int = metadataStore.getCurrentVersion()

  /**
   * Check if the current key should be rotated based on policy.
   *
   * @return True if key age exceeds [KeyRotationPolicy.maxKeyAgeDays].
   */
  fun shouldRotate(): Boolean {
    if (rotationPolicy.maxKeyAgeDays <= 0) return false

    val ageDays = metadataStore.getCurrentKeyAgeDays(clock()) ?: return false
    return ageDays >= rotationPolicy.maxKeyAgeDays
  }

  /**
   * Rotate to a new key version.
   *
   * Creates a new key and sets it as the current version.
   * Old keys remain available for decryption until they expire.
   *
   * @param force If true, rotate even if not needed by policy.
   * @return Result of the rotation operation.
   */
  fun rotateKey(force: Boolean = false): RotationResult {
    val currentVersion = currentVersion()
    val ageDays = metadataStore.getCurrentKeyAgeDays(clock())

    if (!force && !shouldRotate()) {
      return RotationResult.NotNeeded(
        currentKeyVersion = currentVersion,
        keyAgeDays = ageDays ?: 0,
      )
    }

    return try {
      val newVersion = currentVersion + 1

      // Generate new key in Keystore
      createKeyForVersion(newVersion)

      // Store metadata
      metadataStore.setKeyMetadata(
        version = newVersion,
        metadata = KeyMetadata(
          version = newVersion,
          createdAtMs = clock(),
          algorithm = "AES/GCM/NoPadding",
          keyLengthBits = 256,
          rotatedFromVersion = currentVersion,
          isHardwareBacked = isKeyHardwareBacked(newVersion),
        )
      )

      // Update current version
      metadataStore.setCurrentVersion(newVersion)

      // Cleanup old versions if needed
      cleanupOldVersions()

      RotationResult.Success(
        newKeyVersion = newVersion,
        previousKeyVersion = currentVersion,
      )
    } catch (e: Exception) {
      RotationResult.Failed(
        reason = "Failed to create new key: ${e.message}",
        cause = e,
      )
    }
  }

  /**
   * Re-encrypt data with the current key version.
   *
   * Use this to migrate old encrypted data to the current key
   * after rotation.
   *
   * @param blob Previously encrypted blob.
   * @return Blob encrypted with the current key version.
   */
  fun reencryptWithCurrentKey(blob: ByteArray): ByteArray {
    val plaintext = decrypt(blob)
    return encrypt(plaintext)
  }

  override fun encrypt(plain: ByteArray): ByteArray {
    val version = currentVersion()
    val key = getOrCreateKeyForVersion(version)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)

    val iv = cipher.iv
    val ciphertext = cipher.doFinal(plain)

    // Build versioned blob
    val out = ByteArray(2 + iv.size + ciphertext.size)
    out[0] = version.toByte()
    out[1] = iv.size.toByte()
    System.arraycopy(iv, 0, out, 2, iv.size)
    System.arraycopy(ciphertext, 0, out, 2 + iv.size, ciphertext.size)

    return out
  }

  override fun decrypt(blob: ByteArray): ByteArray {
    require(blob.size >= 2) { "Blob too short" }

    val version = blob[0].toInt() and 0xFF
    require(version in 1..255) { "Invalid key version: $version" }

    val ivLen = blob[1].toInt() and 0xFF
    require(ivLen in 12..32) { "Invalid IV length: $ivLen" }

    val ivStart = 2
    val ctStart = ivStart + ivLen
    require(ctStart <= blob.size) { "Malformed blob" }

    val iv = blob.copyOfRange(ivStart, ctStart)
    val ciphertext = blob.copyOfRange(ctStart, blob.size)

    // Get key for the version in the blob
    val key = getKeyForVersion(version)
      ?: throw IllegalStateException("Key version $version not available")

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))

    return cipher.doFinal(ciphertext)
  }

  /**
   * Get metadata for all key versions.
   */
  fun getAllKeyMetadata(): List<KeyMetadata> {
    return metadataStore.getAllVersions()
      .mapNotNull { metadataStore.getKeyMetadata(it) }
  }

  /**
   * Get metadata for the current key.
   */
  fun getCurrentKeyMetadata(): KeyMetadata? = metadataStore.getCurrentKeyMetadata()

  private fun aliasForVersion(version: Int): String {
    return "${baseAlias}_v$version"
  }

  private fun getOrCreateKeyForVersion(version: Int): SecretKey {
    return getKeyForVersion(version) ?: createKeyForVersion(version)
  }

  private fun getKeyForVersion(version: Int): SecretKey? {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    return keyStore.getKey(aliasForVersion(version), null) as? SecretKey
  }

  private fun createKeyForVersion(version: Int): SecretKey {
    val alias = aliasForVersion(version)

    val generator = KeyGenerator.getInstance(
      KeyProperties.KEY_ALGORITHM_AES,
      "AndroidKeyStore"
    )

    val spec = KeyGenParameterSpec.Builder(
      alias,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .build()

    generator.init(spec)
    return generator.generateKey()
  }

  private fun isKeyHardwareBacked(version: Int): Boolean {
    val key = getKeyForVersion(version) ?: return false

    return try {
      val factory = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
      val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
      keyInfo.isInsideSecureHardware
    } catch (e: Exception) {
      false
    }
  }

  private fun cleanupOldVersions() {
    if (rotationPolicy.maxKeyVersions <= 0) return

    val allVersions = metadataStore.getAllVersions()
    if (allVersions.size <= rotationPolicy.maxKeyVersions) return

    val currentVersion = currentVersion()
    val cutoffMs = clock() - rotationPolicy.retainOldKeysForDays.toLong() * 24 * 60 * 60 * 1000

    val versionsToDelete = allVersions
      .filter { it != currentVersion }
      .filter { version ->
        val metadata = metadataStore.getKeyMetadata(version)
        metadata != null && metadata.createdAtMs < cutoffMs
      }
      .take(allVersions.size - rotationPolicy.maxKeyVersions)

    for (version in versionsToDelete) {
      deleteKeyVersion(version)
    }
  }

  private fun deleteKeyVersion(version: Int) {
    // Delete from Keystore
    try {
      val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
      keyStore.deleteEntry(aliasForVersion(version))
    } catch (e: Exception) {
      // Ignore deletion errors
    }

    // Delete metadata
    metadataStore.deleteKeyMetadata(version)
  }
}
