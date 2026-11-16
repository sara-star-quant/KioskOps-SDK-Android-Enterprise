/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.sync

import com.sarastarquant.kioskops.sdk.transport.EventAck

/** What to do with one queued event after a send attempt. */
internal sealed interface EventDecision {
  data class Sent(val serverEventId: String?) : EventDecision

  /** Retry at [atMs]; [bumpAttempts] says whether the event's attempt counter increments. */
  data class RetryAt(val atMs: Long, val error: String, val bumpAttempts: Boolean) : EventDecision

  data class Quarantine(val reason: String, val error: String) : EventDecision
}

/** Pure per-event outcome rules; no I/O, no clock reads. */
internal object SyncDecisions {
  fun decideFromAck(attempts: Int, ack: EventAck?, maxAttempts: Int, nowMs: Long): EventDecision {
    val retryAt = nowMs + Backoff.nextDelayMs(attempts)
    val maxedOut = (attempts + 1) >= maxAttempts
    return when {
      ack == null -> EventDecision.RetryAt(retryAt, "missing_ack", bumpAttempts = true)
      ack.accepted -> EventDecision.Sent(ack.serverEventId)
      !ack.retryable -> EventDecision.Quarantine("server_non_retryable", rejectError(ack, maxedOut))
      maxedOut -> EventDecision.Quarantine("max_attempts_exceeded", rejectError(ack, maxedOut))
      else -> EventDecision.RetryAt(retryAt, rejectError(ack, maxedOut), bumpAttempts = true)
    }
  }

  private fun rejectError(ack: EventAck, maxedOut: Boolean): String =
    ack.error ?: if (maxedOut) "max_attempts_exceeded" else "rejected"

  fun decideBatchFailure(attempts: Int, message: String, nowMs: Long): EventDecision.RetryAt =
    EventDecision.RetryAt(nowMs + Backoff.nextDelayMs(attempts), message, bumpAttempts = false)
}
