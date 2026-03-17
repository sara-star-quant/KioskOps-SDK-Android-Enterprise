/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.debug

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Records operation timings for debug builds.
 *
 * Tracks enqueue, validation, PII scan, anomaly detection, encryption, and sync operations.
 *
 * @since 0.5.0
 */
class PerformanceProfiler {

  private val timings = ConcurrentHashMap<String, OperationStats>()

  /**
   * Record a timed operation.
   *
   * @param operation Operation name (e.g., "enqueue", "validation", "pii_scan").
   * @param durationMs Duration in milliseconds.
   */
  fun record(operation: String, durationMs: Long) {
    timings.getOrPut(operation) { OperationStats() }.record(durationMs)
  }

  /**
   * Execute a block and record its timing.
   */
  inline fun <T> timed(operation: String, block: () -> T): T {
    val start = System.nanoTime()
    return try {
      block()
    } finally {
      val durationMs = (System.nanoTime() - start) / 1_000_000
      record(operation, durationMs)
    }
  }

  /**
   * Get stats for all recorded operations.
   */
  fun getStats(): Map<String, OperationTimingSummary> {
    return timings.mapValues { (_, stats) -> stats.summary() }
  }

  /**
   * Reset all collected timings.
   */
  fun reset() {
    timings.clear()
  }
}

internal class OperationStats {
  private val count = AtomicLong(0)
  private val totalMs = AtomicLong(0)
  @Volatile private var minMs = Long.MAX_VALUE
  @Volatile private var maxMs = Long.MIN_VALUE

  @Synchronized
  fun record(durationMs: Long) {
    count.incrementAndGet()
    totalMs.addAndGet(durationMs)
    if (durationMs < minMs) minMs = durationMs
    if (durationMs > maxMs) maxMs = durationMs
  }

  fun summary(): OperationTimingSummary {
    val c = count.get()
    return OperationTimingSummary(
      count = c,
      totalMs = totalMs.get(),
      avgMs = if (c > 0) totalMs.get().toDouble() / c else 0.0,
      minMs = if (c > 0) minMs else 0,
      maxMs = if (c > 0) maxMs else 0,
    )
  }
}

/**
 * Summary of timing statistics for an operation.
 * @since 0.5.0
 */
data class OperationTimingSummary(
  val count: Long,
  val totalMs: Long,
  val avgMs: Double,
  val minMs: Long,
  val maxMs: Long,
)
