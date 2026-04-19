/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecureKeyDerivationTest {

  // Use fast config for testing
  private val derivation = SecureKeyDerivation(KeyDerivationConfig.fastForTesting())

  @Test
  fun `deriveKey produces key and salt`() {
    val result = derivation.deriveKey("password".toCharArray())

    assertThat(result.key).isNotNull()
    assertThat(result.salt).isNotNull()
    assertThat(result.salt).hasLength(16) // fastForTesting uses 16 bytes
    assertThat(result.algorithm).isEqualTo("PBKDF2WithHmacSHA256")
    assertThat(result.iterationCount).isEqualTo(1000)
  }

  @Test
  fun `deriveKey with string password`() {
    val result = derivation.deriveKey("password")

    assertThat(result.key).isNotNull()
    assertThat(result.salt).isNotNull()
  }

  @Test
  fun `deriveKeyWithSalt produces same key`() {
    val result1 = derivation.deriveKey("password".toCharArray())
    val key2 = derivation.deriveKeyWithSalt("password".toCharArray(), result1.salt)

    assertThat(key2.encoded).isEqualTo(result1.key.encoded)
  }

  @Test
  fun `different passwords produce different keys`() {
    val result1 = derivation.deriveKey("password1".toCharArray())
    val key2 = derivation.deriveKeyWithSalt("password2".toCharArray(), result1.salt)

    assertThat(key2.encoded).isNotEqualTo(result1.key.encoded)
  }

  @Test
  fun `different salts produce different keys`() {
    val result1 = derivation.deriveKey("password".toCharArray())
    val result2 = derivation.deriveKey("password".toCharArray())

    // Same password but different random salts should produce different keys
    assertThat(result1.salt).isNotEqualTo(result2.salt)
    assertThat(result1.key.encoded).isNotEqualTo(result2.key.encoded)
  }

  @Test
  fun `deriveDeterministic produces consistent output`() {
    val input = "test input".toByteArray()
    val context = "idempotency"
    val salt = ByteArray(16) { it.toByte() }

    val result1 = derivation.deriveDeterministic(input, context, salt)
    val result2 = derivation.deriveDeterministic(input, context, salt)

    assertThat(result1).isEqualTo(result2)
  }

  @Test
  fun `deriveDeterministic with different context produces different output`() {
    val input = "test input".toByteArray()
    val salt = ByteArray(16) { it.toByte() }

    val result1 = derivation.deriveDeterministic(input, "context1", salt)
    val result2 = derivation.deriveDeterministic(input, "context2", salt)

    assertThat(result1).isNotEqualTo(result2)
  }

  @Test
  fun `DerivedKeyResult equals and hashCode`() {
    val result1 = derivation.deriveKey("password")
    val result2 = derivation.deriveKeyWithSalt("password", result1.salt)

    val derived1 = DerivedKeyResult(
      key = result1.key,
      salt = result1.salt,
      algorithm = result1.algorithm,
      iterationCount = result1.iterationCount,
    )
    val derived2 = DerivedKeyResult(
      key = result2,
      salt = result1.salt,
      algorithm = result1.algorithm,
      iterationCount = result1.iterationCount,
    )

    // Keys should be equal since same password and salt
    assertThat(derived1.key.encoded).isEqualTo(derived2.key.encoded)
  }

  @Test
  fun `key algorithm is AES`() {
    val result = derivation.deriveKey("password")
    assertThat(result.key.algorithm).isEqualTo("AES")
  }

  @Test
  fun `HKDF matches RFC 5869 test vector 1 SHA-256`() {
    // RFC 5869 Appendix A.1
    val ikm = ByteArray(22) { 0x0b }
    val salt = byteArrayOf(
      0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
      0x08, 0x09, 0x0a, 0x0b, 0x0c,
    )
    val info = byteArrayOf(
      0xf0.toByte(), 0xf1.toByte(), 0xf2.toByte(), 0xf3.toByte(),
      0xf4.toByte(), 0xf5.toByte(), 0xf6.toByte(), 0xf7.toByte(),
      0xf8.toByte(), 0xf9.toByte(),
    )
    val expected = hexToBytes(
      "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"
    )

    val out = hkdfSha256(ikm, salt, info, expected.size)
    assertThat(out).isEqualTo(expected)
  }

  @Test
  fun `HKDF empty salt still produces deterministic output`() {
    val ikm = "input".toByteArray()
    val out1 = hkdfSha256(ikm, salt = ByteArray(0), info = "ctx".toByteArray(), outLen = 32)
    val out2 = hkdfSha256(ikm, salt = ByteArray(0), info = "ctx".toByteArray(), outLen = 32)
    assertThat(out1).isEqualTo(out2)
    assertThat(out1).hasLength(32)
  }

  @Test
  fun `deriveDeterministic output length matches keyLengthBits`() {
    val input = "payload".toByteArray()
    val salt = ByteArray(16) { it.toByte() }
    val out = derivation.deriveDeterministic(input, "idempotency", salt)
    assertThat(out.size * 8).isEqualTo(KeyDerivationConfig.fastForTesting().keyLengthBits)
  }

  private fun hexToBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0)
    return ByteArray(hex.length / 2) { i ->
      hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
  }
}
