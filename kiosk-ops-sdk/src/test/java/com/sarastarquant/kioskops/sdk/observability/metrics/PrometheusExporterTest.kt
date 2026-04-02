/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.observability.metrics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PrometheusExporterTest {

  private val registry = MetricRegistry()
  private val exporter = PrometheusExporter(registry)

  @Test
  fun `exports counter in Prometheus format`() {
    registry.getOrCreateCounter("http_requests_total", "Total HTTP requests", "1")
      .add(42, mapOf("method" to "GET"))

    val output = exporter.export()
    assertThat(output).contains("# HELP http_requests_total Total HTTP requests")
    assertThat(output).contains("# TYPE http_requests_total counter")
    assertThat(output).contains("http_requests_total{method=\"GET\"} 42.0")
  }

  @Test
  fun `exports gauge in Prometheus format`() {
    registry.getOrCreateGauge("queue_depth", "Current queue depth", "events")
      .record(15.0, emptyMap())

    val output = exporter.export()
    assertThat(output).contains("# TYPE queue_depth gauge")
    assertThat(output).contains("queue_depth 15.0")
  }

  @Test
  fun `exports histogram with cumulative buckets`() {
    val histogram = registry.getOrCreateHistogram(
      "request_duration", "Request duration", "ms", listOf(10.0, 50.0)
    )
    histogram.record(5.0, emptyMap())
    histogram.record(25.0, emptyMap())
    histogram.record(100.0, emptyMap())

    val output = exporter.export()
    assertThat(output).contains("# TYPE request_duration histogram")
    assertThat(output).contains("request_duration_bucket{le=\"10.0\"} 1")
    assertThat(output).contains("request_duration_bucket{le=\"50.0\"} 2")
    assertThat(output).contains("request_duration_bucket{le=\"+Inf\"} 3")
    assertThat(output).contains("request_duration_sum 130.0")
    assertThat(output).contains("request_duration_count 3")
  }

  @Test
  fun `sanitizes metric names with dots and dashes`() {
    registry.getOrCreateCounter("kioskops.sdk.events-total", "Events", "1")
      .add(1, emptyMap())

    val output = exporter.export()
    assertThat(output).contains("kioskops_sdk_events_total")
  }

  @Test
  fun `escapes label values`() {
    registry.getOrCreateCounter("errors", "Errors", "1")
      .add(1, mapOf("msg" to "value with \"quotes\" and \\backslash"))

    val output = exporter.export()
    assertThat(output).contains("\\\"quotes\\\"")
    assertThat(output).contains("\\\\backslash")
  }

  @Test
  fun `exportWithHeader includes metadata`() {
    val output = exporter.exportWithHeader()
    assertThat(output).contains("# KioskOps SDK Metrics")
    assertThat(output).contains("# Exported at:")
  }

  @Test
  fun `empty registry exports empty string`() {
    val output = exporter.export()
    assertThat(output).isEmpty()
  }
}
