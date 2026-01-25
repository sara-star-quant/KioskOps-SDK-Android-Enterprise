/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability.tracing

import com.peterz.kioskops.sdk.observability.CorrelationContext
import com.peterz.kioskops.sdk.observability.ObservabilityPolicy
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * KioskOps SDK tracer implementation.
 *
 * Creates spans with automatic correlation context integration
 * and configurable sampling.
 *
 * Standards: Follows OpenTelemetry Semantic Conventions 1.25.0.
 *
 * @property instrumentationName Name of the instrumentation (e.g., "kioskops-sdk")
 * @property instrumentationVersion Version of the instrumentation
 * @property policyProvider Provides current observability policy
 * @property exporter Optional span exporter for sending completed spans
 *
 * @since 0.4.0
 */
class KioskOpsTracer(
  private val instrumentationName: String,
  private val instrumentationVersion: String?,
  private val policyProvider: () -> ObservabilityPolicy,
  private val exporter: SpanExporter? = null,
) : Tracer {

  override fun spanBuilder(spanName: String): SpanBuilder {
    return KioskOpsSpanBuilder(
      spanName = spanName,
      instrumentationName = instrumentationName,
      policyProvider = policyProvider,
      exporter = exporter,
    )
  }
}

/**
 * KioskOps span builder implementation.
 *
 * @since 0.4.0
 */
internal class KioskOpsSpanBuilder(
  private var spanName: String,
  private val instrumentationName: String,
  private val policyProvider: () -> ObservabilityPolicy,
  private val exporter: SpanExporter?,
) : SpanBuilder {

  private var parentContext: SpanContext? = null
  private var spanKind: SpanKind = SpanKind.INTERNAL
  private val attributes = ConcurrentHashMap<String, Any>()
  private var startTimestamp: Instant? = null

  override fun setParent(context: SpanContext?): SpanBuilder {
    parentContext = context
    return this
  }

  override fun setSpanKind(kind: SpanKind): SpanBuilder {
    spanKind = kind
    return this
  }

  override fun setAttribute(key: String, value: String): SpanBuilder {
    attributes[key] = value
    return this
  }

  override fun setAttribute(key: String, value: Long): SpanBuilder {
    attributes[key] = value
    return this
  }

  override fun setAttribute(key: String, value: Double): SpanBuilder {
    attributes[key] = value
    return this
  }

  override fun setAttribute(key: String, value: Boolean): SpanBuilder {
    attributes[key] = value
    return this
  }

  override fun setAttributes(attributes: Map<String, Any>): SpanBuilder {
    this.attributes.putAll(attributes)
    return this
  }

  override fun setStartTimestamp(timestamp: Instant): SpanBuilder {
    startTimestamp = timestamp
    return this
  }

  override fun startSpan(): Span {
    val policy = policyProvider()

    // Check if tracing is enabled
    if (!policy.tracingEnabled) {
      return NoOpSpan
    }

    // Apply sampling
    if (!shouldSample(policy.traceSampleRate)) {
      return NoOpSpan
    }

    // Determine parent context (from explicit parent or correlation context)
    val parent = parentContext ?: getContextFromCorrelation()

    // Generate span context
    val traceId = parent?.traceId ?: CorrelationContext.generateTraceId()
    val spanId = CorrelationContext.generateSpanId()
    val parentSpanId = parent?.spanId

    val context = SpanContext(
      traceId = traceId,
      spanId = spanId,
      parentSpanId = parentSpanId,
      traceFlags = SpanContext.SAMPLED,
    )

    // Update correlation context
    CorrelationContext.set(CorrelationContext.KEY_TRACE_ID, traceId)
    CorrelationContext.set(CorrelationContext.KEY_SPAN_ID, spanId)
    parentSpanId?.let { CorrelationContext.set(CorrelationContext.KEY_PARENT_SPAN_ID, it) }

    return KioskOpsSpan(
      name = spanName,
      context = context,
      kind = spanKind,
      instrumentationName = instrumentationName,
      startTime = startTimestamp ?: Instant.now(),
      initialAttributes = attributes.toMap(),
      exporter = exporter,
    )
  }

  private fun shouldSample(sampleRate: Double): Boolean {
    return Math.random() < sampleRate
  }

  private fun getContextFromCorrelation(): SpanContext? {
    val traceId = CorrelationContext.traceId ?: return null
    val spanId = CorrelationContext.spanId ?: return null
    return SpanContext(traceId = traceId, spanId = spanId)
  }
}

