package com.peterz.kioskops.sdk.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FieldLevelEncryptorTest {

  @Test fun `encrypts specified fields when crypto enabled`() {
    val crypto = object : CryptoProvider {
      override val isEnabled = true
      override fun encrypt(plain: ByteArray): ByteArray = plain.reversedArray()
      override fun decrypt(blob: ByteArray): ByteArray = blob.reversedArray()
    }
    val encryptor = FieldLevelEncryptor(crypto)

    val result = encryptor.encryptFields(
      """{"email":"user@test.com","safe":"data"}""",
      setOf("email"),
    )

    assertThat(result).contains("__enc")
    assertThat(result).contains("__alg")
    assertThat(result).contains("AES-256-GCM")
    assertThat(result).contains("safe")
    assertThat(result).doesNotContain("user@test.com")
  }

  @Test fun `returns original when crypto disabled`() {
    val encryptor = FieldLevelEncryptor(NoopCryptoProvider)
    val original = """{"email":"user@test.com"}"""
    val result = encryptor.encryptFields(original, setOf("email"))
    assertThat(result).isEqualTo(original)
  }

  @Test fun `returns original when no fields specified`() {
    val crypto = object : CryptoProvider {
      override val isEnabled = true
      override fun encrypt(plain: ByteArray): ByteArray = plain
      override fun decrypt(blob: ByteArray): ByteArray = blob
    }
    val encryptor = FieldLevelEncryptor(crypto)
    val original = """{"email":"user@test.com"}"""
    val result = encryptor.encryptFields(original, emptySet())
    assertThat(result).isEqualTo(original)
  }

  @Test fun `only encrypts primitive fields, not nested objects`() {
    val crypto = object : CryptoProvider {
      override val isEnabled = true
      override fun encrypt(plain: ByteArray): ByteArray = plain.reversedArray()
      override fun decrypt(blob: ByteArray): ByteArray = blob.reversedArray()
    }
    val encryptor = FieldLevelEncryptor(crypto)

    val result = encryptor.encryptFields(
      """{"name":"test","nested":{"key":"val"}}""",
      setOf("name", "nested"),
    )
    // name (primitive) should be encrypted, nested (object) should not
    assertThat(result).contains("__enc")
  }
}
