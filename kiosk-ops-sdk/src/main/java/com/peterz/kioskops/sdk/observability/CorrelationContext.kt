/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability

import java.util.UUID

/**
 * Thread-local correlation context for distributed tracing and audit trail linkage.
 *
 * Provides automatic correlation ID generation and propagation across SDK operations,
 * enabling end-to-end request tracing through logs, spans, and audit events.
 *
 * Compliance (ISO 27001 A.12.4): Enables comprehensive audit trail by linking
 * related operations across service boundaries.
 *
 * Thread Safety: Uses ThreadLocal storage. Context must be explicitly copied
 * when crossing thread boundaries (e.g., coroutine dispatchers).
 *
 * @since 0.4.0
 */
object CorrelationContext {

  private val context = ThreadLocal<MutableMap<String, String>>()

  /**
   * Current correlation ID. Auto-generated if not explicitly set.
   * Format: 16 character lowercase hex string.
   */
  val correlationId: String
    get() = get(KEY_CORRELATION_ID) ?: generateId().also { set(KEY_CORRELATION_ID, it) }

  /**
   * Current OpenTelemetry trace ID (32 hex chars), or null if not in a trace context.
   */
  val traceId: String?
    get() = get(KEY_TRACE_ID)

  /**
   * Current OpenTelemetry span ID (16 hex chars), or null if not in a span.
   */
  val spanId: String?
    get() = get(KEY_SPAN_ID)

  /**
   * Parent span ID for nested spans, or null if root span.
   */
  val parentSpanId: String?
    get() = get(KEY_PARENT_SPAN_ID)

  /**
   * Operation name for the current context.
   */
  val operationName: String?
    get() = get(KEY_OPERATION_NAME)

  /**
   * Get a context value by key.
   */
  fun get(key: String): String? = context.get()?.get(key)

  /**
   * Set a context value.
   */
  fun set(key: String, value: String) {
    val map = context.get() ?: mutableMapOf<String, String>().also { context.set(it) }
    map[key] = value
  }

  /**
   * Remove a context value.
   */
  fun remove(key: String) {
    context.get()?.remove(key)
  }

  /**
   * Clear all context values for the current thread.
   */
  fun clear() {
    context.remove()
  }

  /**
   * Get a snapshot of the current context for cross-thread propagation.
   */
  fun snapshot(): Map<String, String> = context.get()?.toMap() ?: emptyMap()

  /**
   * Restore context from a snapshot (e.g., when crossing thread boundaries).
   */
  fun restore(snapshot: Map<String, String>) {
    if (snapshot.isEmpty()) {
      context.remove()
    } else {
      context.set(snapshot.toMutableMap())
    }
  }

  /**
   * Execute a block with a new or specified correlation context.
   * Previous context is restored after the block completes.
   *
   * @param correlationId Optional correlation ID (auto-generated if null)
   * @param operationName Optional operation name for tracing
   * @param block The block to execute
   * @return The result of the block
   */
  inline fun <T> withContext(
    correlationId: String? = null,
    operationName: String? = null,
    block: () -> T,
  ): T {
    val prevSnapshot = snapshot()
    try {
      correlationId?.let { set(KEY_CORRELATION_ID, it) }
      operationName?.let { set(KEY_OPERATION_NAME, it) }
      return block()
    } finally {
      restore(prevSnapshot)
    }
  }

  /**
   * Execute a block with additional context attributes.
   * Attributes are merged with existing context and removed after block completes.
   */
  inline fun <T> withAttributes(
    attributes: Map<String, String>,
    block: () -> T,
  ): T {
    val prevSnapshot = snapshot()
    try {
      attributes.forEach { (k, v) -> set(k, v) }
      return block()
    } finally {
      restore(prevSnapshot)
    }
  }

  /**
   * Generate a new correlation ID (16 lowercase hex characters).
   */
  fun generateId(): String =
    UUID.randomUUID().toString().replace("-", "").take(16).lowercase()

  /**
   * Generate a new trace ID (32 lowercase hex characters) following W3C Trace Context.
   */
  fun generateTraceId(): String =
    UUID.randomUUID().toString().replace("-", "").lowercase()

  /**
   * Generate a new span ID (16 lowercase hex characters) following W3C Trace Context.
   */
  fun generateSpanId(): String =
    UUID.randomUUID().toString().replace("-", "").take(16).lowercase()

  // Standard context keys
  const val KEY_CORRELATION_ID = "correlation_id"
  const val KEY_TRACE_ID = "trace_id"
  const val KEY_SPAN_ID = "span_id"
  const val KEY_PARENT_SPAN_ID = "parent_span_id"
  const val KEY_OPERATION_NAME = "operation_name"
  const val KEY_USER_ID = "user_id"
  const val KEY_DEVICE_ID = "device_id"
  const val KEY_SESSION_ID = "session_id"

  // HTTP header names for context propagation
  const val HEADER_CORRELATION_ID = "X-Correlation-ID"
  const val HEADER_TRACE_PARENT = "traceparent"
  const val HEADER_TRACE_STATE = "tracestate"
}
