package com.peterz.kioskops.sdk.sync

import kotlin.math.min

/**
 * Deterministic exponential backoff for events.
 *
 * We keep it simple and predictable for enterprise debugging.
 */
object Backoff {
  /** Base delay in seconds. */
  private const val BASE_S = 10L
  /** Max delay in seconds. */
  private const val MAX_S = 6L * 60L * 60L // 6 hours

  fun nextDelayMs(attemptsSoFar: Int): Long {
    val exp = 1L shl min(attemptsSoFar.coerceAtLeast(0), 10)
    val seconds = min(BASE_S * exp, MAX_S)
    return seconds * 1000L
  }
}
