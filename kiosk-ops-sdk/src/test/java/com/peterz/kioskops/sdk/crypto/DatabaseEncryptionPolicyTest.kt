/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DatabaseEncryptionPolicyTest {

  @Test
  fun `disabledDefaults is not enabled`() {
    val policy = DatabaseEncryptionPolicy.disabledDefaults()
    assertThat(policy.enabled).isFalse()
  }

  @Test
  fun `enabledDefaults is enabled`() {
    val policy = DatabaseEncryptionPolicy.enabledDefaults()
    assertThat(policy.enabled).isTrue()
  }

  @Test
  fun `default constructor is disabled`() {
    val policy = DatabaseEncryptionPolicy()
    assertThat(policy.enabled).isFalse()
  }

  @Test
  fun `copy preserves values`() {
    val policy = DatabaseEncryptionPolicy.enabledDefaults()
    val copy = policy.copy(enabled = false)
    assertThat(copy.enabled).isFalse()
  }
}
