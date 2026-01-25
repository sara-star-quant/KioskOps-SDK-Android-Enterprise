/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability.logging

import com.peterz.kioskops.sdk.observability.LogLevel
import java.time.Instant

/**
 * Structured log entry with metadata for multi-sink output.
 *
 * Privacy (GDPR Art. 5): Log entries should not contain PII.
 * Use field filtering before logging sensitive data.
 *
 * @property timestamp When the log entry was created (UTC)
 * @property level Severity level of the log entry
 * @property tag Component or class identifier (e.g., "SyncEngine", "QueueRepository")
 * @property message Human-readable log message
 * @property throwable Optional exception associated with this entry
 * @property fields Structured key-value fields for searchable attributes
 *
 * @since 0.4.0
 */
data class LogEntry(
  val timestamp: Instant,
  val level: LogLevel,
  val tag: String,
  val message: String,
  val throwable: Throwable? = null,
  val fields: Map<String, String> = emptyMap(),
) {

  /**
   * Format entry as a single-line string for simple output.
   *
   * @param includeTimestamp Include ISO 8601 timestamp prefix
   * @param includeFields Include structured fields suffix
   * @return Formatted log line
   */
  fun formatLine(
    includeTimestamp: Boolean = true,
    includeFields: Boolean = true,
  ): String = buildString {
    if (includeTimestamp) {
      append(timestamp.toString())
      append(" ")
    }
    append("[${level.name}] ")
    append(tag)
    append(": ")
    append(message)
    if (includeFields && fields.isNotEmpty()) {
      append(" ")
      append(fields.entries.joinToString(" ") { "${it.key}=${it.value}" })
    }
  }

  /**
   * Format entry as JSON for structured logging systems.
   *
   * @return JSON string representation
   */
  fun formatJson(): String = buildString {
    append("{")
    append("\"timestamp\":\"${timestamp}\"")
    append(",\"level\":\"${level.name}\"")
    append(",\"tag\":\"${escapeJson(tag)}\"")
    append(",\"message\":\"${escapeJson(message)}\"")
    if (throwable != null) {
      append(",\"error\":\"${escapeJson(throwable.toString())}\"")
      append(",\"stackTrace\":\"${escapeJson(throwable.stackTraceToString().take(2000))}\"")
    }
    if (fields.isNotEmpty()) {
      append(",\"fields\":{")
      append(fields.entries.joinToString(",") { "\"${escapeJson(it.key)}\":\"${escapeJson(it.value)}\"" })
      append("}")
    }
    append("}")
  }

  private fun escapeJson(s: String): String =
    s.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

  companion object {
    /**
     * Create a log entry with current timestamp.
     */
    fun now(
      level: LogLevel,
      tag: String,
      message: String,
      throwable: Throwable? = null,
      fields: Map<String, String> = emptyMap(),
    ) = LogEntry(
      timestamp = Instant.now(),
      level = level,
      tag = tag,
      message = message,
      throwable = throwable,
      fields = fields,
    )
  }
}
