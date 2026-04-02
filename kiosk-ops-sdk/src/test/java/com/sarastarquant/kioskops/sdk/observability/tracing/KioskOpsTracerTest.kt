/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.observability.tracing

import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.observability.ObservabilityPolicy
import org.junit.Test

class KioskOpsTracerTest {

  private val enabledPolicy = ObservabilityPolicy(
    tracingEnabled = true,
    metricsEnabled = true,
    traceSampleRate = 1.0,
  )

  private val disabledPolicy = ObservabilityPolicy(
    tracingEnabled = false,
    metricsEnabled = false,
  )

  @Test
  fun `span builder creates span when tracing is enabled`() {
    val exported = mutableListOf<SpanData>()
    val exporter = RecordingExporter(exported)
    val tracer = KioskOpsTracer("test", "1.0", { enabledPolicy }, exporter)

    val span = tracer.spanBuilder("test-operation").startSpan()
    assertThat(span.isRecording()).isTrue()
    assertThat(span.context.traceId).isNotEmpty()
    assertThat(span.context.spanId).isNotEmpty()

    span.end()
    assertThat(span.isRecording()).isFalse()
    assertThat(exported).hasSize(1)
    assertThat(exported[0].name).isEqualTo("test-operation")
  }

  @Test
  fun `span returns NoOp when tracing is disabled`() {
    val tracer = KioskOpsTracer("test", "1.0", { disabledPolicy })
    val span = tracer.spanBuilder("test-op").startSpan()
    // NoOpSpan should not record
    assertThat(span.isRecording()).isFalse()
  }

  @Test
  fun `span records attributes`() {
    val exported = mutableListOf<SpanData>()
    val tracer = KioskOpsTracer("test", "1.0", { enabledPolicy }, RecordingExporter(exported))

    val span = tracer.spanBuilder("op")
      .setAttribute("key1", "value1")
      .setAttribute("key2", 42L)
      .setAttribute("key3", 3.14)
      .setAttribute("key4", true)
      .startSpan()

    span.setAttribute("key5", "after-start")
    span.end()

    val attrs = exported[0].attributes
    assertThat(attrs["key1"]).isEqualTo("value1")
    assertThat(attrs["key2"]).isEqualTo(42L)
    assertThat(attrs["key3"]).isEqualTo(3.14)
    assertThat(attrs["key4"]).isEqualTo(true)
    assertThat(attrs["key5"]).isEqualTo("after-start")
  }

  @Test
  fun `span ignores attributes after end`() {
    val exported = mutableListOf<SpanData>()
    val tracer = KioskOpsTracer("test", "1.0", { enabledPolicy }, RecordingExporter(exported))

    val span = tracer.spanBuilder("op").startSpan()
    span.end()
    span.setAttribute("late", "value")

    assertThat(exported[0].attributes).doesNotContainKey("late")
  }

  @Test
  fun `span records events`() {
    val exported = mutableListOf<SpanData>()
    val tracer = KioskOpsTracer("test", "1.0", { enabledPolicy }, RecordingExporter(exported))

    val span = tracer.spanBuilder("op").startSpan()
    span.addEvent("checkpoint", mapOf("step" to "1"))
    span.end()

    assertThat(exported[0].events).hasSize(1)
    assertThat(exported[0].events[0].name).isEqualTo("checkpoint")
  }

  @Test
  fun `span records exception`() {
    val exported = mutableListOf<SpanData>()
    val tracer = KioskOpsTracer("test", "1.0", { enabledPolicy }, RecordingExporter(exported))

    val span = tracer.spanBuilder("op").startSpan()
    span.recordException(RuntimeException("boom"))
    span.end()

    assertThat(exported[0].status).isEqualTo(SpanStatus.ERROR)
    assertThat(exported[0].events).isNotEmpty()
    assertThat(exported[0].events[0].name).isEqualTo("exception")
  }

  @Test
  fun `span updateName changes name`() {
    val exported = mutableListOf<SpanData>()
    val tracer = KioskOpsTracer("test", "1.0", { enabledPolicy }, RecordingExporter(exported))

    val span = tracer.spanBuilder("original").startSpan()
    span.updateName("renamed")
    span.end()

    assertThat(exported[0].name).isEqualTo("renamed")
  }

  @Test
  fun `double end is ignored`() {
    val exported = mutableListOf<SpanData>()
    val tracer = KioskOpsTracer("test", "1.0", { enabledPolicy }, RecordingExporter(exported))

    val span = tracer.spanBuilder("op").startSpan()
    span.end()
    span.end()

    assertThat(exported).hasSize(1)
  }

  @Test
  fun `span kind can be set`() {
    val exported = mutableListOf<SpanData>()
    val tracer = KioskOpsTracer("test", "1.0", { enabledPolicy }, RecordingExporter(exported))

    tracer.spanBuilder("op").setSpanKind(SpanKind.CLIENT).startSpan().end()

    assertThat(exported[0].kind).isEqualTo(SpanKind.CLIENT)
  }

  private class RecordingExporter(private val spans: MutableList<SpanData>) : SpanExporter {
    override fun export(spans: List<SpanData>): ExportResult {
      this.spans.addAll(spans)
      return ExportResult.SUCCESS
    }
    override fun flush() = ExportResult.SUCCESS
    override fun shutdown() = ExportResult.SUCCESS
  }
}
