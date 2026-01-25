/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability.tracing

import java.time.Instant

/**
 * Represents a unit of work in a distributed trace.
 *
 * Follows OpenTelemetry Semantic Conventions 1.25.0.
 *
 * Thread Safety: Span implementations must be thread-safe.
 * Spans may be modified from multiple threads.
 *
 * @since 0.4.0
 */
interface Span : AutoCloseable {

  /**
   * The span context containing trace/span IDs.
   */
  val context: SpanContext

  /**
   * Set a string attribute on this span.
   *
   * @param key Attribute key (should follow semantic conventions)
   * @param value Attribute value
   * @return This span for chaining
   */
  fun setAttribute(key: String, value: String): Span

  /**
   * Set a long attribute on this span.
   */
  fun setAttribute(key: String, value: Long): Span

  /**
   * Set a double attribute on this span.
   */
  fun setAttribute(key: String, value: Double): Span

  /**
   * Set a boolean attribute on this span.
   */
  fun setAttribute(key: String, value: Boolean): Span

  /**
   * Add an event (annotation) to this span.
   *
   * @param name Event name
   * @param attributes Optional event attributes
   * @return This span for chaining
   */
  fun addEvent(name: String, attributes: Map<String, String> = emptyMap()): Span

  /**
   * Add an event with timestamp.
   */
  fun addEvent(name: String, timestamp: Instant, attributes: Map<String, String> = emptyMap()): Span

  /**
   * Set the status of this span.
   *
   * @param status The span status
   * @param description Optional description (usually for ERROR status)
   * @return This span for chaining
   */
  fun setStatus(status: SpanStatus, description: String? = null): Span

  /**
   * Record an exception on this span.
   *
   * Sets status to ERROR and adds an exception event.
   *
   * @param exception The exception to record
   * @return This span for chaining
   */
  fun recordException(exception: Throwable): Span

  /**
   * Update the span name.
   */
  fun updateName(name: String): Span

  /**
   * End this span with the current timestamp.
   */
  fun end()

  /**
   * End this span with a specific timestamp.
   */
  fun end(timestamp: Instant)

  /**
   * Check if this span is recording (not a no-op).
   */
  fun isRecording(): Boolean

  /**
   * Close is an alias for end() (for use with AutoCloseable).
   */
  override fun close() = end()
}

/**
 * Event recorded on a span.
 *
 * @since 0.4.0
 */
data class SpanEvent(
  val name: String,
  val timestamp: Instant,
  val attributes: Map<String, String>,
)

/**
 * No-op span implementation for when tracing is disabled or not sampled.
 *
 * @since 0.4.0
 */
object NoOpSpan : Span {
  override val context: SpanContext = SpanContext.invalid()

  override fun setAttribute(key: String, value: String) = this
  override fun setAttribute(key: String, value: Long) = this
  override fun setAttribute(key: String, value: Double) = this
  override fun setAttribute(key: String, value: Boolean) = this
  override fun addEvent(name: String, attributes: Map<String, String>) = this
  override fun addEvent(name: String, timestamp: Instant, attributes: Map<String, String>) = this
  override fun setStatus(status: SpanStatus, description: String?) = this
  override fun recordException(exception: Throwable) = this
  override fun updateName(name: String) = this
  override fun end() {}
  override fun end(timestamp: Instant) {}
  override fun isRecording() = false
}
