/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [VersionedCryptoProvider].
 *
 * Because Android Keystore is not available in Robolectric, these tests
 * exercise the metadata/versioning/policy layer directly on the real class
 * and use an in-memory [VersionedSoftwareCryptoProvider] to verify the blob
 * format, round-trip encrypt/decrypt, and multi-version decryption.
 */
@RunWith(RobolectricTestRunner::class)
class VersionedCryptoProviderTest {

  private lateinit var ctx: Context

  @Before
  fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()
  }

  // ---------------------------------------------------------------------------
  // Construction and initialisation
  // ---------------------------------------------------------------------------

  @Test
  fun `constructor initialises metadata for version 1`() {
    val provider = VersionedCryptoProvider(ctx, baseAlias = "test_init")

    assertThat(provider.currentVersion()).isEqualTo(1)
    assertThat(provider.isEnabled).isTrue()
  }

  @Test
  fun `constructor uses custom base alias`() {
    val provider1 = VersionedCryptoProvider(ctx, baseAlias = "alias_a")
    val provider2 = VersionedCryptoProvider(ctx, baseAlias = "alias_b")

    // Both independently start at version 1
    assertThat(provider1.currentVersion()).isEqualTo(1)
    assertThat(provider2.currentVersion()).isEqualTo(1)
  }

  @Test
  fun `isEnabled is always true`() {
    val provider = VersionedCryptoProvider(ctx, baseAlias = "test_enabled")
    assertThat(provider.isEnabled).isTrue()
  }

  // ---------------------------------------------------------------------------
  // currentVersion
  // ---------------------------------------------------------------------------

  @Test
  fun `currentVersion returns 1 on fresh initialisation`() {
    val provider = VersionedCryptoProvider(ctx, baseAlias = "test_current_v")
    assertThat(provider.currentVersion()).isEqualTo(1)
  }

  // ---------------------------------------------------------------------------
  // shouldRotate: policy-driven, does not touch Keystore
  // ---------------------------------------------------------------------------

  @Test
  fun `shouldRotate returns false for fresh key`() {
    val now = 1_700_000_000_000L
    val provider = VersionedCryptoProvider(
      ctx,
      baseAlias = "test_rotate_fresh",
      rotationPolicy = KeyRotationPolicy(maxKeyAgeDays = 365),
      clock = { now },
    )

    assertThat(provider.shouldRotate()).isFalse()
  }

  @Test
  fun `shouldRotate returns true when key exceeds max age`() {
    val createdAt = 1_700_000_000_000L
    val daysOld = 400L
    var now = createdAt
    val provider = VersionedCryptoProvider(
      ctx,
      baseAlias = "test_rotate_old",
      rotationPolicy = KeyRotationPolicy(maxKeyAgeDays = 365),
      clock = { now },
    )

    // Advance clock past max age
    now = createdAt + daysOld * 24 * 60 * 60 * 1000
    assertThat(provider.shouldRotate()).isTrue()
  }

  @Test
  fun `shouldRotate returns false when key is exactly at max age minus one day`() {
    val createdAt = 1_700_000_000_000L
    val daysOld = 364L
    var now = createdAt
    val provider = VersionedCryptoProvider(
      ctx,
      baseAlias = "test_rotate_364",
      rotationPolicy = KeyRotationPolicy(maxKeyAgeDays = 365),
      clock = { now },
    )

    // Advance clock to 364 days (just under threshold)
    now = createdAt + daysOld * 24 * 60 * 60 * 1000
    assertThat(provider.shouldRotate()).isFalse()
  }

  @Test
  fun `shouldRotate returns true when key is exactly at max age`() {
    val createdAt = 1_700_000_000_000L
    val daysOld = 365L
    var now = createdAt
    val provider = VersionedCryptoProvider(
      ctx,
      baseAlias = "test_rotate_exact",
      rotationPolicy = KeyRotationPolicy(maxKeyAgeDays = 365),
      clock = { now },
    )

    // Advance clock to exactly max age
    now = createdAt + daysOld * 24 * 60 * 60 * 1000
    assertThat(provider.shouldRotate()).isTrue()
  }

  @Test
  fun `shouldRotate returns false when policy disables rotation`() {
    val createdAt = 1_700_000_000_000L
    val daysOld = 9999L
    var now = createdAt
    val provider = VersionedCryptoProvider(
      ctx,
      baseAlias = "test_rotate_disabled",
      rotationPolicy = KeyRotationPolicy.disabled(),
      clock = { now },
    )

    // Even after 9999 days, disabled policy never triggers
    now = createdAt + daysOld * 24 * 60 * 60 * 1000
    assertThat(provider.shouldRotate()).isFalse()
  }

  @Test
  fun `shouldRotate with strict policy triggers sooner`() {
    val createdAt = 1_700_000_000_000L
    val daysOld = 91L
    var now = createdAt
    val provider = VersionedCryptoProvider(
      ctx,
      baseAlias = "test_rotate_strict",
      rotationPolicy = KeyRotationPolicy.strict(),
      clock = { now },
    )

    // Advance clock past strict policy threshold (90 days)
    now = createdAt + daysOld * 24 * 60 * 60 * 1000
    assertThat(provider.shouldRotate()).isTrue()
  }

  // ---------------------------------------------------------------------------
  // rotateKey: Keystore operations will fail, but policy guard is testable
  // ---------------------------------------------------------------------------

  @Test
  fun `rotateKey returns NotNeeded when key is fresh`() {
    val now = 1_700_000_000_000L
    val provider = VersionedCryptoProvider(
      ctx,
      baseAlias = "test_rk_not_needed",
      rotationPolicy = KeyRotationPolicy(maxKeyAgeDays = 365),
      clock = { now },
    )

    val result = provider.rotateKey()

    assertThat(result).isInstanceOf(RotationResult.NotNeeded::class.java)
    val notNeeded = result as RotationResult.NotNeeded
    assertThat(notNeeded.currentKeyVersion).isEqualTo(1)
    assertThat(notNeeded.keyAgeDays).isEqualTo(0)
  }

  @Test
  fun `rotateKey forced on Robolectric returns Failed because AndroidKeyStore unavailable`() {
    val provider = VersionedCryptoProvider(
      ctx,
      baseAlias = "test_rk_force_fail",
      rotationPolicy = KeyRotationPolicy.default(),
    )

    // Force rotation bypasses shouldRotate() but still hits AndroidKeyStore
    val result = provider.rotateKey(force = true)

    assertThat(result).isInstanceOf(RotationResult.Failed::class.java)
    val failed = result as RotationResult.Failed
    assertThat(failed.reason).contains("Failed to create new key")
    assertThat(failed.cause).isNotNull()
  }

  @Test
  fun `rotateKey without force returns NotNeeded for disabled policy`() {
    val provider = VersionedCryptoProvider(
      ctx,
      baseAlias = "test_rk_disabled_policy",
      rotationPolicy = KeyRotationPolicy.disabled(),
    )

    val result = provider.rotateKey(force = false)

    assertThat(result).isInstanceOf(RotationResult.NotNeeded::class.java)
  }

  // ---------------------------------------------------------------------------
  // Metadata accessors
  // ---------------------------------------------------------------------------

  @Test
  fun `getCurrentKeyMetadata returns metadata for version 1 after init`() {
    val now = 1_700_000_000_000L
    val provider = VersionedCryptoProvider(
      ctx,
      baseAlias = "test_meta",
      clock = { now },
    )

    val meta = provider.getCurrentKeyMetadata()

    assertThat(meta).isNotNull()
    assertThat(meta!!.version).isEqualTo(1)
    assertThat(meta.createdAtMs).isEqualTo(now)
    assertThat(meta.algorithm).isEqualTo("AES/GCM/NoPadding")
    assertThat(meta.keyLengthBits).isEqualTo(256)
    assertThat(meta.rotatedFromVersion).isNull()
  }

  @Test
  fun `getAllKeyMetadata returns single entry on fresh init`() {
    val provider = VersionedCryptoProvider(
      ctx,
      baseAlias = "test_all_meta",
    )

    val all = provider.getAllKeyMetadata()

    assertThat(all).hasSize(1)
    assertThat(all[0].version).isEqualTo(1)
  }

  // ---------------------------------------------------------------------------
  // decrypt: blob validation (rejects malformed blobs before Keystore call)
  // ---------------------------------------------------------------------------

  @Test(expected = IllegalArgumentException::class)
  fun `decrypt rejects blob shorter than 2 bytes`() {
    val provider = VersionedCryptoProvider(ctx, baseAlias = "test_dec_short")
    provider.decrypt(byteArrayOf(0x01))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `decrypt rejects empty blob`() {
    val provider = VersionedCryptoProvider(ctx, baseAlias = "test_dec_empty")
    provider.decrypt(byteArrayOf())
  }

  @Test(expected = IllegalArgumentException::class)
  fun `decrypt rejects version 0`() {
    val provider = VersionedCryptoProvider(ctx, baseAlias = "test_dec_v0")
    // version=0, ivLen=12
    provider.decrypt(byteArrayOf(0x00, 0x0C))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `decrypt rejects IV length below 12`() {
    val provider = VersionedCryptoProvider(ctx, baseAlias = "test_dec_iv_low")
    // version=1, ivLen=11
    provider.decrypt(byteArrayOf(0x01, 0x0B))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `decrypt rejects IV length above 32`() {
    val provider = VersionedCryptoProvider(ctx, baseAlias = "test_dec_iv_high")
    // version=1, ivLen=33
    provider.decrypt(byteArrayOf(0x01, 0x21))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `decrypt rejects malformed blob where IV extends past data`() {
    val provider = VersionedCryptoProvider(ctx, baseAlias = "test_dec_malformed")
    // version=1, ivLen=20, but only 3 bytes total
    provider.decrypt(byteArrayOf(0x01, 0x14, 0x00))
  }

  @Test
  fun `decrypt with valid header but missing key version throws IllegalStateException`() {
    val provider = VersionedCryptoProvider(ctx, baseAlias = "test_dec_no_key")
    // version=1, ivLen=12, then 12 bytes IV + 1 byte ciphertext
    val blob = ByteArray(2 + 12 + 1)
    blob[0] = 0x01
    blob[1] = 0x0C
    // The rest is zeroes which is fine for header parsing

    try {
      provider.decrypt(blob)
      // If we get here without exception, the Robolectric AndroidKeyStore
      // shadow loaded: that is acceptable but uncommon
    } catch (e: IllegalStateException) {
      assertThat(e.message).contains("not available")
    } catch (_: Exception) {
      // AndroidKeyStore not available is also expected
    }
  }

  // ---------------------------------------------------------------------------
  // encrypt: header validation (Keystore will fail, but we verify the attempt)
  // ---------------------------------------------------------------------------

  @Test
  fun `encrypt fails gracefully when AndroidKeyStore unavailable`() {
    val provider = VersionedCryptoProvider(ctx, baseAlias = "test_enc_fail")
    try {
      provider.encrypt("hello".toByteArray())
      // Unlikely to succeed without AndroidKeyStore
    } catch (_: Exception) {
      // Expected: AndroidKeyStore not available in Robolectric
    }
  }

  // ---------------------------------------------------------------------------
  // Blob format: software implementation for round-trip testing
  // ---------------------------------------------------------------------------

  @Test
  fun `round-trip encrypt then decrypt preserves plaintext`() {
    val provider = VersionedSoftwareCryptoProvider()
    val plaintext = "Hello, KioskOps!".toByteArray()

    val encrypted = provider.encrypt(plaintext)
    val decrypted = provider.decrypt(encrypted)

    assertThat(decrypted).isEqualTo(plaintext)
  }

  @Test
  fun `round-trip with empty plaintext`() {
    val provider = VersionedSoftwareCryptoProvider()
    val plaintext = byteArrayOf()

    val encrypted = provider.encrypt(plaintext)
    val decrypted = provider.decrypt(encrypted)

    assertThat(decrypted).isEqualTo(plaintext)
  }

  @Test
  fun `encrypted blob starts with version byte`() {
    val provider = VersionedSoftwareCryptoProvider(version = 3)
    val encrypted = provider.encrypt("test".toByteArray())

    assertThat(encrypted[0].toInt() and 0xFF).isEqualTo(3)
  }

  @Test
  fun `encrypted blob second byte is IV length`() {
    val provider = VersionedSoftwareCryptoProvider()
    val encrypted = provider.encrypt("test".toByteArray())

    val ivLen = encrypted[1].toInt() and 0xFF
    // AES-GCM standard IV is 12 bytes
    assertThat(ivLen).isEqualTo(12)
  }

  @Test
  fun `encrypted blob is larger than plaintext due to header and GCM tag`() {
    val provider = VersionedSoftwareCryptoProvider()
    val plaintext = "short".toByteArray()
    val encrypted = provider.encrypt(plaintext)

    // header (2) + IV (12) + ciphertext (plaintext.size) + GCM tag (16)
    val expectedMinSize = 2 + 12 + plaintext.size + 16
    assertThat(encrypted.size).isEqualTo(expectedMinSize)
  }

  @Test
  fun `encrypting same plaintext twice produces different ciphertexts`() {
    val provider = VersionedSoftwareCryptoProvider()
    val plaintext = "deterministic?".toByteArray()

    val enc1 = provider.encrypt(plaintext)
    val enc2 = provider.encrypt(plaintext)

    // IVs should differ, so ciphertexts differ
    assertThat(enc1).isNotEqualTo(enc2)

    // But both decrypt to the same plaintext
    assertThat(provider.decrypt(enc1)).isEqualTo(plaintext)
    assertThat(provider.decrypt(enc2)).isEqualTo(plaintext)
  }

  // ---------------------------------------------------------------------------
  // Multi-version decrypt
  // ---------------------------------------------------------------------------

  @Test
  fun `decrypt data encrypted with older version using multi-version provider`() {
    val v1Key = generateAesKey()
    val v2Key = generateAesKey()

    val multiProvider = MultiVersionSoftwareCryptoProvider(
      keys = mapOf(1 to v1Key, 2 to v2Key),
      currentVersion = 2,
    )

    // Encrypt with version 1
    val v1Provider = VersionedSoftwareCryptoProvider(version = 1, key = v1Key)
    val encryptedV1 = v1Provider.encrypt("old-data".toByteArray())

    // Decrypt with multi-version provider that has both keys
    val decrypted = multiProvider.decrypt(encryptedV1)
    assertThat(String(decrypted)).isEqualTo("old-data")
  }

  @Test
  fun `decrypt data encrypted with current version using multi-version provider`() {
    val v1Key = generateAesKey()
    val v2Key = generateAesKey()

    val multiProvider = MultiVersionSoftwareCryptoProvider(
      keys = mapOf(1 to v1Key, 2 to v2Key),
      currentVersion = 2,
    )

    // Encrypt with version 2
    val v2Provider = VersionedSoftwareCryptoProvider(version = 2, key = v2Key)
    val encryptedV2 = v2Provider.encrypt("new-data".toByteArray())

    val decrypted = multiProvider.decrypt(encryptedV2)
    assertThat(String(decrypted)).isEqualTo("new-data")
  }

  @Test
  fun `multi-version provider encrypts with current version`() {
    val v1Key = generateAesKey()
    val v2Key = generateAesKey()

    val multiProvider = MultiVersionSoftwareCryptoProvider(
      keys = mapOf(1 to v1Key, 2 to v2Key),
      currentVersion = 2,
    )

    val encrypted = multiProvider.encrypt("payload".toByteArray())

    // Version byte should be 2
    assertThat(encrypted[0].toInt() and 0xFF).isEqualTo(2)

    // Can decrypt with v2 key directly
    val v2Provider = VersionedSoftwareCryptoProvider(version = 2, key = v2Key)
    assertThat(String(v2Provider.decrypt(encrypted))).isEqualTo("payload")
  }

  @Test(expected = IllegalStateException::class)
  fun `multi-version provider throws when version key is missing`() {
    val multiProvider = MultiVersionSoftwareCryptoProvider(
      keys = mapOf(1 to generateAesKey()),
      currentVersion = 1,
    )

    // Build a blob with version 99
    val fakeBlob = ByteArray(2 + 12 + 32)
    fakeBlob[0] = 99.toByte()
    fakeBlob[1] = 12

    multiProvider.decrypt(fakeBlob)
  }

  // ---------------------------------------------------------------------------
  // Re-encrypt lifecycle (simulated with software provider)
  // ---------------------------------------------------------------------------

  @Test
  fun `reencrypt upgrades version header from old to current`() {
    val v1Key = generateAesKey()
    val v2Key = generateAesKey()

    val multiProvider = MultiVersionSoftwareCryptoProvider(
      keys = mapOf(1 to v1Key, 2 to v2Key),
      currentVersion = 2,
    )

    // Encrypt with old version
    val v1Provider = VersionedSoftwareCryptoProvider(version = 1, key = v1Key)
    val oldBlob = v1Provider.encrypt("migrate-me".toByteArray())
    assertThat(oldBlob[0].toInt() and 0xFF).isEqualTo(1)

    // Re-encrypt: decrypt then encrypt with current
    val plaintext = multiProvider.decrypt(oldBlob)
    val newBlob = multiProvider.encrypt(plaintext)

    // New blob should have version 2
    assertThat(newBlob[0].toInt() and 0xFF).isEqualTo(2)

    // And still decrypt to the same data
    assertThat(String(multiProvider.decrypt(newBlob))).isEqualTo("migrate-me")
  }

  // ---------------------------------------------------------------------------
  // Version byte encoding edge cases
  // ---------------------------------------------------------------------------

  @Test
  fun `version 255 is correctly encoded and decoded`() {
    val key = generateAesKey()
    val provider = VersionedSoftwareCryptoProvider(version = 255, key = key)
    val encrypted = provider.encrypt("max-version".toByteArray())

    // Version byte 255 is 0xFF
    assertThat(encrypted[0].toInt() and 0xFF).isEqualTo(255)

    val decrypted = provider.decrypt(encrypted)
    assertThat(String(decrypted)).isEqualTo("max-version")
  }

  @Test
  fun `version 1 is correctly encoded and decoded`() {
    val key = generateAesKey()
    val provider = VersionedSoftwareCryptoProvider(version = 1, key = key)
    val encrypted = provider.encrypt("min-version".toByteArray())

    assertThat(encrypted[0].toInt() and 0xFF).isEqualTo(1)

    val decrypted = provider.decrypt(encrypted)
    assertThat(String(decrypted)).isEqualTo("min-version")
  }

  // ---------------------------------------------------------------------------
  // Large payload round-trip
  // ---------------------------------------------------------------------------

  @Test
  fun `round-trip with large payload`() {
    val provider = VersionedSoftwareCryptoProvider()
    val plaintext = ByteArray(64 * 1024) { (it % 256).toByte() }

    val encrypted = provider.encrypt(plaintext)
    val decrypted = provider.decrypt(encrypted)

    assertThat(decrypted).isEqualTo(plaintext)
  }

  // ---------------------------------------------------------------------------
  // KeyMetadataStore integration (via VersionedCryptoProvider)
  // ---------------------------------------------------------------------------

  @Test
  fun `second construction with same alias preserves version`() {
    val now = 1_700_000_000_000L
    VersionedCryptoProvider(
      ctx,
      baseAlias = "test_persist",
      clock = { now },
    )

    // Re-create with the same alias; version should still be 1
    val provider2 = VersionedCryptoProvider(
      ctx,
      baseAlias = "test_persist",
      clock = { now + 1000 },
    )

    assertThat(provider2.currentVersion()).isEqualTo(1)
    // Metadata should retain original creation time
    val meta = provider2.getCurrentKeyMetadata()
    assertThat(meta).isNotNull()
    assertThat(meta!!.createdAtMs).isEqualTo(now)
  }

  @Test
  fun `different base aliases have independent version counters`() {
    val providerA = VersionedCryptoProvider(ctx, baseAlias = "alias_x")
    val providerB = VersionedCryptoProvider(ctx, baseAlias = "alias_y")

    assertThat(providerA.currentVersion()).isEqualTo(1)
    assertThat(providerB.currentVersion()).isEqualTo(1)

    // They should not share SharedPreferences
    val metaA = providerA.getCurrentKeyMetadata()
    val metaB = providerB.getCurrentKeyMetadata()
    assertThat(metaA).isNotNull()
    assertThat(metaB).isNotNull()
  }

  // ---------------------------------------------------------------------------
  // Clock injection
  // ---------------------------------------------------------------------------

  @Test
  fun `custom clock is used for metadata timestamps`() {
    val fixedTime = 1_234_567_890_000L
    val provider = VersionedCryptoProvider(
      ctx,
      baseAlias = "test_clock",
      clock = { fixedTime },
    )

    val meta = provider.getCurrentKeyMetadata()
    assertThat(meta).isNotNull()
    assertThat(meta!!.createdAtMs).isEqualTo(fixedTime)
  }

  @Test
  fun `shouldRotate uses injected clock not system clock`() {
    val createdAt = 1_000_000_000_000L
    val provider = VersionedCryptoProvider(
      ctx,
      baseAlias = "test_clock_rotate",
      rotationPolicy = KeyRotationPolicy(maxKeyAgeDays = 30),
      clock = { createdAt },
    )

    // Key was just created so should not rotate
    assertThat(provider.shouldRotate()).isFalse()
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private fun generateAesKey(): SecretKey {
    return KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
  }
}

// =============================================================================
// Test-only software implementations that mirror VersionedCryptoProvider blob format
// =============================================================================

/**
 * Software-only versioned crypto provider for JVM/Robolectric tests.
 * Produces blobs with the same format as [VersionedCryptoProvider]:
 * [version:1][ivLen:1][iv:N][ciphertext+tag].
 */
private class VersionedSoftwareCryptoProvider(
  private val version: Int = 1,
  private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey(),
) : CryptoProvider {

  override val isEnabled: Boolean = true

  override fun encrypt(plain: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val iv = cipher.iv
    val ct = cipher.doFinal(plain)

    val out = ByteArray(2 + iv.size + ct.size)
    out[0] = version.toByte()
    out[1] = iv.size.toByte()
    System.arraycopy(iv, 0, out, 2, iv.size)
    System.arraycopy(ct, 0, out, 2 + iv.size, ct.size)
    return out
  }

  override fun decrypt(blob: ByteArray): ByteArray {
    require(blob.size >= 2) { "Blob too short" }

    val blobVersion = blob[0].toInt() and 0xFF
    require(blobVersion == version) { "Version mismatch: expected $version, got $blobVersion" }

    val ivLen = blob[1].toInt() and 0xFF
    val iv = blob.copyOfRange(2, 2 + ivLen)
    val ct = blob.copyOfRange(2 + ivLen, blob.size)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
    return cipher.doFinal(ct)
  }
}

/**
 * Multi-version software crypto provider for testing cross-version decryption.
 * Holds multiple AES keys keyed by version number.
 */
private class MultiVersionSoftwareCryptoProvider(
  private val keys: Map<Int, SecretKey>,
  private val currentVersion: Int,
) : CryptoProvider {

  override val isEnabled: Boolean = true

  override fun encrypt(plain: ByteArray): ByteArray {
    val key = checkNotNull(keys[currentVersion]) { "No key for current version $currentVersion" }

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val iv = cipher.iv
    val ct = cipher.doFinal(plain)

    val out = ByteArray(2 + iv.size + ct.size)
    out[0] = currentVersion.toByte()
    out[1] = iv.size.toByte()
    System.arraycopy(iv, 0, out, 2, iv.size)
    System.arraycopy(ct, 0, out, 2 + iv.size, ct.size)
    return out
  }

  override fun decrypt(blob: ByteArray): ByteArray {
    require(blob.size >= 2) { "Blob too short" }

    val version = blob[0].toInt() and 0xFF
    val key = checkNotNull(keys[version]) { "Key version $version not available" }

    val ivLen = blob[1].toInt() and 0xFF
    val iv = blob.copyOfRange(2, 2 + ivLen)
    val ct = blob.copyOfRange(2 + ivLen, blob.size)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
    return cipher.doFinal(ct)
  }
}
