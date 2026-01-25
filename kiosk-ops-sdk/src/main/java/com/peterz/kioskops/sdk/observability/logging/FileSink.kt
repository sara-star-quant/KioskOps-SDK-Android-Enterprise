/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability.logging

import com.peterz.kioskops.sdk.observability.LogLevel
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory ring buffer logging sink with file persistence support.
 *
 * Maintains a fixed-size circular buffer of log entries. Oldest entries
 * are discarded when the buffer is full. Entries can be exported as
 * formatted text.
 *
 * Security (BSI APP.4.4.A3): Logs are stored in memory only and can be
 * explicitly exported. No automatic disk persistence.
 *
 * Thread Safety: Uses ConcurrentLinkedDeque for thread-safe operations.
 *
 * @property minLevel Minimum log level to accept
 * @property maxLines Maximum number of lines to retain
 * @property includeTimestamps Include ISO 8601 timestamps in output
 *
 * @since 0.4.0
 */
class FileSink(
  override val minLevel: LogLevel = LogLevel.INFO,
  private val maxLines: Int = 2000,
  private val includeTimestamps: Boolean = true,
) : LoggingSink {

  private val buffer = ConcurrentLinkedDeque<LogEntry>()
  private val size = AtomicInteger(0)

  override fun emit(entry: LogEntry) {
    buffer.addLast(entry)
    val currentSize = size.incrementAndGet()

    // Trim if over capacity
    while (currentSize > maxLines && buffer.isNotEmpty()) {
      if (buffer.pollFirst() != null) {
        size.decrementAndGet()
      }
    }
  }

  /**
   * Get all buffered log entries.
   *
   * @return Immutable list of entries in chronological order
   */
  fun getEntries(): List<LogEntry> = buffer.toList()

  /**
   * Get formatted log text for export.
   *
   * @param separator Line separator (default newline)
   * @return Formatted log text
   */
  fun formatForExport(separator: String = "\n"): String {
    return buffer.joinToString(separator) { entry ->
      entry.formatLine(
        includeTimestamp = includeTimestamps,
        includeFields = true,
      )
    }
  }

  /**
   * Get formatted log as JSON array for structured export.
   *
   * @return JSON array string
   */
  fun formatAsJsonArray(): String {
    return buildString {
      append("[")
      buffer.forEachIndexed { index, entry ->
        if (index > 0) append(",")
        append(entry.formatJson())
      }
      append("]")
    }
  }

  /**
   * Get entries matching a filter.
   *
   * @param minLevel Minimum level to include
   * @param tag Optional tag filter (exact match)
   * @param correlationId Optional correlation ID filter
   * @return Filtered entries
   */
  fun filter(
    minLevel: LogLevel? = null,
    tag: String? = null,
    correlationId: String? = null,
  ): List<LogEntry> {
    return buffer.filter { entry ->
      val levelMatch = minLevel?.let { entry.level >= it } ?: true
      val tagMatch = tag?.let { entry.tag == it } ?: true
      val correlationMatch = correlationId?.let {
        entry.fields["correlation_id"] == it
      } ?: true
      levelMatch && tagMatch && correlationMatch
    }
  }

  /**
   * Get current buffer size.
   */
  fun size(): Int = size.get()

  /**
   * Clear all buffered entries.
   */
  fun clear() {
    buffer.clear()
    size.set(0)
  }

  /**
   * Get entries since a given timestamp.
   *
   * @param sinceMs Unix timestamp in milliseconds
   * @return Entries with timestamp >= sinceMs
   */
  fun entriesSince(sinceMs: Long): List<LogEntry> {
    return buffer.filter { entry ->
      entry.timestamp.toEpochMilli() >= sinceMs
    }
  }

  /**
   * Get statistics about the buffer.
   */
  fun getStatistics(): BufferStatistics {
    val entries = buffer.toList()
    val levelCounts = entries.groupingBy { it.level }.eachCount()
    return BufferStatistics(
      totalEntries = entries.size,
      maxCapacity = maxLines,
      levelBreakdown = levelCounts,
      oldestTimestamp = entries.firstOrNull()?.timestamp?.toEpochMilli(),
      newestTimestamp = entries.lastOrNull()?.timestamp?.toEpochMilli(),
    )
  }

  /**
   * Statistics about the log buffer.
   */
  data class BufferStatistics(
    val totalEntries: Int,
    val maxCapacity: Int,
    val levelBreakdown: Map<LogLevel, Int>,
    val oldestTimestamp: Long?,
    val newestTimestamp: Long?,
  ) {
    val utilizationPercent: Int
      get() = if (maxCapacity > 0) (totalEntries * 100) / maxCapacity else 0
  }
}
