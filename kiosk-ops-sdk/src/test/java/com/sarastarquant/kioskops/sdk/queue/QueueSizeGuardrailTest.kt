/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.queue

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueueSizeGuardrailTest : QueueTestBase() {

  @Test
  fun `payload exceeding maxEventPayloadBytes is rejected as PayloadTooLarge`() = runTest {
    val tinyCfg = baseCfg.copy(
      securityPolicy = baseCfg.securityPolicy.copy(maxEventPayloadBytes = 10),
    )
    val repo = newRepo()

    val result = repo.enqueue("T", "{\"data\":\"this-exceeds-ten-bytes\"}", tinyCfg)

    assertThat(result.isAccepted).isFalse()
    assertThat(result).isInstanceOf(EnqueueResult.Rejected.PayloadTooLarge::class.java)
    val rejected = result as EnqueueResult.Rejected.PayloadTooLarge
    assertThat(rejected.max).isEqualTo(10)
    assertThat(rejected.bytes).isGreaterThan(10)
  }

  @Test
  fun `empty payload is accepted`() = runTest {
    val repo = newRepo()

    val result = repo.enqueue("T", "", baseCfg)

    assertThat(result.isAccepted).isTrue()
  }

  @Test
  fun `payload at exact byte limit is accepted`() = runTest {
    val limit = 20
    val cfg = baseCfg.copy(
      securityPolicy = baseCfg.securityPolicy.copy(maxEventPayloadBytes = limit),
    )
    val payload = "a".repeat(limit)
    assertThat(payload.toByteArray(Charsets.UTF_8).size).isEqualTo(limit)

    val repo = newRepo()
    val result = repo.enqueue("T", payload, cfg)

    assertThat(result.isAccepted).isTrue()
  }

  @Test
  fun `payload one byte over limit is rejected`() = runTest {
    val limit = 20
    val cfg = baseCfg.copy(
      securityPolicy = baseCfg.securityPolicy.copy(maxEventPayloadBytes = limit),
    )
    val payload = "a".repeat(limit + 1)

    val repo = newRepo()
    val result = repo.enqueue("T", payload, cfg)

    assertThat(result.isAccepted).isFalse()
    assertThat(result).isInstanceOf(EnqueueResult.Rejected.PayloadTooLarge::class.java)
  }

  @Test
  fun `multibyte UTF-8 payload is measured in bytes not chars`() = runTest {
    val multibytePayload = "\u4e16\u754c\u4f60\u597d"
    val byteLen = multibytePayload.toByteArray(Charsets.UTF_8).size
    assertThat(byteLen).isEqualTo(12)

    val cfg = baseCfg.copy(
      securityPolicy = baseCfg.securityPolicy.copy(maxEventPayloadBytes = 11),
    )
    val repo = newRepo()
    val result = repo.enqueue("T", multibytePayload, cfg)

    assertThat(result.isAccepted).isFalse()
    assertThat(result).isInstanceOf(EnqueueResult.Rejected.PayloadTooLarge::class.java)
  }
}
