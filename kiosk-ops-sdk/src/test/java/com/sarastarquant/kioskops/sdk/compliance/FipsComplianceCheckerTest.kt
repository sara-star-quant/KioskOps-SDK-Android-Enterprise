/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.compliance

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FipsComplianceCheckerTest {

  @Test
  fun `check returns result with provider info`() {
    val status = FipsComplianceChecker.check()
    // On standard JVM/Robolectric, FIPS mode is not active
    assertThat(status.isFipsMode).isFalse()
    assertThat(status.details).isNotEmpty()
  }

  @Test
  fun `FipsStatus data class fields are accessible`() {
    val status = FipsComplianceChecker.FipsStatus(
      isFipsMode = true,
      providerName = "Conscrypt",
      providerVersion = "2.5.6",
      details = "FIPS mode active",
    )
    assertThat(status.isFipsMode).isTrue()
    assertThat(status.providerName).isEqualTo("Conscrypt")
    assertThat(status.providerVersion).isEqualTo("2.5.6")
  }
}
