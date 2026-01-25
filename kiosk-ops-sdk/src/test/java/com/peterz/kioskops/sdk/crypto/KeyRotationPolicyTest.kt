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
class KeyRotationPolicyTest {

  @Test
  fun `default policy has reasonable values`() {
    val policy = KeyRotationPolicy.default()
    assertThat(policy.maxKeyAgeDays).isEqualTo(365)
    assertThat(policy.autoRotateEnabled).isFalse()
    assertThat(policy.retainOldKeysForDays).isEqualTo(90)
    assertThat(policy.maxKeyVersions).isEqualTo(5)
  }

  @Test
  fun `strict policy has shorter rotation period`() {
    val policy = KeyRotationPolicy.strict()
    assertThat(policy.maxKeyAgeDays).isEqualTo(90)
    assertThat(policy.autoRotateEnabled).isTrue()
    assertThat(policy.retainOldKeysForDays).isEqualTo(30)
    assertThat(policy.maxKeyVersions).isEqualTo(4)
  }

  @Test
  fun `disabled policy has no rotation`() {
    val policy = KeyRotationPolicy.disabled()
    assertThat(policy.maxKeyAgeDays).isEqualTo(0)
    assertThat(policy.autoRotateEnabled).isFalse()
    assertThat(policy.retainOldKeysForDays).isEqualTo(Int.MAX_VALUE)
    assertThat(policy.maxKeyVersions).isEqualTo(0)
  }

  @Test
  fun `custom policy values`() {
    val policy = KeyRotationPolicy(
      maxKeyAgeDays = 180,
      autoRotateEnabled = true,
      retainOldKeysForDays = 60,
      maxKeyVersions = 3,
    )
    assertThat(policy.maxKeyAgeDays).isEqualTo(180)
    assertThat(policy.autoRotateEnabled).isTrue()
    assertThat(policy.retainOldKeysForDays).isEqualTo(60)
    assertThat(policy.maxKeyVersions).isEqualTo(3)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `negative maxKeyAgeDays throws exception`() {
    KeyRotationPolicy(maxKeyAgeDays = -1)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `negative retainOldKeysForDays throws exception`() {
    KeyRotationPolicy(retainOldKeysForDays = -1)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `negative maxKeyVersions throws exception`() {
    KeyRotationPolicy(maxKeyVersions = -1)
  }

  @Test
  fun `RotationResult Success contains version info`() {
    val result = RotationResult.Success(newKeyVersion = 2, previousKeyVersion = 1)
    assertThat(result.newKeyVersion).isEqualTo(2)
    assertThat(result.previousKeyVersion).isEqualTo(1)
  }

  @Test
  fun `RotationResult NotNeeded contains current state`() {
    val result = RotationResult.NotNeeded(currentKeyVersion = 1, keyAgeDays = 30)
    assertThat(result.currentKeyVersion).isEqualTo(1)
    assertThat(result.keyAgeDays).isEqualTo(30)
  }

  @Test
  fun `RotationResult Failed contains error info`() {
    val exception = RuntimeException("test error")
    val result = RotationResult.Failed(reason = "Key generation failed", cause = exception)
    assertThat(result.reason).isEqualTo("Key generation failed")
    assertThat(result.cause).isEqualTo(exception)
  }
}
