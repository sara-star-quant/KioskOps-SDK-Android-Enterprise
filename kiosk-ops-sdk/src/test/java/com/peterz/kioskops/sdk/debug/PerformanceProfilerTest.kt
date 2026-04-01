/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.debug

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PerformanceProfilerTest {

  private val profiler = PerformanceProfiler()

  @Test
  fun `records operation timing`() {
    profiler.record("enqueue", 5)
    profiler.record("enqueue", 15)
    profiler.record("enqueue", 10)

    val stats = profiler.getStats()["enqueue"]!!
    assertThat(stats.count).isEqualTo(3)
    assertThat(stats.totalMs).isEqualTo(30)
    assertThat(stats.avgMs).isEqualTo(10.0)
    assertThat(stats.minMs).isEqualTo(5)
    assertThat(stats.maxMs).isEqualTo(15)
  }

  @Test
  fun `timed block records duration`() {
    val result = profiler.timed("compute") {
      Thread.sleep(10)
      42
    }
    assertThat(result).isEqualTo(42)
    val stats = profiler.getStats()["compute"]!!
    assertThat(stats.count).isEqualTo(1)
    assertThat(stats.totalMs).isAtLeast(0)
  }

  @Test
  fun `tracks multiple operations independently`() {
    profiler.record("enqueue", 5)
    profiler.record("sync", 100)

    val stats = profiler.getStats()
    assertThat(stats).containsKey("enqueue")
    assertThat(stats).containsKey("sync")
    assertThat(stats["enqueue"]!!.count).isEqualTo(1)
    assertThat(stats["sync"]!!.count).isEqualTo(1)
  }

  @Test
  fun `reset clears all timings`() {
    profiler.record("op", 10)
    profiler.reset()
    assertThat(profiler.getStats()).isEmpty()
  }

  @Test
  fun `empty profiler returns empty stats`() {
    assertThat(profiler.getStats()).isEmpty()
  }

  @Test
  fun `empty operation summary has zero averages`() {
    val summary = OperationTimingSummary(
      count = 0, totalMs = 0, avgMs = 0.0, minMs = 0, maxMs = 0
    )
    assertThat(summary.avgMs).isEqualTo(0.0)
  }
}
