/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability.metrics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MetricRegistryTest {

  private val registry = MetricRegistry()

  @Test
  fun `counter increments and returns values`() {
    val counter = registry.getOrCreateCounter("requests", "Total requests", "1")
    counter.add(1, mapOf("method" to "GET"))
    counter.add(3, mapOf("method" to "GET"))
    counter.add(1, mapOf("method" to "POST"))

    val values = counter.getValues()
    assertThat(values[mapOf("method" to "GET")]).isEqualTo(4)
    assertThat(values[mapOf("method" to "POST")]).isEqualTo(1)
  }

  @Test
  fun `getOrCreateCounter returns same instance for same name`() {
    val c1 = registry.getOrCreateCounter("x", "desc", "1")
    val c2 = registry.getOrCreateCounter("x", "desc", "1")
    assertThat(c1).isSameInstanceAs(c2)
  }

  @Test
  fun `gauge records latest value`() {
    val gauge = registry.getOrCreateGauge("queue_depth", "Queue depth", "events")
    gauge.record(10.0, emptyMap())
    gauge.record(25.0, emptyMap())

    assertThat(gauge.getValues()[emptyMap()]).isEqualTo(25.0)
  }

  @Test
  fun `histogram records into buckets`() {
    val histogram = registry.getOrCreateHistogram(
      "latency", "Latency", "ms", listOf(10.0, 50.0, 100.0)
    )
    histogram.record(5.0, emptyMap())
    histogram.record(25.0, emptyMap())
    histogram.record(75.0, emptyMap())
    histogram.record(200.0, emptyMap())

    val snapshot = histogram.getValues()[emptyMap()]!!
    assertThat(snapshot.count).isEqualTo(4)
    assertThat(snapshot.sum).isEqualTo(305.0)
    assertThat(snapshot.min).isEqualTo(5.0)
    assertThat(snapshot.max).isEqualTo(200.0)
    // Buckets: [<=10, <=50, <=100, +Inf] = [1, 1, 1, 1]
    assertThat(snapshot.counts).containsExactly(1L, 1L, 1L, 1L)
  }

  @Test
  fun `collectMetrics returns all registered metrics`() {
    registry.getOrCreateCounter("c1", "d", "1").add(1, emptyMap())
    registry.getOrCreateGauge("g1", "d", "1").record(42.0, emptyMap())
    registry.getOrCreateHistogram("h1", "d", "ms", listOf(10.0))
      .record(5.0, emptyMap())

    val snapshot = registry.collectMetrics()
    assertThat(snapshot.counters).hasSize(1)
    assertThat(snapshot.gauges).hasSize(1)
    assertThat(snapshot.histograms).hasSize(1)
    assertThat(snapshot.collectedAt).isGreaterThan(0)
  }

  @Test
  fun `reset clears all metric values`() {
    val counter = registry.getOrCreateCounter("c", "d", "1")
    counter.add(10, emptyMap())
    registry.reset()
    assertThat(counter.getValues()).isEmpty()
  }

  @Test
  fun `BucketSnapshot average is correct`() {
    val snapshot = BucketSnapshot(
      boundaries = listOf(10.0),
      counts = listOf(2L, 1L),
      sum = 30.0,
      count = 3,
      min = 5.0,
      max = 15.0,
    )
    assertThat(snapshot.average).isEqualTo(10.0)
  }

  @Test
  fun `BucketSnapshot average is zero when empty`() {
    val snapshot = BucketSnapshot(
      boundaries = listOf(10.0),
      counts = listOf(0L, 0L),
      sum = 0.0,
      count = 0,
      min = 0.0,
      max = 0.0,
    )
    assertThat(snapshot.average).isEqualTo(0.0)
  }
}
