/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability.metrics

import com.peterz.kioskops.sdk.observability.ObservabilityPolicy

/**
 * KioskOps SDK meter implementation.
 *
 * Creates metric instruments backed by the shared MetricRegistry.
 *
 * Built-in SDK metrics (semantic conventions):
 * - kioskops.queue.depth (gauge) - Current event queue depth
 * - kioskops.queue.quarantined (gauge) - Quarantined events count
 * - kioskops.sync.duration_ms (histogram) - Sync operation duration
 * - kioskops.sync.events_sent (counter) - Total events sent
 * - kioskops.sync.events_failed (counter) - Failed event sends
 * - kioskops.diagnostics.exports (counter) - Diagnostics export count
 * - kioskops.heartbeat.count (counter) - Heartbeat count
 *
 * @property instrumentationName Name of the instrumentation
 * @property registry Shared metric registry
 *
 * @since 0.4.0
 */
class KioskOpsMeter(
  private val instrumentationName: String,
  private val registry: MetricRegistry,
) : Meter {

  override fun counterBuilder(name: String): CounterBuilder {
    return KioskOpsCounterBuilder(name, registry)
  }

  override fun gaugeBuilder(name: String): GaugeBuilder {
    return KioskOpsGaugeBuilder(name, registry)
  }

  override fun histogramBuilder(name: String): HistogramBuilder {
    return KioskOpsHistogramBuilder(name, registry)
  }
}

/**
 * Counter builder implementation.
 *
 * @since 0.4.0
 */
private class KioskOpsCounterBuilder(
  private val name: String,
  private val registry: MetricRegistry,
) : CounterBuilder {
  private var description: String = ""
  private var unit: String = ""

  override fun setDescription(description: String): CounterBuilder {
    this.description = description
    return this
  }

  override fun setUnit(unit: String): CounterBuilder {
    this.unit = unit
    return this
  }

  override fun build(): Counter {
    val data = registry.getOrCreateCounter(name, description, unit)
    return KioskOpsCounter(data)
  }
}

/**
 * Counter implementation.
 *
 * @since 0.4.0
 */
private class KioskOpsCounter(
  private val data: CounterData,
) : Counter {
  override fun add(value: Long, attributes: Map<String, String>) {
    require(value >= 0) { "Counter values must be non-negative" }
    data.add(value, attributes)
  }
}

/**
 * Gauge builder implementation.
 *
 * @since 0.4.0
 */
private class KioskOpsGaugeBuilder(
  private val name: String,
  private val registry: MetricRegistry,
) : GaugeBuilder {
  private var description: String = ""
  private var unit: String = ""

  override fun setDescription(description: String): GaugeBuilder {
    this.description = description
    return this
  }

  override fun setUnit(unit: String): GaugeBuilder {
    this.unit = unit
    return this
  }

  override fun build(): Gauge {
    val data = registry.getOrCreateGauge(name, description, unit)
    return KioskOpsGauge(data)
  }
}

/**
 * Gauge implementation.
 *
 * @since 0.4.0
 */
private class KioskOpsGauge(
  private val data: GaugeData,
) : Gauge {
  override fun record(value: Double, attributes: Map<String, String>) {
    data.record(value, attributes)
  }
}

/**
 * Histogram builder implementation.
 *
 * @since 0.4.0
 */
private class KioskOpsHistogramBuilder(
  private val name: String,
  private val registry: MetricRegistry,
) : HistogramBuilder {
  private var description: String = ""
  private var unit: String = ""
  private var boundaries: List<Double> = DEFAULT_BOUNDARIES

  override fun setDescription(description: String): HistogramBuilder {
    this.description = description
    return this
  }

  override fun setUnit(unit: String): HistogramBuilder {
    this.unit = unit
    return this
  }

  override fun setExplicitBucketBoundaries(boundaries: List<Double>): HistogramBuilder {
    this.boundaries = boundaries.sorted()
    return this
  }

  override fun build(): Histogram {
    val data = registry.getOrCreateHistogram(name, description, unit, boundaries)
    return KioskOpsHistogram(data)
  }

  companion object {
    // Default boundaries for latency histograms (in ms)
    private val DEFAULT_BOUNDARIES = listOf(
      5.0, 10.0, 25.0, 50.0, 75.0, 100.0, 250.0, 500.0, 750.0,
      1000.0, 2500.0, 5000.0, 7500.0, 10000.0,
    )
  }
}

/**
 * Histogram implementation.
 *
 * @since 0.4.0
 */
private class KioskOpsHistogram(
  private val data: HistogramData,
) : Histogram {
  override fun record(value: Double, attributes: Map<String, String>) {
    require(value >= 0) { "Histogram values must be non-negative" }
    data.record(value, attributes)
  }
}

/**
 * KioskOps meter provider implementation.
 *
 * @since 0.4.0
 */
class KioskOpsMeterProvider(
  private val policyProvider: () -> ObservabilityPolicy,
  private val registry: MetricRegistry = MetricRegistry(),
) : MeterProvider {

  override fun getMeter(instrumentationName: String): Meter {
    val policy = policyProvider()
    return if (policy.metricsEnabled) {
      KioskOpsMeter(instrumentationName, registry)
    } else {
      NoOpMeter
    }
  }

  /**
   * Get the underlying registry for export.
   */
  fun getRegistry(): MetricRegistry = registry

  companion object {
    const val SDK_INSTRUMENTATION_NAME = "kioskops-sdk"

    /**
     * Create a provider from observability policy.
     */
    fun fromPolicy(policyProvider: () -> ObservabilityPolicy): MeterProvider {
      val policy = policyProvider()
      return if (policy.metricsEnabled) {
        KioskOpsMeterProvider(policyProvider)
      } else {
        NoOpMeterProvider
      }
    }
  }
}
