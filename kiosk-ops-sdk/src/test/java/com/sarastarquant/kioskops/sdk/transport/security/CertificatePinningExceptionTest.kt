/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.transport.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CertificatePinningExceptionTest {

  // ---------------------------------------------------------------------------
  // CertificatePinningException
  // ---------------------------------------------------------------------------

  @Test
  fun `CertificatePinningException contains message`() {
    val cause = RuntimeException("root cause")
    val exception = CertificatePinningException("pin failed", cause)
    assertThat(exception.message).isEqualTo("pin failed")
    assertThat(exception.cause).isEqualTo(cause)
  }

  @Test
  fun `CertificatePinningException with null cause`() {
    val exception = CertificatePinningException("pin failed")
    assertThat(exception.message).isEqualTo("pin failed")
    assertThat(exception.cause).isNull()
  }

  @Test
  fun `CertificatePinningException is IOException`() {
    val exception = CertificatePinningException("test")
    assertThat(exception).isInstanceOf(java.io.IOException::class.java)
  }
}
