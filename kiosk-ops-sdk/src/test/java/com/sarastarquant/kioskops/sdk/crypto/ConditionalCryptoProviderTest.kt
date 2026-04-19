/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [ConditionalCryptoProvider].
 *
 * Verifies the configuration-driven encryption toggle: when enabled the
 * delegate is invoked; when disabled plaintext passes through untouched.
 */
@RunWith(RobolectricTestRunner::class)
class ConditionalCryptoProviderTest {

  // -------------------------------------------------------------------------
  // isEnabled reflects both the flag and delegate state
  // -------------------------------------------------------------------------

  @Test
  fun `isEnabled is true when flag is true and delegate is enabled`() {
    val provider = ConditionalCryptoProvider(
      enabledProvider = { true },
      delegate = SoftwareAesGcmCryptoProvider(),
    )
    assertThat(provider.isEnabled).isTrue()
  }

  @Test
  fun `isEnabled is false when flag is false`() {
    val provider = ConditionalCryptoProvider(
      enabledProvider = { false },
      delegate = SoftwareAesGcmCryptoProvider(),
    )
    assertThat(provider.isEnabled).isFalse()
  }

  @Test
  fun `isEnabled is false when delegate is disabled`() {
    val provider = ConditionalCryptoProvider(
      enabledProvider = { true },
      delegate = NoopCryptoProvider,
    )
    // NoopCryptoProvider.isEnabled == false, so conditional is false
    assertThat(provider.isEnabled).isFalse()
  }

  @Test
  fun `isEnabled is false when both flag and delegate are disabled`() {
    val provider = ConditionalCryptoProvider(
      enabledProvider = { false },
      delegate = NoopCryptoProvider,
    )
    assertThat(provider.isEnabled).isFalse()
  }

  // -------------------------------------------------------------------------
  // Encrypt: delegate called when enabled; passthrough when disabled
  // -------------------------------------------------------------------------

  @Test
  fun `encrypt delegates to real provider when enabled`() {
    val delegate = SoftwareAesGcmCryptoProvider()
    val provider = ConditionalCryptoProvider(
      enabledProvider = { true },
      delegate = delegate,
    )
    val plaintext = "secret data".toByteArray()

    val encrypted = provider.encrypt(plaintext)

    // Encrypted output should differ from plaintext
    assertThat(encrypted).isNotEqualTo(plaintext)
    // And should have the AES-GCM blob header (version byte = 1)
    assertThat(encrypted[0].toInt()).isEqualTo(1)
  }

  @Test
  fun `encrypt returns plaintext when disabled`() {
    val delegate = SoftwareAesGcmCryptoProvider()
    val provider = ConditionalCryptoProvider(
      enabledProvider = { false },
      delegate = delegate,
    )
    val plaintext = "not encrypted".toByteArray()

    val result = provider.encrypt(plaintext)

    assertThat(result).isEqualTo(plaintext)
  }

  // -------------------------------------------------------------------------
  // Decrypt: delegate called when enabled; passthrough when disabled
  // -------------------------------------------------------------------------

  @Test
  fun `decrypt delegates to real provider when enabled`() {
    val delegate = SoftwareAesGcmCryptoProvider()
    val provider = ConditionalCryptoProvider(
      enabledProvider = { true },
      delegate = delegate,
    )
    val plaintext = "round-trip test".toByteArray()
    val encrypted = delegate.encrypt(plaintext)

    val decrypted = provider.decrypt(encrypted)

    assertThat(decrypted).isEqualTo(plaintext)
  }

  @Test
  fun `decrypt returns blob unchanged when disabled`() {
    val provider = ConditionalCryptoProvider(
      enabledProvider = { false },
      delegate = SoftwareAesGcmCryptoProvider(),
    )
    val blob = "passthrough blob".toByteArray()

    val result = provider.decrypt(blob)

    assertThat(result).isEqualTo(blob)
  }

  // -------------------------------------------------------------------------
  // Round-trip encrypt then decrypt in both states
  // -------------------------------------------------------------------------

  @Test
  fun `round-trip succeeds when encryption is enabled`() {
    val delegate = SoftwareAesGcmCryptoProvider()
    val provider = ConditionalCryptoProvider(
      enabledProvider = { true },
      delegate = delegate,
    )
    val plaintext = "full round-trip".toByteArray()

    val encrypted = provider.encrypt(plaintext)
    val decrypted = provider.decrypt(encrypted)

    assertThat(decrypted).isEqualTo(plaintext)
  }

  @Test
  fun `round-trip is identity when encryption is disabled`() {
    val provider = ConditionalCryptoProvider(
      enabledProvider = { false },
      delegate = SoftwareAesGcmCryptoProvider(),
    )
    val plaintext = "no-op round-trip".toByteArray()

    val encrypted = provider.encrypt(plaintext)
    val decrypted = provider.decrypt(encrypted)

    // Both encrypt and decrypt are identity, so result == original
    assertThat(encrypted).isEqualTo(plaintext)
    assertThat(decrypted).isEqualTo(plaintext)
  }

  // -------------------------------------------------------------------------
  // Dynamic toggling
  // -------------------------------------------------------------------------

  @Test
  fun `toggling enabled flag changes behavior dynamically`() {
    var enabled = true
    val delegate = SoftwareAesGcmCryptoProvider()
    val provider = ConditionalCryptoProvider(
      enabledProvider = { enabled },
      delegate = delegate,
    )
    val plaintext = "toggle test".toByteArray()

    // Enabled: encrypt produces ciphertext
    val encrypted = provider.encrypt(plaintext)
    assertThat(encrypted).isNotEqualTo(plaintext)

    // Disable: encrypt returns plaintext
    enabled = false
    val passthrough = provider.encrypt(plaintext)
    assertThat(passthrough).isEqualTo(plaintext)

    // Re-enable: can decrypt what was encrypted when enabled
    enabled = true
    val decrypted = provider.decrypt(encrypted)
    assertThat(decrypted).isEqualTo(plaintext)
  }

  // -------------------------------------------------------------------------
  // Empty plaintext
  // -------------------------------------------------------------------------

  @Test
  fun `encrypt and decrypt empty byte array when enabled`() {
    val delegate = SoftwareAesGcmCryptoProvider()
    val provider = ConditionalCryptoProvider(
      enabledProvider = { true },
      delegate = delegate,
    )
    val empty = byteArrayOf()

    val encrypted = provider.encrypt(empty)
    val decrypted = provider.decrypt(encrypted)

    assertThat(decrypted).isEqualTo(empty)
  }

  @Test
  fun `encrypt and decrypt empty byte array when disabled`() {
    val provider = ConditionalCryptoProvider(
      enabledProvider = { false },
      delegate = SoftwareAesGcmCryptoProvider(),
    )
    val empty = byteArrayOf()

    val encrypted = provider.encrypt(empty)
    val decrypted = provider.decrypt(encrypted)

    assertThat(encrypted).isEqualTo(empty)
    assertThat(decrypted).isEqualTo(empty)
  }

  // -------------------------------------------------------------------------
  // Large payload
  // -------------------------------------------------------------------------

  @Test
  fun `round-trip with large payload when enabled`() {
    val delegate = SoftwareAesGcmCryptoProvider()
    val provider = ConditionalCryptoProvider(
      enabledProvider = { true },
      delegate = delegate,
    )
    val large = ByteArray(64 * 1024) { (it % 256).toByte() }

    val encrypted = provider.encrypt(large)
    val decrypted = provider.decrypt(encrypted)

    assertThat(decrypted).isEqualTo(large)
  }
}
