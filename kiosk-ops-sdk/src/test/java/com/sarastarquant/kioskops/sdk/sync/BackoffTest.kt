/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BackoffTest {

  @Test
  fun `delay doubles from ten seconds`() {
    assertThat(Backoff.nextDelayMs(0)).isEqualTo(10_000L)
    assertThat(Backoff.nextDelayMs(1)).isEqualTo(20_000L)
    assertThat(Backoff.nextDelayMs(2)).isEqualTo(40_000L)
  }

  @Test
  fun `negative attempts behave as zero`() {
    assertThat(Backoff.nextDelayMs(-5)).isEqualTo(10_000L)
  }

  @Test
  fun `delay stops doubling at the documented ceiling`() {
    assertThat(Backoff.CEILING_MS).isEqualTo(10_240_000L)
    assertThat(Backoff.nextDelayMs(10)).isEqualTo(Backoff.CEILING_MS)
    assertThat(Backoff.nextDelayMs(11)).isEqualTo(Backoff.CEILING_MS)
    assertThat(Backoff.nextDelayMs(1000)).isEqualTo(Backoff.CEILING_MS)
  }

  @Test
  fun `delay never decreases as attempts grow`() {
    var previous = 0L
    for (attempt in 0..50) {
      val delay = Backoff.nextDelayMs(attempt)
      assertThat(delay).isAtLeast(previous)
      previous = delay
    }
  }
}
