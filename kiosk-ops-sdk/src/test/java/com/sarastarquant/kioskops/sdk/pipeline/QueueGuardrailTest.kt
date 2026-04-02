/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.pipeline

import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.compliance.IdempotencyConfig
import com.sarastarquant.kioskops.sdk.compliance.OverflowStrategy
import com.sarastarquant.kioskops.sdk.compliance.QueueLimits
import com.sarastarquant.kioskops.sdk.queue.EnqueueResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueueGuardrailTest : PipelineTestBase() {

  // ==========================================================================
  // 4. Size Guardrail
  // ==========================================================================

  @Test
  fun `payload exceeding maxEventPayloadBytes is rejected with PayloadTooLarge`() = runTest {
    val smallLimit = 64
    val config = baseConfig(
      securityPolicy = noEncryptionSecurity.copy(maxEventPayloadBytes = smallLimit),
    )
    val sdk = initSdk(config)

    // Generate a payload larger than the 64-byte limit
    val oversizedPayload = """{"data": "${"x".repeat(100)}"}"""
    val result = sdk.enqueueDetailed("oversized.event", oversizedPayload)

    assertThat(result).isInstanceOf(EnqueueResult.Rejected.PayloadTooLarge::class.java)
    val rejected = result as EnqueueResult.Rejected.PayloadTooLarge
    assertThat(rejected.bytes).isGreaterThan(smallLimit)
    assertThat(rejected.max).isEqualTo(smallLimit)
  }

  @Test
  fun `payload exactly at maxEventPayloadBytes limit is accepted`() = runTest {
    // We need the JSON byte length to match exactly the limit.
    // Build a payload, measure its byte size, set the limit to match.
    val payload = """{"v":1}"""
    val payloadBytes = payload.toByteArray(Charsets.UTF_8).size
    val config = baseConfig(
      securityPolicy = noEncryptionSecurity.copy(maxEventPayloadBytes = payloadBytes),
    )
    val sdk = initSdk(config)

    val result = sdk.enqueueDetailed("exact.size", payload)

    assertThat(result).isInstanceOf(EnqueueResult.Accepted::class.java)
  }

  @Test
  fun `payload one byte under maxEventPayloadBytes limit is accepted`() = runTest {
    val payload = """{"v":1}"""
    val payloadBytes = payload.toByteArray(Charsets.UTF_8).size
    val config = baseConfig(
      securityPolicy = noEncryptionSecurity.copy(maxEventPayloadBytes = payloadBytes + 1),
    )
    val sdk = initSdk(config)

    val result = sdk.enqueueDetailed("under.size", payload)

    assertThat(result).isInstanceOf(EnqueueResult.Accepted::class.java)
  }

  // ==========================================================================
  // 5. Queue Overflow / Pressure
  // ==========================================================================

  @Test
  fun `DROP_OLDEST overflow returns droppedOldest greater than zero when queue is at limit`() = runTest {
    val maxEvents = 3
    val config = baseConfig(
      queueLimits = QueueLimits(
        maxActiveEvents = maxEvents,
        maxActiveBytes = 50L * 1024 * 1024,
        overflowStrategy = OverflowStrategy.DROP_OLDEST,
      ),
    )
    val sdk = initSdk(config)

    // Fill the queue to capacity
    for (i in 1..maxEvents) {
      val r = sdk.enqueueDetailed("fill.event", """{"seq": $i}""")
      assertThat(r).isInstanceOf(EnqueueResult.Accepted::class.java)
    }
    assertThat(sdk.queueDepth()).isEqualTo(maxEvents.toLong())

    // Enqueue one more; should drop oldest to make room
    val overflow = sdk.enqueueDetailed("overflow.event", """{"seq": ${maxEvents + 1}}""")
    assertThat(overflow).isInstanceOf(EnqueueResult.Accepted::class.java)
    val accepted = overflow as EnqueueResult.Accepted
    assertThat(accepted.droppedOldest).isGreaterThan(0)
  }

  @Test
  fun `queue depth stays at limit after DROP_OLDEST overflow`() = runTest {
    val maxEvents = 3
    val config = baseConfig(
      queueLimits = QueueLimits(
        maxActiveEvents = maxEvents,
        maxActiveBytes = 50L * 1024 * 1024,
        overflowStrategy = OverflowStrategy.DROP_OLDEST,
      ),
    )
    val sdk = initSdk(config)

    // Fill and then overflow
    for (i in 1..(maxEvents + 2)) {
      sdk.enqueueDetailed("pressure.event", """{"seq": $i}""")
    }

    assertThat(sdk.queueDepth()).isEqualTo(maxEvents.toLong())
  }

  @Test
  fun `DROP_NEWEST overflow rejects new event when queue is at limit`() = runTest {
    val maxEvents = 3
    val config = baseConfig(
      queueLimits = QueueLimits(
        maxActiveEvents = maxEvents,
        maxActiveBytes = 50L * 1024 * 1024,
        overflowStrategy = OverflowStrategy.DROP_NEWEST,
      ),
    )
    val sdk = initSdk(config)

    // Fill the queue to capacity
    for (i in 1..maxEvents) {
      val r = sdk.enqueueDetailed("fill.event", """{"seq": $i}""")
      assertThat(r).isInstanceOf(EnqueueResult.Accepted::class.java)
    }

    // Next enqueue should be rejected
    val result = sdk.enqueueDetailed("overflow.event", """{"seq": 999}""")
    assertThat(result).isInstanceOf(EnqueueResult.Rejected.QueueFull::class.java)
  }

  @Test
  fun `BLOCK overflow strategy rejects new event when queue is at limit`() = runTest {
    val maxEvents = 2
    val config = baseConfig(
      queueLimits = QueueLimits(
        maxActiveEvents = maxEvents,
        maxActiveBytes = 50L * 1024 * 1024,
        overflowStrategy = OverflowStrategy.BLOCK,
      ),
    )
    val sdk = initSdk(config)

    for (i in 1..maxEvents) {
      sdk.enqueueDetailed("fill.event", """{"seq": $i}""")
    }

    val result = sdk.enqueueDetailed("blocked.event", """{"seq": 999}""")
    assertThat(result).isInstanceOf(EnqueueResult.Rejected.QueueFull::class.java)
  }

  // ==========================================================================
  // 6. Idempotency
  // ==========================================================================

  @Test
  fun `duplicate idempotencyKeyOverride is rejected as DuplicateIdempotency`() = runTest {
    val config = baseConfig()
    val sdk = initSdk(config)

    val key = "deterministic-key-abc-123"
    val first = sdk.enqueueDetailed(
      "order.placed",
      """{"orderId": "A1"}""",
      idempotencyKeyOverride = key,
    )
    assertThat(first).isInstanceOf(EnqueueResult.Accepted::class.java)

    val second = sdk.enqueueDetailed(
      "order.placed",
      """{"orderId": "A1"}""",
      idempotencyKeyOverride = key,
    )
    assertThat(second).isInstanceOf(EnqueueResult.Rejected.DuplicateIdempotency::class.java)
  }

  @Test
  fun `explicit idempotencyKeyOverride is used in accepted result`() = runTest {
    val config = baseConfig()
    val sdk = initSdk(config)

    val customKey = "custom-idem-key-xyz-789"
    val result = sdk.enqueueDetailed(
      "order.placed",
      """{"orderId": "B2"}""",
      idempotencyKeyOverride = customKey,
    )

    assertThat(result).isInstanceOf(EnqueueResult.Accepted::class.java)
    val accepted = result as EnqueueResult.Accepted
    assertThat(accepted.idempotencyKey).isEqualTo(customKey)
  }

  @Test
  fun `deterministic idempotency deduplicates same stableEventId within same bucket`() = runTest {
    val config = baseConfig(
      idempotencyConfig = IdempotencyConfig(
        deterministicEnabled = true,
        bucketMs = 24L * 60 * 60 * 1000, // 1 day bucket
      ),
    )
    val sdk = initSdk(config)

    val stableId = "business-event-001"
    val first = sdk.enqueueDetailed(
      "order.placed",
      """{"orderId": "C3"}""",
      stableEventId = stableId,
    )
    assertThat(first).isInstanceOf(EnqueueResult.Accepted::class.java)

    // Same stableEventId within same time bucket should produce same idempotency key
    val second = sdk.enqueueDetailed(
      "order.placed",
      """{"orderId": "C3"}""",
      stableEventId = stableId,
    )
    assertThat(second).isInstanceOf(EnqueueResult.Rejected.DuplicateIdempotency::class.java)
  }
}
