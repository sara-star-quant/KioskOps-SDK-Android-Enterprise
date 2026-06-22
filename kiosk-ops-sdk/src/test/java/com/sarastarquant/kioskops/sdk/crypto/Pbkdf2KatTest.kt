/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.crypto

import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.compliance.ComplianceControl
import org.junit.Test

/**
 * Known-answer tests for PBKDF2 against published vectors, run through the
 * SDK's own [SecureKeyDerivation.deriveKeyWithSalt]. A pass proves the SDK
 * derives the exact bytes the standards specify, not just that derivation is
 * internally consistent.
 *
 * SHA-1 vector: RFC 6070 section 2 (case 5, the embedded-NUL case, truncated to
 * a 128-bit key so it maps onto the SDK's supported key lengths).
 * SHA-256 vectors: the widely cited PBKDF2-HMAC-SHA256 vectors for
 * password="password", salt="salt".
 */
@ComplianceControl(framework = "NIST SP 800-171", control = "3.13.11")
class Pbkdf2KatTest {

  @Test
  fun `PBKDF2-HMAC-SHA1 matches RFC 6070 case 5`() {
    val derivation = SecureKeyDerivation(
      KeyDerivationConfig(
        algorithm = "PBKDF2WithHmacSHA1",
        iterationCount = 4096,
        saltLengthBytes = 16,
        keyLengthBits = 128,
      ),
    )

    val key = derivation.deriveKeyWithSalt("pass\u0000word".toCharArray(), "sa\u0000lt".toByteArray())

    assertThat(key.encoded).isEqualTo(hex("56fa6aa75548099dcc37d7f03425e0c3"))
  }

  @Test
  fun `PBKDF2-HMAC-SHA256 matches vector at 1 iteration`() {
    val key = deriveSha256(iterations = 1)
    assertThat(key.encoded).isEqualTo(
      hex("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b"),
    )
  }

  @Test
  fun `PBKDF2-HMAC-SHA256 matches vector at 4096 iterations`() {
    val key = deriveSha256(iterations = 4096)
    assertThat(key.encoded).isEqualTo(
      hex("c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a"),
    )
  }

  private fun deriveSha256(iterations: Int) = SecureKeyDerivation(
    KeyDerivationConfig(
      algorithm = "PBKDF2WithHmacSHA256",
      iterationCount = iterations,
      saltLengthBytes = 16,
      keyLengthBits = 256,
    ),
  ).deriveKeyWithSalt("password".toCharArray(), "salt".toByteArray())

  private fun hex(s: String): ByteArray =
    ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
