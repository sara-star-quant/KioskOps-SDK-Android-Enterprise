/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.sync

import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.transport.EventAck
import org.junit.Test

class SyncDecisionsTest {
  private val now = 1_000_000L
  private val sixHoursMs = 21_600_000L

  private fun ack(
    accepted: Boolean,
    retryable: Boolean = true,
    error: String? = null,
    serverEventId: String? = null,
  ) = EventAck("e1", "k1", accepted, retryable, error, serverEventId)

  @Test fun missingAckRetriesWithBumpedAttempts() {
    assertThat(SyncDecisions.decideFromAck(0, null, 5, now))
      .isEqualTo(EventDecision.RetryAt(now + 10_000L, "missing_ack", bumpAttempts = true))
    assertThat(SyncDecisions.decideFromAck(3, null, 5, now))
      .isEqualTo(EventDecision.RetryAt(now + 80_000L, "missing_ack", bumpAttempts = true))
  }

  @Test fun acceptedAckIsSent() {
    assertThat(SyncDecisions.decideFromAck(2, ack(true, serverEventId = "srv-1"), 5, now))
      .isEqualTo(EventDecision.Sent("srv-1"))
    assertThat(SyncDecisions.decideFromAck(2, ack(true), 5, now))
      .isEqualTo(EventDecision.Sent(null))
  }

  @Test fun retryableRejectBelowCapDefaultsErrorToRejected() {
    assertThat(SyncDecisions.decideFromAck(3, ack(false), 10, now))
      .isEqualTo(EventDecision.RetryAt(now + 80_000L, "rejected", bumpAttempts = true))
  }

  @Test fun retryableRejectBelowCapKeepsAckError() {
    assertThat(SyncDecisions.decideFromAck(0, ack(false, error = "busy"), 10, now))
      .isEqualTo(EventDecision.RetryAt(now + 10_000L, "busy", bumpAttempts = true))
  }

  @Test fun nonRetryableRejectQuarantines() {
    assertThat(SyncDecisions.decideFromAck(0, ack(false, retryable = false, error = "bad"), 10, now))
      .isEqualTo(EventDecision.Quarantine("server_non_retryable", "bad"))
    assertThat(SyncDecisions.decideFromAck(0, ack(false, retryable = false), 10, now))
      .isEqualTo(EventDecision.Quarantine("server_non_retryable", "rejected"))
  }

  @Test fun retryableRejectAtCapQuarantinesWithMaxAttemptsReason() {
    assertThat(SyncDecisions.decideFromAck(4, ack(false), 5, now))
      .isEqualTo(EventDecision.Quarantine("max_attempts_exceeded", "max_attempts_exceeded"))
  }

  @Test fun rejectAtCapKeepsAckError() {
    assertThat(SyncDecisions.decideFromAck(4, ack(false, error = "boom"), 5, now))
      .isEqualTo(EventDecision.Quarantine("max_attempts_exceeded", "boom"))
  }

  @Test fun nonRetryableAndMaxedOutReportsServerNonRetryable() {
    assertThat(SyncDecisions.decideFromAck(4, ack(false, retryable = false), 5, now))
      .isEqualTo(EventDecision.Quarantine("server_non_retryable", "max_attempts_exceeded"))
  }

  @Test fun batchFailureRetriesWithoutBump() {
    assertThat(SyncDecisions.decideBatchFailure(0, "http 503", now))
      .isEqualTo(EventDecision.RetryAt(now + 10_000L, "http 503", bumpAttempts = false))
    assertThat(SyncDecisions.decideBatchFailure(20, "x", now))
      .isEqualTo(EventDecision.RetryAt(now + 10_240_000L, "x", bumpAttempts = false))
  }

  private fun check(attempts: Int, max: Int, retryable: Boolean) {
    val d = SyncDecisions.decideFromAck(attempts, ack(false, retryable), max, now)
    if (d is EventDecision.RetryAt) {
      assertThat(d.atMs).isAtLeast(now)
      assertThat(d.atMs).isAtMost(now + sixHoursMs)
    }
    if (d is EventDecision.Quarantine) assertThat(!retryable || attempts + 1 >= max).isTrue()
    if (retryable && attempts + 1 < max) assertThat(d).isInstanceOf(EventDecision.RetryAt::class.java)
    assertThat(SyncDecisions.decideBatchFailure(attempts, "m", now).bumpAttempts).isFalse()
  }

  @Test fun rejectedAckProperties() {
    for (attempts in 0..50) for (max in 1..10) for (retryable in listOf(true, false)) {
      check(attempts, max, retryable)
    }
  }

  @Test fun batchFailureDelayIsNonDecreasing() {
    var prev = 0L
    for (attempts in 0..50) {
      val at = SyncDecisions.decideBatchFailure(attempts, "m", now).atMs
      assertThat(at).isAtLeast(prev)
      prev = at
    }
  }
}
