/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability.logging

import com.peterz.kioskops.sdk.observability.LogLevel

/**
 * Interface for logging output destinations.
 *
 * Sinks receive log entries from the StructuredLogger and output them
 * to their respective destinations (Logcat, file, remote endpoint, etc.).
 *
 * Security (BSI SYS.3.2.2.A12): Implementations must respect minLevel
 * filtering to prevent verbose output in production.
 *
 * @since 0.4.0
 */
interface LoggingSink {

  /**
   * Minimum log level this sink will accept.
   * Entries below this level are silently discarded.
   */
  val minLevel: LogLevel

  /**
   * Emit a log entry to this sink.
   *
   * Called by StructuredLogger after level filtering.
   * Implementations should be thread-safe.
   *
   * @param entry The log entry to output
   */
  fun emit(entry: LogEntry)

  /**
   * Flush any buffered entries.
   *
   * Called during shutdown or when immediate output is required.
   * Default implementation is no-op for non-buffered sinks.
   */
  fun flush() {}

  /**
   * Close this sink and release resources.
   *
   * Called during SDK shutdown.
   * Default implementation calls flush().
   */
  fun close() {
    flush()
  }

  /**
   * Check if this sink accepts entries at the given level.
   */
  fun accepts(level: LogLevel): Boolean = level >= minLevel
}
