/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability.metrics

/**
 * Prometheus text format exporter for metrics.
 *
 * Exports metrics in Prometheus exposition format for scraping.
 * Intended for debug builds and local development.
 *
 * Security: Only available in debug builds. Do not expose in production
 * without authentication.
 *
 * @property registry The metric registry to export from
 *
 * @since 0.4.0
 */
class PrometheusExporter(
  private val registry: MetricRegistry,
) {

  /**
   * Export all metrics in Prometheus text format.
   *
   * @return Prometheus exposition format text
   */
  fun export(): String {
    val snapshot = registry.collectMetrics()
    return buildString {
      // Counters
      snapshot.counters.forEach { counter ->
        appendMetricHelp(counter.name, counter.description, "counter")
        counter.getValues().forEach { (attrs, value) ->
          appendMetricLine(counter.name, attrs, value.toDouble())
        }
        appendLine()
      }

      // Gauges
      snapshot.gauges.forEach { gauge ->
        appendMetricHelp(gauge.name, gauge.description, "gauge")
        gauge.getValues().forEach { (attrs, value) ->
          appendMetricLine(gauge.name, attrs, value)
        }
        appendLine()
      }

      // Histograms
      snapshot.histograms.forEach { histogram ->
        appendMetricHelp(histogram.name, histogram.description, "histogram")
        histogram.getValues().forEach { (attrs, bucketSnapshot) ->
          appendHistogramLines(histogram.name, attrs, bucketSnapshot)
        }
        appendLine()
      }
    }
  }

  private fun StringBuilder.appendMetricHelp(
    name: String,
    description: String,
    type: String,
  ) {
    val sanitizedName = sanitizeMetricName(name)
    if (description.isNotEmpty()) {
      appendLine("# HELP $sanitizedName $description")
    }
    appendLine("# TYPE $sanitizedName $type")
  }

  private fun StringBuilder.appendMetricLine(
    name: String,
    attributes: Map<String, String>,
    value: Double,
  ) {
    val sanitizedName = sanitizeMetricName(name)
    val labels = formatLabels(attributes)
    appendLine("$sanitizedName$labels $value")
  }

  private fun StringBuilder.appendHistogramLines(
    name: String,
    attributes: Map<String, String>,
    snapshot: BucketSnapshot,
  ) {
    val sanitizedName = sanitizeMetricName(name)
    val baseLabels = formatLabels(attributes)

    // Bucket counts (cumulative)
    var cumulative = 0L
    snapshot.boundaries.forEachIndexed { index, boundary ->
      cumulative += snapshot.counts.getOrElse(index) { 0 }
      val bucketLabels = formatLabels(attributes + ("le" to boundary.toString()))
      appendLine("${sanitizedName}_bucket$bucketLabels $cumulative")
    }
    // +Inf bucket
    cumulative += snapshot.counts.lastOrNull() ?: 0
    val infLabels = formatLabels(attributes + ("le" to "+Inf"))
    appendLine("${sanitizedName}_bucket$infLabels $cumulative")

    // Sum and count
    appendLine("${sanitizedName}_sum$baseLabels ${snapshot.sum}")
    appendLine("${sanitizedName}_count$baseLabels ${snapshot.count}")
  }

  private fun formatLabels(attributes: Map<String, String>): String {
    if (attributes.isEmpty()) return ""
    return attributes.entries
      .sortedBy { it.key }
      .joinToString(",", prefix = "{", postfix = "}") { (k, v) ->
        "${sanitizeLabelName(k)}=\"${escapeLabelValue(v)}\""
      }
  }

  private fun sanitizeMetricName(name: String): String {
    return name.replace(".", "_").replace("-", "_")
  }

  private fun sanitizeLabelName(name: String): String {
    return name.replace(".", "_").replace("-", "_")
  }

  private fun escapeLabelValue(value: String): String {
    return value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
  }

  /**
   * Export metrics with metadata header.
   *
   * @return Prometheus format with comments
   */
  fun exportWithHeader(): String {
    return buildString {
      appendLine("# KioskOps SDK Metrics")
      appendLine("# Exported at: ${System.currentTimeMillis()}")
      appendLine()
      append(export())
    }
  }

  companion object {
    /**
     * Content type for Prometheus exposition format.
     */
    const val CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8"
  }
}
