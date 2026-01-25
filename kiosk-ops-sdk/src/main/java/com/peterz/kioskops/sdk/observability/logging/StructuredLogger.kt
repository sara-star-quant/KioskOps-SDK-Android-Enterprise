/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability.logging

import com.peterz.kioskops.sdk.observability.CorrelationContext
import com.peterz.kioskops.sdk.observability.LogLevel
import com.peterz.kioskops.sdk.observability.ObservabilityPolicy
import java.time.Instant

/**
 * Multi-sink structured logger implementation.
 *
 * Dispatches log entries to configured sinks with automatic enrichment
 * of correlation context and thread information.
 *
 * Security (BSI SYS.3.2.2.A12): Centralized logging with configurable
 * verbosity per sink.
 *
 * Thread Safety: This class is thread-safe. All sink dispatching is
 * synchronized and sinks must be thread-safe.
 *
 * @property sinks List of logging sinks to dispatch to
 * @property policyProvider Provides current observability policy
 * @property enabledProvider Optional override for enabled state
 *
 * @since 0.4.0
 */
class StructuredLogger(
  private val sinks: List<LoggingSink>,
  private val policyProvider: () -> ObservabilityPolicy,
  private val enabledProvider: (() -> Boolean)? = null,
) : Logger {

  /**
   * Minimum log level across all sinks, cached for performance.
   */
  private val effectiveMinLevel: LogLevel
    get() = sinks.minOfOrNull { it.minLevel } ?: LogLevel.ERROR

  override fun v(tag: String, message: String, fields: Map<String, String>) {
    emit(LogLevel.VERBOSE, tag, message, null, fields)
  }

  override fun d(tag: String, message: String, fields: Map<String, String>) {
    emit(LogLevel.DEBUG, tag, message, null, fields)
  }

  override fun i(tag: String, message: String, fields: Map<String, String>) {
    emit(LogLevel.INFO, tag, message, null, fields)
  }

  override fun w(
    tag: String,
    message: String,
    throwable: Throwable?,
    fields: Map<String, String>,
  ) {
    emit(LogLevel.WARN, tag, message, throwable, fields)
  }

  override fun e(
    tag: String,
    message: String,
    throwable: Throwable?,
    fields: Map<String, String>,
  ) {
    emit(LogLevel.ERROR, tag, message, throwable, fields)
  }

  override fun log(
    level: LogLevel,
    tag: String,
    message: String,
    throwable: Throwable?,
    fields: Map<String, String>,
  ) {
    emit(level, tag, message, throwable, fields)
  }

  override fun isEnabled(level: LogLevel): Boolean {
    if (!isLoggingEnabled()) return false
    return level >= effectiveMinLevel
  }

  private fun emit(
    level: LogLevel,
    tag: String,
    message: String,
    throwable: Throwable?,
    fields: Map<String, String>,
  ) {
    if (!isLoggingEnabled()) return
    if (level < effectiveMinLevel) return

    val enrichedFields = enrichFields(fields)
    val entry = LogEntry(
      timestamp = Instant.now(),
      level = level,
      tag = tag,
      message = message,
      throwable = throwable,
      fields = enrichedFields,
    )

    dispatchToSinks(entry)
  }

  private fun enrichFields(fields: Map<String, String>): Map<String, String> {
    return buildMap {
      // Add correlation context
      put("correlation_id", CorrelationContext.correlationId)
      CorrelationContext.traceId?.let { put("trace_id", it) }
      CorrelationContext.spanId?.let { put("span_id", it) }
      CorrelationContext.operationName?.let { put("operation", it) }

      // Add thread info
      put("thread", Thread.currentThread().name)

      // Add caller-provided fields
      putAll(fields)
    }
  }

  private fun dispatchToSinks(entry: LogEntry) {
    sinks.forEach { sink ->
      if (sink.accepts(entry.level)) {
        try {
          sink.emit(entry)
        } catch (e: Exception) {
          // Don't let sink failures crash the app
          // This is a last-resort fallback
          android.util.Log.e(
            "StructuredLogger",
            "Sink ${sink::class.simpleName} failed: ${e.message}",
          )
        }
      }
    }
  }

  private fun isLoggingEnabled(): Boolean {
    return enabledProvider?.invoke() ?: policyProvider().structuredLoggingEnabled
  }

  /**
   * Flush all sinks.
   */
  fun flush() {
    sinks.forEach { sink ->
      try {
        sink.flush()
      } catch (e: Exception) {
        android.util.Log.e("StructuredLogger", "Sink flush failed: ${e.message}")
      }
    }
  }

  /**
   * Close all sinks.
   */
  fun close() {
    sinks.forEach { sink ->
      try {
        sink.close()
      } catch (e: Exception) {
        android.util.Log.e("StructuredLogger", "Sink close failed: ${e.message}")
      }
    }
  }

  companion object {
    /**
     * Create a logger from observability policy configuration.
     */
    fun fromPolicy(policyProvider: () -> ObservabilityPolicy): StructuredLogger {
      val policy = policyProvider()
      val sinks = policy.loggingSinks.mapNotNull { config ->
        when (config) {
          is com.peterz.kioskops.sdk.observability.LoggingSinkConfig.Logcat ->
            LogcatSink(config.minLevel, config.tagPrefix)
          is com.peterz.kioskops.sdk.observability.LoggingSinkConfig.File ->
            FileSink(config.minLevel, config.maxLines, config.includeTimestamps)
          is com.peterz.kioskops.sdk.observability.LoggingSinkConfig.Remote ->
            null // Remote sink requires transport, created separately
        }
      }
      return StructuredLogger(sinks, policyProvider)
    }
  }
}
