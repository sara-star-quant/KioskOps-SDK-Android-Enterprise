/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability.tracing

/**
 * Creates spans for distributed tracing.
 *
 * Tracers are obtained from a TracerProvider and are typically
 * associated with an instrumentation library name.
 *
 * @since 0.4.0
 */
interface Tracer {

  /**
   * Create a span builder for a new span.
   *
   * @param spanName The name of the span
   * @return A SpanBuilder for configuring and starting the span
   */
  fun spanBuilder(spanName: String): SpanBuilder
}

/**
 * Builder for configuring and starting spans.
 *
 * @since 0.4.0
 */
interface SpanBuilder {

  /**
   * Set the parent span context.
   *
   * @param context Parent span context, or null for root span
   * @return This builder for chaining
   */
  fun setParent(context: SpanContext?): SpanBuilder

  /**
   * Set the span kind.
   *
   * @param kind The span kind (CLIENT, SERVER, INTERNAL, etc.)
   * @return This builder for chaining
   */
  fun setSpanKind(kind: SpanKind): SpanBuilder

  /**
   * Set a string attribute on the span.
   */
  fun setAttribute(key: String, value: String): SpanBuilder

  /**
   * Set a long attribute on the span.
   */
  fun setAttribute(key: String, value: Long): SpanBuilder

  /**
   * Set a double attribute on the span.
   */
  fun setAttribute(key: String, value: Double): SpanBuilder

  /**
   * Set a boolean attribute on the span.
   */
  fun setAttribute(key: String, value: Boolean): SpanBuilder

  /**
   * Set multiple attributes at once.
   */
  fun setAttributes(attributes: Map<String, Any>): SpanBuilder

  /**
   * Set explicit start timestamp (default is now).
   */
  fun setStartTimestamp(timestamp: java.time.Instant): SpanBuilder

  /**
   * Start and return the span.
   *
   * The span should be ended by calling span.end() or span.close().
   *
   * @return The started span
   */
  fun startSpan(): Span
}

/**
 * Provider for obtaining Tracers.
 *
 * @since 0.4.0
 */
interface TracerProvider {

  /**
   * Get a tracer for the given instrumentation name.
   *
   * @param instrumentationName Name of the instrumentation library (e.g., "kioskops-sdk")
   * @param instrumentationVersion Optional version of the instrumentation library
   * @return A Tracer instance
   */
  fun getTracer(
    instrumentationName: String,
    instrumentationVersion: String? = null,
  ): Tracer
}

/**
 * No-op tracer provider for when tracing is disabled.
 *
 * @since 0.4.0
 */
object NoOpTracerProvider : TracerProvider {
  override fun getTracer(instrumentationName: String, instrumentationVersion: String?): Tracer =
    NoOpTracer
}

/**
 * No-op tracer implementation.
 *
 * @since 0.4.0
 */
object NoOpTracer : Tracer {
  override fun spanBuilder(spanName: String): SpanBuilder = NoOpSpanBuilder
}

/**
 * No-op span builder implementation.
 *
 * @since 0.4.0
 */
object NoOpSpanBuilder : SpanBuilder {
  override fun setParent(context: SpanContext?) = this
  override fun setSpanKind(kind: SpanKind) = this
  override fun setAttribute(key: String, value: String) = this
  override fun setAttribute(key: String, value: Long) = this
  override fun setAttribute(key: String, value: Double) = this
  override fun setAttribute(key: String, value: Boolean) = this
  override fun setAttributes(attributes: Map<String, Any>) = this
  override fun setStartTimestamp(timestamp: java.time.Instant) = this
  override fun startSpan(): Span = NoOpSpan
}
