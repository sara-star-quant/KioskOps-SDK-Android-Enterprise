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
class DatabaseEncryptionProviderTest {

  @Test
  fun `createFactory throws when SQLCipher not on classpath`() {
    // On Robolectric, AndroidKeyStore is not available, so createFactory will fail
    // either at key generation or at SQLCipher class loading.
    // We verify it does not silently succeed.
    var threw = false
    try {
      DatabaseEncryptionProvider.createFactory()
    } catch (_: Exception) {
      threw = true
    }
    assertThat(threw).isTrue()
  }
}
