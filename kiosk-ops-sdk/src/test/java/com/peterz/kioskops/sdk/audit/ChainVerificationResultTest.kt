/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.audit

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChainVerificationResultTest {

  @Test
  fun `Valid result contains event count and time range`() {
    val result = ChainVerificationResult.Valid(
      eventCount = 100,
      fromTs = 1000L,
      toTs = 2000L,
    )

    assertThat(result.eventCount).isEqualTo(100)
    assertThat(result.fromTs).isEqualTo(1000L)
    assertThat(result.toTs).isEqualTo(2000L)
  }

  @Test
  fun `Broken result contains break details`() {
    val result = ChainVerificationResult.Broken(
      brokenAtId = "event-123",
      brokenAtTs = 1500L,
      expectedPrevHash = "abc123",
      actualPrevHash = "def456",
    )

    assertThat(result.brokenAtId).isEqualTo("event-123")
    assertThat(result.brokenAtTs).isEqualTo(1500L)
    assertThat(result.expectedPrevHash).isEqualTo("abc123")
    assertThat(result.actualPrevHash).isEqualTo("def456")
  }

  @Test
  fun `EmptyRange result contains time range`() {
    val result = ChainVerificationResult.EmptyRange(
      fromTs = 1000L,
      toTs = 2000L,
    )

    assertThat(result.fromTs).isEqualTo(1000L)
    assertThat(result.toTs).isEqualTo(2000L)
  }

  @Test
  fun `HashMismatch result contains event details`() {
    val result = ChainVerificationResult.HashMismatch(
      eventId = "event-456",
      eventTs = 1600L,
    )

    assertThat(result.eventId).isEqualTo("event-456")
    assertThat(result.eventTs).isEqualTo(1600L)
  }

  @Test
  fun `SignatureInvalid result contains error details`() {
    val result = ChainVerificationResult.SignatureInvalid(
      eventId = "event-789",
      eventTs = 1700L,
      reason = "Invalid ECDSA signature",
    )

    assertThat(result.eventId).isEqualTo("event-789")
    assertThat(result.eventTs).isEqualTo(1700L)
    assertThat(result.reason).isEqualTo("Invalid ECDSA signature")
  }

  @Test
  fun `result types are sealed subclasses`() {
    val valid: ChainVerificationResult = ChainVerificationResult.Valid(1, 0, 1)
    val broken: ChainVerificationResult = ChainVerificationResult.Broken("id", 0, "a", "b")
    val empty: ChainVerificationResult = ChainVerificationResult.EmptyRange(0, 1)
    val hashMismatch: ChainVerificationResult = ChainVerificationResult.HashMismatch("id", 0)
    val signatureInvalid: ChainVerificationResult = ChainVerificationResult.SignatureInvalid("id", 0, "reason")

    // Test exhaustive when
    when (valid) {
      is ChainVerificationResult.Valid -> assertThat(true).isTrue()
      is ChainVerificationResult.Broken -> assertThat(false).isTrue()
      is ChainVerificationResult.EmptyRange -> assertThat(false).isTrue()
      is ChainVerificationResult.HashMismatch -> assertThat(false).isTrue()
      is ChainVerificationResult.SignatureInvalid -> assertThat(false).isTrue()
    }
  }

  @Test
  fun `AuditStatistics contains all fields`() {
    val stats = AuditStatistics(
      totalEvents = 1000,
      oldestEventTs = 100L,
      newestEventTs = 2000L,
      chainGeneration = 3,
      signedEventCount = 500,
      eventsByName = mapOf(
        "sdk_initialized" to 1,
        "event_enqueued" to 800,
        "heartbeat" to 199,
      ),
    )

    assertThat(stats.totalEvents).isEqualTo(1000)
    assertThat(stats.oldestEventTs).isEqualTo(100L)
    assertThat(stats.newestEventTs).isEqualTo(2000L)
    assertThat(stats.chainGeneration).isEqualTo(3)
    assertThat(stats.signedEventCount).isEqualTo(500)
    assertThat(stats.eventsByName).hasSize(3)
    assertThat(stats.eventsByName["event_enqueued"]).isEqualTo(800)
  }

  @Test
  fun `AuditStatistics with null timestamps`() {
    val stats = AuditStatistics(
      totalEvents = 0,
      oldestEventTs = null,
      newestEventTs = null,
      chainGeneration = 1,
      signedEventCount = 0,
      eventsByName = emptyMap(),
    )

    assertThat(stats.totalEvents).isEqualTo(0)
    assertThat(stats.oldestEventTs).isNull()
    assertThat(stats.newestEventTs).isNull()
    assertThat(stats.eventsByName).isEmpty()
  }
}
