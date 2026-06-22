/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.crypto

import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.compliance.ComplianceControl
import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec
import org.junit.Test

/**
 * Known-answer tests for the SDK's AES-256-GCM transform against the published
 * vectors in McGrew & Viega, "The Galois/Counter Mode of Operation (GCM)"
 * (the NIST SP 800-38D reference vectors), AES-256 test cases 13-15.
 *
 * These run through [AesGcmBlob] - the exact code path used by the shipped
 * [AesGcmKeystoreCryptoProvider] - so a pass proves the SDK's parameters
 * (256-bit key, 96-bit IV, 128-bit tag, no AAD) and blob framing are correct,
 * not merely that the JDK ships a working AES. The Keystore-backed key path
 * itself requires hardware and is exercised by instrumented tests.
 */
@ComplianceControl(framework = "NIST SP 800-171", control = "3.13.11")
class AesGcmKatTest {

  // GCM spec Test Case 13: 256-bit zero key, zero IV, empty plaintext.
  @Test
  fun `case 13 empty plaintext matches published tag`() {
    val key = aesKey("0000000000000000000000000000000000000000000000000000000000000000")
    val iv = hex("000000000000000000000000")
    val expectedCtAndTag = hex("530f8afbc74536b9a963b4f1c4cb738b")

    val blob = AesGcmBlob.sealWithIv(key, iv, ByteArray(0))

    assertThat(ciphertextAndTag(blob, iv.size)).isEqualTo(expectedCtAndTag)
    assertThat(AesGcmBlob.open(key, blob)).isEqualTo(ByteArray(0))
  }

  // GCM spec Test Case 14: 256-bit zero key, zero IV, one zero block of plaintext.
  @Test
  fun `case 14 single block matches published ciphertext and tag`() {
    val key = aesKey("0000000000000000000000000000000000000000000000000000000000000000")
    val iv = hex("000000000000000000000000")
    val plain = hex("00000000000000000000000000000000")
    val expectedCtAndTag = hex(
      "cea7403d4d606b6e074ec5d3baf39d18" + "d0d1c8a799996bf0265b98b5d48ab919",
    )

    val blob = AesGcmBlob.sealWithIv(key, iv, plain)

    assertThat(ciphertextAndTag(blob, iv.size)).isEqualTo(expectedCtAndTag)
    assertThat(AesGcmBlob.open(key, blob)).isEqualTo(plain)
  }

  // GCM spec Test Case 15: full 64-byte plaintext, no associated data.
  @Test
  fun `case 15 multi block matches published ciphertext and tag`() {
    val key = aesKey("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
    val iv = hex("cafebabefacedbaddecaf888")
    val plain = hex(
      "d9313225f88406e5a55909c5aff5269a" +
        "86a7a9531534f7da2e4c303d8a318a72" +
        "1c3c0c95956809532fcf0e2449a6b525" +
        "b16aedf5aa0de657ba637b391aafd255",
    )
    val expectedCtAndTag = hex(
      "522dc1f099567d07f47f37a32a84427d" +
        "643a8cdcbfe5c0c97598a2bd2555d1aa" +
        "8cb08e48590dbb3da7b08b1056828838" +
        "c5f61e6393ba7a0abcc9f662898015ad" +
        "b094dac5d93471bdec1a502270e3cc6c",
    )

    val blob = AesGcmBlob.sealWithIv(key, iv, plain)

    assertThat(ciphertextAndTag(blob, iv.size)).isEqualTo(expectedCtAndTag)
    assertThat(AesGcmBlob.open(key, blob)).isEqualTo(plain)
  }

  @Test(expected = AEADBadTagException::class)
  fun `tampered tag is rejected`() {
    val key = aesKey("0000000000000000000000000000000000000000000000000000000000000000")
    val blob = AesGcmBlob.sealWithIv(key, hex("000000000000000000000000"), hex("00000000000000000000000000000000"))

    blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 0x01).toByte()

    AesGcmBlob.open(key, blob)
  }

  private fun ciphertextAndTag(blob: ByteArray, ivLen: Int): ByteArray =
    blob.copyOfRange(2 + ivLen, blob.size)

  private fun aesKey(hex: String) = SecretKeySpec(hex(hex), "AES")

  private fun hex(s: String): ByteArray {
    val clean = s.replace(" ", "")
    return ByteArray(clean.length / 2) { i ->
      clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
  }
}
