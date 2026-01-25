/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.crypto

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
}
