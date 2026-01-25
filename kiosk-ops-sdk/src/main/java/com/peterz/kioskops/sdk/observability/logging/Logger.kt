/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability.logging

/**
 * Structured logging interface for KioskOps SDK.
 *
 * Provides level-based logging with optional structured fields for
 * enhanced searchability and correlation.
 *
 * Security (BSI SYS.3.2.2.A12): All log methods support structured fields
 * to enable consistent audit trail format.
 *
 * Privacy (GDPR Art. 5): Callers are responsible for not including PII
 * in log messages or fields. Use field filtering if needed.
 *
 * Usage:
 * ```kotlin
 * logger.i("SyncEngine", "Sync completed", mapOf(
 *   "events_sent" to "42",
 *   "duration_ms" to "150",
 * ))
 * ```
 *
 * @since 0.4.0
 */
interface Logger {

  /**
   * Log a verbose message.
   * Use for detailed debugging information.
   */
  fun v(tag: String, message: String, fields: Map<String, String> = emptyMap())

  /**
   * Log a debug message.
   * Use for development-time debugging.
   */
  fun d(tag: String, message: String, fields: Map<String, String> = emptyMap())

  /**
   * Log an info message.
   * Use for general operational information.
   */
  fun i(tag: String, message: String, fields: Map<String, String> = emptyMap())

  /**
   * Log a warning message.
   * Use for potentially problematic situations.
   */
  fun w(
    tag: String,
    message: String,
    throwable: Throwable? = null,
    fields: Map<String, String> = emptyMap(),
  )

  /**
   * Log an error message.
   * Use for error conditions that should be investigated.
   */
  fun e(
    tag: String,
    message: String,
    throwable: Throwable? = null,
    fields: Map<String, String> = emptyMap(),
  )

  /**
   * Log a message at the specified level.
   */
  fun log(
    level: com.peterz.kioskops.sdk.observability.LogLevel,
    tag: String,
    message: String,
    throwable: Throwable? = null,
    fields: Map<String, String> = emptyMap(),
  )

  /**
   * Check if logging is enabled at the given level.
   * Use to avoid expensive string formatting when logging is disabled.
   */
  fun isEnabled(level: com.peterz.kioskops.sdk.observability.LogLevel): Boolean
}

/**
 * No-op logger implementation for disabled logging.
 *
 * @since 0.4.0
 */
object NoOpLogger : Logger {
  override fun v(tag: String, message: String, fields: Map<String, String>) {}
  override fun d(tag: String, message: String, fields: Map<String, String>) {}
  override fun i(tag: String, message: String, fields: Map<String, String>) {}
  override fun w(tag: String, message: String, throwable: Throwable?, fields: Map<String, String>) {}
  override fun e(tag: String, message: String, throwable: Throwable?, fields: Map<String, String>) {}
  override fun log(
    level: com.peterz.kioskops.sdk.observability.LogLevel,
    tag: String,
    message: String,
    throwable: Throwable?,
    fields: Map<String, String>,
  ) {}
  override fun isEnabled(level: com.peterz.kioskops.sdk.observability.LogLevel): Boolean = false
}
