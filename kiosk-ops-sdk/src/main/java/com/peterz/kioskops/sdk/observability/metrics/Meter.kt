/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability.metrics

/**
 * Interface for creating metric instruments.
 *
 * Follows OpenTelemetry Metrics API patterns.
 *
 * @since 0.4.0
 */
interface Meter {

  /**
   * Create a counter builder.
   *
   * Counters are monotonically increasing values (e.g., request count).
   */
  fun counterBuilder(name: String): CounterBuilder

  /**
   * Create a gauge builder.
   *
   * Gauges represent current values that can go up or down (e.g., queue depth).
   */
  fun gaugeBuilder(name: String): GaugeBuilder

  /**
   * Create a histogram builder.
   *
   * Histograms record distribution of values (e.g., request latency).
   */
  fun histogramBuilder(name: String): HistogramBuilder
}

/**
 * Builder for Counter instruments.
 *
 * @since 0.4.0
 */
interface CounterBuilder {
  fun setDescription(description: String): CounterBuilder
  fun setUnit(unit: String): CounterBuilder
  fun build(): Counter
}

/**
 * Monotonically increasing counter.
 *
 * @since 0.4.0
 */
interface Counter {
  fun add(value: Long, attributes: Map<String, String> = emptyMap())
  fun add(value: Long, vararg attributes: Pair<String, String>) =
    add(value, attributes.toMap())
}

/**
 * Builder for Gauge instruments.
 *
 * @since 0.4.0
 */
interface GaugeBuilder {
  fun setDescription(description: String): GaugeBuilder
  fun setUnit(unit: String): GaugeBuilder
  fun build(): Gauge
}

/**
 * Gauge that records current values.
 *
 * @since 0.4.0
 */
interface Gauge {
  fun record(value: Double, attributes: Map<String, String> = emptyMap())
  fun record(value: Double, vararg attributes: Pair<String, String>) =
    record(value, attributes.toMap())
  fun record(value: Long, attributes: Map<String, String> = emptyMap()) =
    record(value.toDouble(), attributes)
}

/**
 * Builder for Histogram instruments.
 *
 * @since 0.4.0
 */
interface HistogramBuilder {
  fun setDescription(description: String): HistogramBuilder
  fun setUnit(unit: String): HistogramBuilder
  fun setExplicitBucketBoundaries(boundaries: List<Double>): HistogramBuilder
  fun build(): Histogram
}

/**
 * Histogram for recording value distributions.
 *
 * @since 0.4.0
 */
interface Histogram {
  fun record(value: Double, attributes: Map<String, String> = emptyMap())
  fun record(value: Double, vararg attributes: Pair<String, String>) =
    record(value, attributes.toMap())
  fun record(value: Long, attributes: Map<String, String> = emptyMap()) =
    record(value.toDouble(), attributes)
}

/**
 * Provider for obtaining Meters.
 *
 * @since 0.4.0
 */
interface MeterProvider {
  fun getMeter(instrumentationName: String): Meter
}

/**
 * No-op meter provider for when metrics are disabled.
 *
 * @since 0.4.0
 */
object NoOpMeterProvider : MeterProvider {
  override fun getMeter(instrumentationName: String): Meter = NoOpMeter
}

/**
 * No-op meter implementation.
 *
 * @since 0.4.0
 */
object NoOpMeter : Meter {
  override fun counterBuilder(name: String): CounterBuilder = NoOpCounterBuilder
  override fun gaugeBuilder(name: String): GaugeBuilder = NoOpGaugeBuilder
  override fun histogramBuilder(name: String): HistogramBuilder = NoOpHistogramBuilder
}

internal object NoOpCounterBuilder : CounterBuilder {
  override fun setDescription(description: String) = this
  override fun setUnit(unit: String) = this
  override fun build(): Counter = NoOpCounter
}

internal object NoOpCounter : Counter {
  override fun add(value: Long, attributes: Map<String, String>) {}
}

internal object NoOpGaugeBuilder : GaugeBuilder {
  override fun setDescription(description: String) = this
  override fun setUnit(unit: String) = this
  override fun build(): Gauge = NoOpGauge
}

internal object NoOpGauge : Gauge {
  override fun record(value: Double, attributes: Map<String, String>) {}
}

internal object NoOpHistogramBuilder : HistogramBuilder {
  override fun setDescription(description: String) = this
  override fun setUnit(unit: String) = this
  override fun setExplicitBucketBoundaries(boundaries: List<Double>) = this
  override fun build(): Histogram = NoOpHistogram
}

internal object NoOpHistogram : Histogram {
  override fun record(value: Double, attributes: Map<String, String>) {}
}
