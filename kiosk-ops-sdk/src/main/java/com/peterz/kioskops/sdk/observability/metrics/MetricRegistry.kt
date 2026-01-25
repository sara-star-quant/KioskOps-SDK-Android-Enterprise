/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability.metrics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.DoubleAdder

/**
 * In-memory metric registry for collecting and exporting metrics.
 *
 * Security (BSI SYS.3.2.2.A8): Metrics collection is designed for
 * minimal performance impact with lock-free data structures.
 *
 * @since 0.4.0
 */
class MetricRegistry {

  private val counters = ConcurrentHashMap<MetricKey, CounterData>()
  private val gauges = ConcurrentHashMap<MetricKey, GaugeData>()
  private val histograms = ConcurrentHashMap<MetricKey, HistogramData>()

  /**
   * Register or get a counter.
   */
  fun getOrCreateCounter(
    name: String,
    description: String,
    unit: String,
  ): CounterData {
    val key = MetricKey(name, MetricType.COUNTER)
    return counters.getOrPut(key) {
      CounterData(name, description, unit)
    }
  }

  /**
   * Register or get a gauge.
   */
  fun getOrCreateGauge(
    name: String,
    description: String,
    unit: String,
  ): GaugeData {
    val key = MetricKey(name, MetricType.GAUGE)
    return gauges.getOrPut(key) {
      GaugeData(name, description, unit)
    }
  }

  /**
   * Register or get a histogram.
   */
  fun getOrCreateHistogram(
    name: String,
    description: String,
    unit: String,
    boundaries: List<Double>,
  ): HistogramData {
    val key = MetricKey(name, MetricType.HISTOGRAM)
    return histograms.getOrPut(key) {
      HistogramData(name, description, unit, boundaries)
    }
  }

  /**
   * Get all metric data for export.
   */
  fun collectMetrics(): MetricSnapshot {
    return MetricSnapshot(
      counters = counters.values.toList(),
      gauges = gauges.values.toList(),
      histograms = histograms.values.toList(),
      collectedAt = System.currentTimeMillis(),
    )
  }

  /**
   * Reset all metrics (for testing).
   */
  fun reset() {
    counters.values.forEach { it.reset() }
    gauges.values.forEach { it.reset() }
    histograms.values.forEach { it.reset() }
  }

  private data class MetricKey(
    val name: String,
    val type: MetricType,
  )

  private enum class MetricType {
    COUNTER, GAUGE, HISTOGRAM
  }
}

/**
 * Counter metric data.
 *
 * @since 0.4.0
 */
class CounterData(
  val name: String,
  val description: String,
  val unit: String,
) {
  private val values = ConcurrentHashMap<Map<String, String>, AtomicLong>()

  fun add(value: Long, attributes: Map<String, String>) {
    values.getOrPut(attributes) { AtomicLong(0) }.addAndGet(value)
  }

  fun getValues(): Map<Map<String, String>, Long> =
    values.mapValues { it.value.get() }

  fun reset() {
    values.clear()
  }
}

/**
 * Gauge metric data.
 *
 * @since 0.4.0
 */
class GaugeData(
  val name: String,
  val description: String,
  val unit: String,
) {
  private val values = ConcurrentHashMap<Map<String, String>, DoubleAdder>()

  fun record(value: Double, attributes: Map<String, String>) {
    val adder = values.getOrPut(attributes) { DoubleAdder() }
    adder.reset()
    adder.add(value)
  }

  fun getValues(): Map<Map<String, String>, Double> =
    values.mapValues { it.value.sum() }

  fun reset() {
    values.clear()
  }
}

/**
 * Histogram metric data with bucket counts.
 *
 * @since 0.4.0
 */
class HistogramData(
  val name: String,
  val description: String,
  val unit: String,
  val boundaries: List<Double>,
) {
  private val buckets = ConcurrentHashMap<Map<String, String>, BucketData>()

  fun record(value: Double, attributes: Map<String, String>) {
    buckets.getOrPut(attributes) { BucketData(boundaries) }.record(value)
  }

  fun getValues(): Map<Map<String, String>, BucketSnapshot> =
    buckets.mapValues { it.value.snapshot() }

  fun reset() {
    buckets.clear()
  }

  /**
   * Internal bucket accumulator.
   */
  class BucketData(private val boundaries: List<Double>) {
    private val counts = LongArray(boundaries.size + 1)
    private var sum = 0.0
    private var count = 0L
    private var min = Double.MAX_VALUE
    private var max = Double.MIN_VALUE

    @Synchronized
    fun record(value: Double) {
      count++
      sum += value
      if (value < min) min = value
      if (value > max) max = value

      // Find bucket
      val bucketIndex = boundaries.indexOfFirst { value <= it }
      val idx = if (bucketIndex < 0) counts.size - 1 else bucketIndex
      counts[idx]++
    }

    @Synchronized
    fun snapshot() = BucketSnapshot(
      boundaries = boundaries,
      counts = counts.toList(),
      sum = sum,
      count = count,
      min = if (count > 0) min else 0.0,
      max = if (count > 0) max else 0.0,
    )
  }
}

/**
 * Snapshot of histogram bucket data.
 *
 * @since 0.4.0
 */
data class BucketSnapshot(
  val boundaries: List<Double>,
  val counts: List<Long>,
  val sum: Double,
  val count: Long,
  val min: Double,
  val max: Double,
) {
  val average: Double
    get() = if (count > 0) sum / count else 0.0
}

/**
 * Snapshot of all metrics at a point in time.
 *
 * @since 0.4.0
 */
data class MetricSnapshot(
  val counters: List<CounterData>,
  val gauges: List<GaugeData>,
  val histograms: List<HistogramData>,
  val collectedAt: Long,
)
