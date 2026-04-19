/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.util

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * InstallSecret's happy path exercises the Android Keystore, which Robolectric does not
 * implement. These JVM tests cover the parts that don't need Keystore:
 *
 * - [InstallSecret.zeroize] array-filling behavior.
 * - The [IllegalStateException]/Keystore-missing surfacing on Robolectric (same pattern used
 *   in [com.sarastarquant.kioskops.sdk.crypto.DatabaseEncryptionProviderTest]).
 *
 * Full Keystore-wrap + legacy-migration verification belongs in instrumented tests against
 * a real device Keystore.
 */
@RunWith(RobolectricTestRunner::class)
class InstallSecretTest {

  @Test
  fun `zeroize fills byte array with zeros`() {
    val secret = ByteArray(32) { (it + 1).toByte() }
    InstallSecret.zeroize(secret)
    assertThat(secret.all { it == 0.toByte() }).isTrue()
  }

  @Test
  fun `getOrCreate surfaces Keystore failure on Robolectric`() {
    // Robolectric does not provide AndroidKeyStore, so getOrCreate should fail loudly
    // rather than silently fall back to plaintext. Mirrors DatabaseEncryptionProviderTest.
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    var threw = false
    try {
      InstallSecret.getOrCreate(context)
    } catch (_: Exception) {
      threw = true
    }
    assertThat(threw).isTrue()
  }
}
