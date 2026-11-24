/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.sync

import kotlin.math.min

/**
 * Deterministic exponential backoff for events.
 *
 * We keep it simple and predictable for enterprise debugging. The delay starts at 10 seconds and
 * doubles per attempt until the tenth doubling, then stays at [CEILING_MS] (10,240,000 ms, about
 * 2.8 hours).
 */
object Backoff {
  /** Base delay in seconds. */
  private const val BASE_S = 10L

  /** Largest shift applied to [BASE_S]. */
  private const val MAX_SHIFT = 10

  /** The longest delay [nextDelayMs] returns: [BASE_S] doubled [MAX_SHIFT] times, in milliseconds. */
  const val CEILING_MS: Long = (BASE_S shl MAX_SHIFT) * 1000L

  fun nextDelayMs(attemptsSoFar: Int): Long {
    val exp = 1L shl min(attemptsSoFar.coerceAtLeast(0), MAX_SHIFT)
    return BASE_S * exp * 1000L
  }
}
