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
class KeyDerivationConfigTest {

  @Test
  fun `default config follows OWASP 2023 recommendations`() {
    val config = KeyDerivationConfig.default()
    assertThat(config.algorithm).isEqualTo("PBKDF2WithHmacSHA256")
    assertThat(config.iterationCount).isEqualTo(310_000)
    assertThat(config.saltLengthBytes).isEqualTo(32)
    assertThat(config.keyLengthBits).isEqualTo(256)
  }

  @Test
  fun `fastForTesting has fewer iterations`() {
    val config = KeyDerivationConfig.fastForTesting()
    assertThat(config.iterationCount).isEqualTo(1000)
    assertThat(config.saltLengthBytes).isEqualTo(16)
  }

  @Test
  fun `legacy config uses SHA1`() {
    val config = KeyDerivationConfig.legacy()
    assertThat(config.algorithm).isEqualTo("PBKDF2WithHmacSHA1")
    assertThat(config.iterationCount).isEqualTo(100_000)
  }

  @Test
  fun `highSecurity config uses SHA512 with more iterations`() {
    val config = KeyDerivationConfig.highSecurity()
    assertThat(config.algorithm).isEqualTo("PBKDF2WithHmacSHA512")
    assertThat(config.iterationCount).isEqualTo(600_000)
  }

  @Test
  fun `custom config values`() {
    val config = KeyDerivationConfig(
      algorithm = "PBKDF2WithHmacSHA384",
      iterationCount = 200_000,
      saltLengthBytes = 24,
      keyLengthBits = 192,
    )
    assertThat(config.algorithm).isEqualTo("PBKDF2WithHmacSHA384")
    assertThat(config.iterationCount).isEqualTo(200_000)
    assertThat(config.saltLengthBytes).isEqualTo(24)
    assertThat(config.keyLengthBits).isEqualTo(192)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `zero iterations throws exception`() {
    KeyDerivationConfig(iterationCount = 0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `negative iterations throws exception`() {
    KeyDerivationConfig(iterationCount = -1)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `salt length less than 16 throws exception`() {
    KeyDerivationConfig(saltLengthBytes = 15)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `invalid key length throws exception`() {
    KeyDerivationConfig(keyLengthBits = 512)
  }

  @Test
  fun `valid key lengths accepted`() {
    assertThat(KeyDerivationConfig(keyLengthBits = 128).keyLengthBits).isEqualTo(128)
    assertThat(KeyDerivationConfig(keyLengthBits = 192).keyLengthBits).isEqualTo(192)
    assertThat(KeyDerivationConfig(keyLengthBits = 256).keyLengthBits).isEqualTo(256)
  }
}
