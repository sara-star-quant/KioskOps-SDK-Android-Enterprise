/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.observability.metrics

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MetricRegistryExtendedTest {

  private lateinit var registry: MetricRegistry

  @Before
  fun setUp() {
    registry = MetricRegistry()
  }

  // -- Counter tests --

  @Test
  fun `counter add and getValue returns correct total`() {
    val counter = registry.getOrCreateCounter("requests_total", "Total requests", "1")
    counter.add(1, emptyMap())
    counter.add(4, emptyMap())
    counter.add(5, emptyMap())

    val values = counter.getValues()
    assertThat(values[emptyMap()]).isEqualTo(10)
  }

  @Test
  fun `counter tracks separate attribute sets independently`() {
    val counter = registry.getOrCreateCounter("http_requests", "HTTP requests", "1")
    counter.add(3, mapOf("method" to "GET"))
    counter.add(7, mapOf("method" to "POST"))
    counter.add(2, mapOf("method" to "GET"))

    val values = counter.getValues()
    assertThat(values[mapOf("method" to "GET")]).isEqualTo(5)
    assertThat(values[mapOf("method" to "POST")]).isEqualTo(7)
  }

  @Test
  fun `counter add zero does not change value`() {
    val counter = registry.getOrCreateCounter("noop_counter", "No-op", "1")
    counter.add(10, emptyMap())
    counter.add(0, emptyMap())

    assertThat(counter.getValues()[emptyMap()]).isEqualTo(10)
  }

  @Test
  fun `counter reset clears all values`() {
    val counter = registry.getOrCreateCounter("reset_counter", "Resettable", "1")
    counter.add(5, mapOf("env" to "prod"))
    counter.add(3, mapOf("env" to "staging"))
    counter.reset()

    assertThat(counter.getValues()).isEmpty()
  }

  @Test
  fun `counter getValues is empty before any add`() {
    val counter = registry.getOrCreateCounter("empty_counter", "Empty", "1")
    assertThat(counter.getValues()).isEmpty()
  }

  // -- Gauge tests --

  @Test
  fun `gauge record and getValue returns latest value`() {
    val gauge = registry.getOrCreateGauge("queue_depth", "Queue depth", "events")
    gauge.record(10.0, emptyMap())
    gauge.record(25.0, emptyMap())
    gauge.record(3.0, emptyMap())

    assertThat(gauge.getValues()[emptyMap()]).isEqualTo(3.0)
  }

  @Test
  fun `gauge tracks separate attribute sets independently`() {
    val gauge = registry.getOrCreateGauge("temp", "Temperature", "celsius")
    gauge.record(22.5, mapOf("room" to "A"))
    gauge.record(19.0, mapOf("room" to "B"))

    val values = gauge.getValues()
    assertThat(values[mapOf("room" to "A")]).isEqualTo(22.5)
    assertThat(values[mapOf("room" to "B")]).isEqualTo(19.0)
  }

  @Test
  fun `gauge reset clears all values`() {
    val gauge = registry.getOrCreateGauge("reset_gauge", "Resettable", "1")
    gauge.record(42.0, emptyMap())
    gauge.reset()

    assertThat(gauge.getValues()).isEmpty()
  }

  @Test
  fun `gauge records zero correctly`() {
    val gauge = registry.getOrCreateGauge("zero_gauge", "Zero test", "1")
    gauge.record(100.0, emptyMap())
    gauge.record(0.0, emptyMap())

    assertThat(gauge.getValues()[emptyMap()]).isEqualTo(0.0)
  }

  @Test
  fun `gauge records negative values`() {
    val gauge = registry.getOrCreateGauge("neg_gauge", "Negative test", "1")
    gauge.record(-15.5, emptyMap())

    assertThat(gauge.getValues()[emptyMap()]).isEqualTo(-15.5)
  }

  // -- Histogram tests --

  @Test
  fun `histogram record and statistics are correct`() {
    val histogram = registry.getOrCreateHistogram(
      "latency", "Request latency", "ms", listOf(10.0, 50.0, 100.0),
    )
    histogram.record(5.0, emptyMap())
    histogram.record(30.0, emptyMap())
    histogram.record(75.0, emptyMap())

    val snapshot = histogram.getValues()[emptyMap()]!!
    assertThat(snapshot.count).isEqualTo(3)
    assertThat(snapshot.sum).isEqualTo(110.0)
    assertThat(snapshot.min).isEqualTo(5.0)
    assertThat(snapshot.max).isEqualTo(75.0)
    assertThat(snapshot.average).isWithin(0.01).of(36.67)
  }

  @Test
  fun `histogram buckets are assigned correctly`() {
    val histogram = registry.getOrCreateHistogram(
      "buckets_test", "Bucket test", "ms", listOf(10.0, 50.0, 100.0),
    )
    // <= 10: value 5
    histogram.record(5.0, emptyMap())
    // <= 50: value 25
    histogram.record(25.0, emptyMap())
    // <= 100: value 75
    histogram.record(75.0, emptyMap())
    // +Inf: value 200
    histogram.record(200.0, emptyMap())

    val snapshot = histogram.getValues()[emptyMap()]!!
    // Buckets: [<=10, <=50, <=100, +Inf]
    assertThat(snapshot.counts).containsExactly(1L, 1L, 1L, 1L)
  }

  @Test
  fun `histogram boundary value falls into correct bucket`() {
    val histogram = registry.getOrCreateHistogram(
      "boundary_test", "Boundary", "ms", listOf(10.0, 50.0),
    )
    // Value exactly at boundary should go into that bucket (<=)
    histogram.record(10.0, emptyMap())
    histogram.record(50.0, emptyMap())

    val snapshot = histogram.getValues()[emptyMap()]!!
    // [<=10, <=50, +Inf]
    assertThat(snapshot.counts[0]).isEqualTo(1) // 10.0 <= 10.0
    assertThat(snapshot.counts[1]).isEqualTo(1) // 50.0 <= 50.0
    assertThat(snapshot.counts[2]).isEqualTo(0) // nothing in +Inf
  }

  @Test
  fun `histogram with single value has matching min and max`() {
    val histogram = registry.getOrCreateHistogram(
      "single_val", "Single", "ms", listOf(100.0),
    )
    histogram.record(42.0, emptyMap())

    val snapshot = histogram.getValues()[emptyMap()]!!
    assertThat(snapshot.min).isEqualTo(42.0)
    assertThat(snapshot.max).isEqualTo(42.0)
    assertThat(snapshot.average).isEqualTo(42.0)
    assertThat(snapshot.count).isEqualTo(1)
  }

  @Test
  fun `histogram reset clears all bucket data`() {
    val histogram = registry.getOrCreateHistogram(
      "reset_hist", "Resettable", "ms", listOf(10.0),
    )
    histogram.record(5.0, emptyMap())
    histogram.reset()

    assertThat(histogram.getValues()).isEmpty()
  }

  @Test
  fun `histogram tracks separate attribute sets`() {
    val histogram = registry.getOrCreateHistogram(
      "multi_attr", "Multi", "ms", listOf(100.0),
    )
    histogram.record(10.0, mapOf("region" to "us"))
    histogram.record(20.0, mapOf("region" to "eu"))

    val values = histogram.getValues()
    assertThat(values).hasSize(2)
    assertThat(values[mapOf("region" to "us")]!!.sum).isEqualTo(10.0)
    assertThat(values[mapOf("region" to "eu")]!!.sum).isEqualTo(20.0)
  }

  // -- BucketSnapshot --

  @Test
  fun `BucketSnapshot average with multiple values`() {
    val snapshot = BucketSnapshot(
      boundaries = listOf(10.0, 50.0),
      counts = listOf(2L, 3L, 1L),
      sum = 120.0,
      count = 6,
      min = 2.0,
      max = 80.0,
    )
    assertThat(snapshot.average).isEqualTo(20.0)
  }

  @Test
  fun `BucketSnapshot average is zero when count is zero`() {
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

  // -- collectMetrics (getAll) --

  @Test
  fun `collectMetrics returns all registered metrics`() {
    registry.getOrCreateCounter("c1", "Counter 1", "1")
    registry.getOrCreateCounter("c2", "Counter 2", "1")
    registry.getOrCreateGauge("g1", "Gauge 1", "1")
    registry.getOrCreateHistogram("h1", "Histogram 1", "ms", listOf(10.0))

    val snapshot = registry.collectMetrics()
    assertThat(snapshot.counters).hasSize(2)
    assertThat(snapshot.gauges).hasSize(1)
    assertThat(snapshot.histograms).hasSize(1)
    assertThat(snapshot.collectedAt).isGreaterThan(0)
  }

  @Test
  fun `collectMetrics on empty registry returns empty lists`() {
    val snapshot = registry.collectMetrics()
    assertThat(snapshot.counters).isEmpty()
    assertThat(snapshot.gauges).isEmpty()
    assertThat(snapshot.histograms).isEmpty()
  }

  // -- reset clears all metrics --

  @Test
  fun `reset clears values across all metric types`() {
    val counter = registry.getOrCreateCounter("c", "Counter", "1")
    val gauge = registry.getOrCreateGauge("g", "Gauge", "1")
    val histogram = registry.getOrCreateHistogram("h", "Histogram", "ms", listOf(10.0))

    counter.add(5, emptyMap())
    gauge.record(42.0, emptyMap())
    histogram.record(7.0, emptyMap())

    registry.reset()

    assertThat(counter.getValues()).isEmpty()
    assertThat(gauge.getValues()).isEmpty()
    assertThat(histogram.getValues()).isEmpty()
  }

  // -- Concurrent counter increments --

  @Test
  fun `concurrent counter increments produce correct total`() {
    val counter = registry.getOrCreateCounter("concurrent", "Concurrent counter", "1")
    val threadCount = 8
    val incrementsPerThread = 1000
    val latch = CountDownLatch(threadCount)
    val executor = Executors.newFixedThreadPool(threadCount)

    for (ignored in 0 until threadCount) {
      executor.submit {
        try {
          for (i in 0 until incrementsPerThread) {
            counter.add(1, emptyMap())
          }
        } finally {
          latch.countDown()
        }
      }
    }

    latch.await(10, TimeUnit.SECONDS)
    executor.shutdown()

    val total = counter.getValues()[emptyMap()]
    assertThat(total).isEqualTo((threadCount * incrementsPerThread).toLong())
  }

  @Test
  fun `concurrent gauge records do not throw`() {
    val gauge = registry.getOrCreateGauge("concurrent_gauge", "Concurrent gauge", "1")
    val threadCount = 4
    val recordsPerThread = 500
    val latch = CountDownLatch(threadCount)
    val executor = Executors.newFixedThreadPool(threadCount)

    for (ignored in 0 until threadCount) {
      executor.submit {
        try {
          for (i in 0 until recordsPerThread) {
            gauge.record(i.toDouble(), emptyMap())
          }
        } finally {
          latch.countDown()
        }
      }
    }

    latch.await(10, TimeUnit.SECONDS)
    executor.shutdown()

    // Gauge should have a value (last write wins, exact value is non-deterministic)
    val values = gauge.getValues()
    assertThat(values).isNotEmpty()
  }

  // -- Metric name uniqueness --

  @Test
  fun `getOrCreateCounter returns same instance for same name`() {
    val c1 = registry.getOrCreateCounter("unique_counter", "Desc A", "1")
    val c2 = registry.getOrCreateCounter("unique_counter", "Desc B", "1")
    assertThat(c1).isSameInstanceAs(c2)
  }

  @Test
  fun `getOrCreateGauge returns same instance for same name`() {
    val g1 = registry.getOrCreateGauge("unique_gauge", "Desc A", "1")
    val g2 = registry.getOrCreateGauge("unique_gauge", "Desc B", "1")
    assertThat(g1).isSameInstanceAs(g2)
  }

  @Test
  fun `getOrCreateHistogram returns same instance for same name`() {
    val h1 = registry.getOrCreateHistogram("unique_hist", "Desc A", "ms", listOf(10.0))
    val h2 = registry.getOrCreateHistogram("unique_hist", "Desc B", "ms", listOf(50.0))
    assertThat(h1).isSameInstanceAs(h2)
  }

  @Test
  fun `different metric types with same name are independent`() {
    val counter = registry.getOrCreateCounter("shared_name", "Counter", "1")
    val gauge = registry.getOrCreateGauge("shared_name", "Gauge", "1")

    counter.add(10, emptyMap())
    gauge.record(99.0, emptyMap())

    assertThat(counter.getValues()[emptyMap()]).isEqualTo(10)
    assertThat(gauge.getValues()[emptyMap()]).isEqualTo(99.0)
  }

  // -- Counter and gauge name/description/unit properties --

  @Test
  fun `counter preserves name description and unit`() {
    val counter = registry.getOrCreateCounter("my_counter", "My counter desc", "bytes")
    assertThat(counter.name).isEqualTo("my_counter")
    assertThat(counter.description).isEqualTo("My counter desc")
    assertThat(counter.unit).isEqualTo("bytes")
  }

  @Test
  fun `gauge preserves name description and unit`() {
    val gauge = registry.getOrCreateGauge("my_gauge", "My gauge desc", "events")
    assertThat(gauge.name).isEqualTo("my_gauge")
    assertThat(gauge.description).isEqualTo("My gauge desc")
    assertThat(gauge.unit).isEqualTo("events")
  }

  @Test
  fun `histogram preserves name description unit and boundaries`() {
    val boundaries = listOf(5.0, 25.0, 100.0)
    val histogram = registry.getOrCreateHistogram("my_hist", "My histogram desc", "ms", boundaries)
    assertThat(histogram.name).isEqualTo("my_hist")
    assertThat(histogram.description).isEqualTo("My histogram desc")
    assertThat(histogram.unit).isEqualTo("ms")
    assertThat(histogram.boundaries).containsExactly(5.0, 25.0, 100.0).inOrder()
  }
}
