/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability.tracing

/**
 * Context for a span in a distributed trace.
 *
 * Follows W3C Trace Context specification for trace/span ID formats.
 *
 * @property traceId 32-character lowercase hex trace identifier
 * @property spanId 16-character lowercase hex span identifier
 * @property parentSpanId Parent span ID if this is a child span, null for root
 * @property traceFlags Trace flags (e.g., 0x01 for sampled)
 * @property traceState Optional vendor-specific trace state
 *
 * @since 0.4.0
 */
data class SpanContext(
  val traceId: String,
  val spanId: String,
  val parentSpanId: String? = null,
  val traceFlags: Byte = SAMPLED,
  val traceState: String? = null,
) {
  /**
   * Whether this span is sampled (should be recorded and exported).
   */
  val isSampled: Boolean
    get() = (traceFlags.toInt() and SAMPLED.toInt()) != 0

  /**
   * Format as W3C traceparent header value.
   *
   * @return traceparent header value (e.g., "00-{traceId}-{spanId}-01")
   */
  fun toTraceparent(): String {
    val flags = String.format("%02x", traceFlags)
    return "$VERSION-$traceId-$spanId-$flags"
  }

  companion object {
    const val SAMPLED: Byte = 0x01
    const val NOT_SAMPLED: Byte = 0x00
    private const val VERSION = "00"

    /**
     * Parse a traceparent header value.
     *
     * @param traceparent W3C traceparent header value
     * @return SpanContext or null if invalid format
     */
    fun fromTraceparent(traceparent: String): SpanContext? {
      val parts = traceparent.split("-")
      if (parts.size != 4) return null

      val version = parts[0]
      val traceId = parts[1]
      val spanId = parts[2]
      val flags = parts[3]

      // Validate format
      if (version != VERSION) return null
      if (traceId.length != 32 || !traceId.all { it in '0'..'9' || it in 'a'..'f' }) return null
      if (spanId.length != 16 || !spanId.all { it in '0'..'9' || it in 'a'..'f' }) return null
      if (flags.length != 2) return null

      val traceFlags = try {
        flags.toInt(16).toByte()
      } catch (e: NumberFormatException) {
        return null
      }

      return SpanContext(
        traceId = traceId,
        spanId = spanId,
        traceFlags = traceFlags,
      )
    }

    /**
     * Create an invalid/empty context.
     */
    fun invalid() = SpanContext(
      traceId = "00000000000000000000000000000000",
      spanId = "0000000000000000",
      traceFlags = NOT_SAMPLED,
    )
  }
}

/**
 * Status of a span.
 *
 * @since 0.4.0
 */
enum class SpanStatus {
  /** Status not set (default). */
  UNSET,
  /** Operation completed successfully. */
  OK,
  /** Operation failed with an error. */
  ERROR,
}

/**
 * Kind of span (client, server, internal, etc.).
 *
 * @since 0.4.0
 */
enum class SpanKind {
  /** Internal operation (default). */
  INTERNAL,
  /** Outgoing request to a remote service. */
  CLIENT,
  /** Incoming request from a remote service. */
  SERVER,
  /** Message producer. */
  PRODUCER,
  /** Message consumer. */
  CONSUMER,
}
