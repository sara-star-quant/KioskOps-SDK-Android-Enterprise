/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability

/**
 * Log severity levels for structured logging.
 *
 * Security (BSI SYS.3.2.2.A12): Levels allow filtering sensitive information
 * by verbosity in production environments.
 */
enum class LogLevel {
  VERBOSE,
  DEBUG,
  INFO,
  WARN,
  ERROR,
}

/**
 * Configuration for logging sinks.
 *
 * Privacy (GDPR Art. 5): Remote sinks require explicit opt-in.
 * No PII is logged by default regardless of sink configuration.
 *
 * @since 0.4.0
 */
sealed class LoggingSinkConfig {

  /**
   * Android Logcat output sink.
   *
   * @property minLevel Minimum log level to output
   * @property tagPrefix Optional prefix for log tags
   */
  data class Logcat(
    val minLevel: LogLevel = LogLevel.DEBUG,
    val tagPrefix: String = "KioskOps",
  ) : LoggingSinkConfig()

  /**
   * File-based logging sink (extends existing RingLog behavior).
   *
   * Security (BSI APP.4.4.A3): File logs are stored in app-private storage.
   *
   * @property minLevel Minimum log level to write
   * @property maxLines Maximum lines before rotation (ring buffer)
   * @property includeTimestamps Include ISO 8601 timestamps
   */
  data class File(
    val minLevel: LogLevel = LogLevel.INFO,
    val maxLines: Int = 2000,
    val includeTimestamps: Boolean = true,
  ) : LoggingSinkConfig()

  /**
   * Remote logging endpoint sink.
   *
   * Privacy (GDPR Art. 6): Requires explicit opt-in as logs are transmitted
   * off-device. Ensure appropriate legal basis before enabling.
   *
   * Security (BSI APP.4.4.A7): Rate-limited to prevent log flooding attacks.
   *
   * @property endpoint HTTPS endpoint for log submission
   * @property minLevel Minimum log level to transmit
   * @property batchSize Maximum entries per batch
   * @property flushIntervalMs Flush interval in milliseconds
   * @property headers Additional headers for authentication
   */
  data class Remote(
    val endpoint: String,
    val minLevel: LogLevel = LogLevel.WARN,
    val batchSize: Int = 100,
    val flushIntervalMs: Long = 30_000L,
    val headers: Map<String, String> = emptyMap(),
  ) : LoggingSinkConfig() {
    init {
      require(endpoint.startsWith("https://")) { "Remote logging requires HTTPS endpoint" }
      require(batchSize in 1..1000) { "batchSize must be 1-1000" }
      require(flushIntervalMs >= 5_000L) { "flushIntervalMs must be >= 5s" }
    }
  }
}