/**
 * KioskOps span implementation.
 *
 * @since 0.4.0
 */
internal class KioskOpsSpan(
  private var name: String,
  override val context: SpanContext,
  private val kind: SpanKind,
  private val instrumentationName: String,
  private val startTime: Instant,
  initialAttributes: Map<String, Any>,
  private val exporter: SpanExporter?,
) : Span {

  private val attributes = ConcurrentHashMap<String, Any>(initialAttributes)
  private val events = CopyOnWriteArrayList<SpanEvent>()
  private var status: SpanStatus = SpanStatus.UNSET
  private var statusDescription: String? = null
  private var endTime: Instant? = null
  private var ended = false

  override fun setAttribute(key: String, value: String): Span {
    if (!ended) attributes[key] = value
    return this
  }

  override fun setAttribute(key: String, value: Long): Span {
    if (!ended) attributes[key] = value
    return this
  }

  override fun setAttribute(key: String, value: Double): Span {
    if (!ended) attributes[key] = value
    return this
  }

  override fun setAttribute(key: String, value: Boolean): Span {
    if (!ended) attributes[key] = value
    return this
  }

  override fun addEvent(name: String, attributes: Map<String, String>): Span {
    return addEvent(name, Instant.now(), attributes)
  }

  override fun addEvent(name: String, timestamp: Instant, attributes: Map<String, String>): Span {
    if (!ended) {
      events.add(SpanEvent(name, timestamp, attributes))
    }
    return this
  }

  override fun setStatus(status: SpanStatus, description: String?): Span {
    if (!ended) {
      this.status = status
      this.statusDescription = description
    }
    return this
  }

  override fun recordException(exception: Throwable): Span {
    if (!ended) {
      setStatus(SpanStatus.ERROR, exception.message)
      addEvent("exception", mapOf(
        "exception.type" to exception::class.java.name,
        "exception.message" to (exception.message ?: ""),
        "exception.stacktrace" to exception.stackTraceToString().take(4000),
      ))
    }
    return this
  }

  override fun updateName(name: String): Span {
    if (!ended) this.name = name
    return this
  }

  override fun end() {
    end(Instant.now())
  }

  override fun end(timestamp: Instant) {
    if (ended) return
    synchronized(this) {
      if (ended) return
      ended = true
      endTime = timestamp
    }

    // Export span data
    exporter?.let { exp ->
      val spanData = toSpanData()
      exp.export(listOf(spanData))
    }
  }

  override fun isRecording(): Boolean = !ended

  private fun toSpanData(): SpanData {
    return SpanData(
      name = name,
      context = context,
      kind = kind,
      startTime = startTime,
      endTime = endTime ?: Instant.now(),
      attributes = attributes.toMap(),
      events = events.toList(),
      status = status,
      statusDescription = statusDescription,
      instrumentationName = instrumentationName,
    )
  }
}

/**
 * Completed span data for export.
 *
 * @since 0.4.0
 */
data class SpanData(
  val name: String,
  val context: SpanContext,
  val kind: SpanKind,
  val startTime: Instant,
  val endTime: Instant,
  val attributes: Map<String, Any>,
  val events: List<SpanEvent>,
  val status: SpanStatus,
  val statusDescription: String?,
  val instrumentationName: String,
) {
  val durationNanos: Long
    get() = endTime.toEpochMilli() - startTime.toEpochMilli() * 1_000_000
}

/**
 * Interface for exporting completed spans.
 *
 * @since 0.4.0
 */
interface SpanExporter {
  /**
   * Export a batch of span data.
   *
   * @param spans List of completed spans to export
   * @return Export result
   */
  fun export(spans: List<SpanData>): ExportResult

  /**
   * Flush any buffered spans.
   */
  fun flush(): ExportResult

  /**
   * Shutdown the exporter.
   */
  fun shutdown(): ExportResult
}

/**
 * Result of an export operation.
 *
 * @since 0.4.0
 */
enum class ExportResult {
  SUCCESS,
  FAILURE,
}
