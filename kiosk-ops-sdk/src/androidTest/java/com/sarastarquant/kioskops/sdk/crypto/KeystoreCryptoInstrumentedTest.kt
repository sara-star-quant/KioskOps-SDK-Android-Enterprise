/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.crypto

import com.google.common.truth.Truth.assertThat
import java.security.KeyStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Instrumented tests for [AesGcmKeystoreCryptoProvider] running on a real device/emulator.
 *
 * These require the real AndroidKeyStore hardware (or emulator) backing, which is not
 * available under Robolectric.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreCryptoInstrumentedTest {

  private val alias = "test_instrumented_key"
  private lateinit var provider: AesGcmKeystoreCryptoProvider

  @Before
  fun setUp() {
    // Ensure clean state: remove any leftover key from a previous run.
    deleteKeystoreEntry(alias)
    provider = AesGcmKeystoreCryptoProvider(alias = alias)
  }

  @After
  fun tearDown() {
    deleteKeystoreEntry(alias)
  }

  // ---------------------------------------------------------------------------
  // Key generation
  // ---------------------------------------------------------------------------

  @Test
  fun generateKeyAndVerifyItExistsInKeystore() {
    // Trigger key creation by encrypting something.
    provider.encrypt("trigger".toByteArray(Charsets.UTF_8))

    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    assertThat(ks.containsAlias(alias)).isTrue()
  }

  // ---------------------------------------------------------------------------
  // Encrypt / decrypt round-trip
  // ---------------------------------------------------------------------------

  @Test
  fun encryptAndDecryptRoundTrip() {
    val plaintext = "Hello, KioskOps!".toByteArray(Charsets.UTF_8)
    val encrypted = provider.encrypt(plaintext)
    val decrypted = provider.decrypt(encrypted)
    assertThat(decrypted).isEqualTo(plaintext)
  }

  @Test
  fun encryptedOutputDiffersFromPlaintext() {
    val plaintext = "sensitive-payload".toByteArray(Charsets.UTF_8)
    val encrypted = provider.encrypt(plaintext)

    // The blob contains a version byte, IV length, IV, and ciphertext: never the raw input.
    assertThat(encrypted).isNotEqualTo(plaintext)
    assertThat(encrypted.size).isGreaterThan(plaintext.size)
  }

  // ---------------------------------------------------------------------------
  // Wrong alias
  // ---------------------------------------------------------------------------

  @Test
  fun decryptWithWrongAliasFails() {
    val plaintext = "secret".toByteArray(Charsets.UTF_8)
    val encrypted = provider.encrypt(plaintext)

    // Create a second provider with a different alias.
    val otherAlias = "test_instrumented_key_other"
    try {
      val other = AesGcmKeystoreCryptoProvider(alias = otherAlias)
      // Force key creation under the other alias.
      other.encrypt("x".toByteArray(Charsets.UTF_8))

      var threw = false
      try {
        other.decrypt(encrypted)
      } catch (_: Exception) {
        threw = true
      }
      assertThat(threw).isTrue()
    } finally {
      deleteKeystoreEntry(otherAlias)
    }
  }

  // ---------------------------------------------------------------------------
  // Edge cases
  // ---------------------------------------------------------------------------

  @Test
  fun emptyPlaintextEncryptDecryptRoundTrip() {
    val plaintext = ByteArray(0)
    val encrypted = provider.encrypt(plaintext)
    val decrypted = provider.decrypt(encrypted)
    assertThat(decrypted).isEqualTo(plaintext)
  }

  @Test
  fun largePayload64KbRoundTrip() {
    val plaintext = ByteArray(64 * 1024) { (it % 256).toByte() }
    val encrypted = provider.encrypt(plaintext)
    val decrypted = provider.decrypt(encrypted)
    assertThat(decrypted).isEqualTo(plaintext)
  }

  // ---------------------------------------------------------------------------
  // Key persistence
  // ---------------------------------------------------------------------------

  @Test
  fun keyPersistsAcrossProviderInstances() {
    val plaintext = "persist-check".toByteArray(Charsets.UTF_8)
    val encrypted = provider.encrypt(plaintext)

    // Create a brand-new provider instance with the same alias.
    val provider2 = AesGcmKeystoreCryptoProvider(alias = alias)
    val decrypted = provider2.decrypt(encrypted)
    assertThat(decrypted).isEqualTo(plaintext)
  }

  // ---------------------------------------------------------------------------
  // isEnabled
  // ---------------------------------------------------------------------------

  @Test
  fun isEnabledReturnsTrue() {
    assertThat(provider.isEnabled).isTrue()
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private fun deleteKeystoreEntry(keyAlias: String) {
    try {
      val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
      if (ks.containsAlias(keyAlias)) {
        ks.deleteEntry(keyAlias)
      }
    } catch (_: Exception) {
      // Best-effort cleanup.
    }
  }
}
