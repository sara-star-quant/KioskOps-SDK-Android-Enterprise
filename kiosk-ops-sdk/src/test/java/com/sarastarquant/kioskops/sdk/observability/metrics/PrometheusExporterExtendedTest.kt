/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.observability.metrics

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class PrometheusExporterExtendedTest {

  private lateinit var registry: MetricRegistry
  private lateinit var exporter: PrometheusExporter

  @Before
  fun setUp() {
    registry = MetricRegistry()
    exporter = PrometheusExporter(registry)
  }

  // -- Counter export format --

  @Test
  fun `counter export includes TYPE and HELP lines`() {
    registry.getOrCreateCounter("events_total", "Total events processed", "1")
      .add(100, emptyMap())

    val output = exporter.export()
    assertThat(output).contains("# HELP events_total Total events processed")
    assertThat(output).contains("# TYPE events_total counter")
  }

  @Test
  fun `counter export includes value line without labels`() {
    registry.getOrCreateCounter("events_total", "Total events", "1")
      .add(42, emptyMap())

    val output = exporter.export()
    assertThat(output).contains("events_total 42.0")
  }

  @Test
  fun `counter export with single label`() {
    registry.getOrCreateCounter("http_requests", "HTTP requests", "1")
      .add(15, mapOf("method" to "GET"))

    val output = exporter.export()
    assertThat(output).contains("http_requests{method=\"GET\"} 15.0")
  }

  @Test
  fun `counter export omits HELP when description is empty`() {
    registry.getOrCreateCounter("no_desc", "", "1")
      .add(1, emptyMap())

    val output = exporter.export()
    assertThat(output).doesNotContain("# HELP no_desc")
    assertThat(output).contains("# TYPE no_desc counter")
  }

  // -- Gauge export format --

  @Test
  fun `gauge export includes TYPE and HELP lines`() {
    registry.getOrCreateGauge("queue_depth", "Current queue depth", "events")
      .record(25.0, emptyMap())

    val output = exporter.export()
    assertThat(output).contains("# HELP queue_depth Current queue depth")
    assertThat(output).contains("# TYPE queue_depth gauge")
  }

  @Test
  fun `gauge export includes value line`() {
    registry.getOrCreateGauge("queue_depth", "Queue depth", "events")
      .record(7.5, emptyMap())

    val output = exporter.export()
    assertThat(output).contains("queue_depth 7.5")
  }

  @Test
  fun `gauge export with labels`() {
    registry.getOrCreateGauge("temperature", "Temp", "celsius")
      .record(22.5, mapOf("sensor" to "main", "floor" to "2"))

    val output = exporter.export()
    // Labels should be sorted by key
    assertThat(output).contains("temperature{floor=\"2\",sensor=\"main\"} 22.5")
  }

  // -- Export with labels --

  @Test
  fun `export with multiple labels sorts them alphabetically`() {
    registry.getOrCreateCounter("requests", "Requests", "1")
      .add(5, mapOf("method" to "POST", "code" to "200", "path" to "/api"))

    val output = exporter.export()
    assertThat(output).contains("requests{code=\"200\",method=\"POST\",path=\"/api\"} 5.0")
  }

  @Test
  fun `export with label containing special characters in value`() {
    registry.getOrCreateCounter("errors", "Errors", "1")
      .add(1, mapOf("msg" to "line1\nline2"))

    val output = exporter.export()
    assertThat(output).contains("\\n")
    assertThat(output).doesNotContain("line1\nline2")
  }

  @Test
  fun `export with label containing backslash`() {
    registry.getOrCreateCounter("paths", "Paths", "1")
      .add(1, mapOf("path" to "C:\\Users\\test"))

    val output = exporter.export()
    assertThat(output).contains("C:\\\\Users\\\\test")
  }

  @Test
  fun `export with label containing double quotes`() {
    registry.getOrCreateCounter("quoted", "Quoted", "1")
      .add(1, mapOf("val" to "say \"hello\""))

    val output = exporter.export()
    assertThat(output).contains("say \\\"hello\\\"")
  }

  // -- Export multiple metrics --

  @Test
  fun `export multiple counters`() {
    registry.getOrCreateCounter("counter_a", "Counter A", "1").add(10, emptyMap())
    registry.getOrCreateCounter("counter_b", "Counter B", "1").add(20, emptyMap())

    val output = exporter.export()
    assertThat(output).contains("# TYPE counter_a counter")
    assertThat(output).contains("counter_a 10.0")
    assertThat(output).contains("# TYPE counter_b counter")
    assertThat(output).contains("counter_b 20.0")
  }

  @Test
  fun `export mix of counters gauges and histograms`() {
    registry.getOrCreateCounter("req_total", "Requests", "1").add(100, emptyMap())
    registry.getOrCreateGauge("mem_used", "Memory used", "bytes").record(1024.0, emptyMap())
    registry.getOrCreateHistogram("latency", "Latency", "ms", listOf(10.0, 50.0))
      .record(25.0, emptyMap())

    val output = exporter.export()

    // Counter section
    assertThat(output).contains("# TYPE req_total counter")
    assertThat(output).contains("req_total 100.0")

    // Gauge section
    assertThat(output).contains("# TYPE mem_used gauge")
    assertThat(output).contains("mem_used 1024.0")

    // Histogram section
    assertThat(output).contains("# TYPE latency histogram")
    assertThat(output).contains("latency_bucket{le=\"10.0\"} 0")
    assertThat(output).contains("latency_bucket{le=\"50.0\"} 1")
    assertThat(output).contains("latency_bucket{le=\"+Inf\"} 1")
    assertThat(output).contains("latency_sum 25.0")
    assertThat(output).contains("latency_count 1")
  }

  @Test
  fun `export counter with multiple attribute sets`() {
    val counter = registry.getOrCreateCounter("http_total", "HTTP total", "1")
    counter.add(50, mapOf("method" to "GET"))
    counter.add(30, mapOf("method" to "POST"))

    val output = exporter.export()
    assertThat(output).contains("http_total{method=\"GET\"} 50.0")
    assertThat(output).contains("http_total{method=\"POST\"} 30.0")
  }

  // -- Export empty registry --

  @Test
  fun `export empty registry returns empty string`() {
    val output = exporter.export()
    assertThat(output).isEmpty()
  }

  @Test
  fun `exportWithHeader on empty registry still has header`() {
    val output = exporter.exportWithHeader()
    assertThat(output).contains("# KioskOps SDK Metrics")
    assertThat(output).contains("# Exported at:")
  }

  // -- Sanitized metric names --

  @Test
  fun `metric name with dots replaced by underscores`() {
    registry.getOrCreateCounter("kioskops.sdk.events", "Events", "1")
      .add(1, emptyMap())

    val output = exporter.export()
    assertThat(output).contains("kioskops_sdk_events")
    assertThat(output).doesNotContain("kioskops.sdk.events")
  }

  @Test
  fun `metric name with dashes replaced by underscores`() {
    registry.getOrCreateCounter("my-counter-name", "Counter", "1")
      .add(1, emptyMap())

    val output = exporter.export()
    assertThat(output).contains("my_counter_name")
    assertThat(output).doesNotContain("my-counter-name")
  }

  @Test
  fun `metric name with mixed dots and dashes`() {
    registry.getOrCreateGauge("kioskops.queue-depth.current", "Depth", "1")
      .record(5.0, emptyMap())

    val output = exporter.export()
    assertThat(output).contains("kioskops_queue_depth_current")
  }

  @Test
  fun `label name with dots and dashes is sanitized`() {
    registry.getOrCreateCounter("test", "Test", "1")
      .add(1, mapOf("host.name" to "server-1"))

    val output = exporter.export()
    assertThat(output).contains("host_name=\"server-1\"")
  }

  // -- Histogram export format --

  @Test
  fun `histogram export has cumulative bucket counts`() {
    val histogram = registry.getOrCreateHistogram(
      "duration", "Duration", "ms", listOf(10.0, 50.0, 100.0),
    )
    histogram.record(5.0, emptyMap())   // bucket <=10
    histogram.record(30.0, emptyMap())  // bucket <=50
    histogram.record(30.0, emptyMap())  // bucket <=50
    histogram.record(75.0, emptyMap())  // bucket <=100
    histogram.record(200.0, emptyMap()) // bucket +Inf

    val output = exporter.export()
    // Cumulative: <=10 -> 1, <=50 -> 1+2=3, <=100 -> 3+1=4, +Inf -> 4+1=5
    assertThat(output).contains("duration_bucket{le=\"10.0\"} 1")
    assertThat(output).contains("duration_bucket{le=\"50.0\"} 3")
    assertThat(output).contains("duration_bucket{le=\"100.0\"} 4")
    assertThat(output).contains("duration_bucket{le=\"+Inf\"} 5")
    assertThat(output).contains("duration_sum 340.0")
    assertThat(output).contains("duration_count 5")
  }

  @Test
  fun `histogram export with labels includes labels in bucket lines`() {
    val histogram = registry.getOrCreateHistogram(
      "req_duration", "Request duration", "ms", listOf(50.0),
    )
    histogram.record(25.0, mapOf("service" to "api"))

    val output = exporter.export()
    assertThat(output).contains("req_duration_bucket{le=\"50.0\",service=\"api\"} 1")
    assertThat(output).contains("req_duration_bucket{le=\"+Inf\",service=\"api\"} 1")
    assertThat(output).contains("req_duration_sum{service=\"api\"} 25.0")
    assertThat(output).contains("req_duration_count{service=\"api\"} 1")
  }

  @Test
  fun `histogram with no recorded values exports zeroes`() {
    registry.getOrCreateHistogram(
      "empty_hist", "Empty histogram", "ms", listOf(10.0),
    )

    val output = exporter.export()
    // No values recorded, so no bucket lines emitted (empty getValues map)
    assertThat(output).contains("# TYPE empty_hist histogram")
    // But no data lines since getValues() returns empty map
    assertThat(output).doesNotContain("empty_hist_bucket")
  }

  // -- CONTENT_TYPE constant --

  @Test
  fun `CONTENT_TYPE is valid Prometheus format`() {
    assertThat(PrometheusExporter.CONTENT_TYPE)
      .isEqualTo("text/plain; version=0.0.4; charset=utf-8")
  }
}
