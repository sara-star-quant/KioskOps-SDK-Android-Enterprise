/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability

/**
 * Policy for SDK observability features including logging, tracing, and metrics.
 *
 * Security (ISO 27001 A.12.4): All observability features are opt-in with secure
 * defaults to prevent accidental data leakage. Remote transmission requires
 * explicit endpoint configuration.
 *
 * Privacy (GDPR Art. 5): No PII is collected by default. Remote logging and
 * tracing require explicit opt-in with appropriate legal basis.
 *
 * @property tracingEnabled Enable OpenTelemetry-compatible distributed tracing
 * @property otlpEndpoint OTLP/HTTP endpoint for trace export (null = disabled)
 * @property structuredLoggingEnabled Enable multi-sink structured logging
 * @property loggingSinks Configured logging sinks (default: Logcat only)
 * @property correlationEnabled Enable correlation ID propagation across operations
 * @property metricsEnabled Enable metrics collection for monitoring
 * @property metricsExportIntervalMs Metrics export interval in milliseconds
 * @property debugFeaturesEnabled Enable debug features (debug builds only)
 * @property networkLoggingEnabled Enable network request/response logging (debug only)
 * @property traceSampleRate Sampling rate for traces (0.0 to 1.0)
 *
 * @since 0.4.0
 */
data class ObservabilityPolicy(
  val tracingEnabled: Boolean = false,
  val otlpEndpoint: String? = null,
  val structuredLoggingEnabled: Boolean = false,
  val loggingSinks: List<LoggingSinkConfig> = listOf(LoggingSinkConfig.Logcat()),
  val correlationEnabled: Boolean = true,
  val metricsEnabled: Boolean = false,
  val metricsExportIntervalMs: Long = 60_000L,
  val debugFeaturesEnabled: Boolean = false,
  val networkLoggingEnabled: Boolean = false,
  val traceSampleRate: Double = 0.1,
) {
  init {
    require(traceSampleRate in 0.0..1.0) { "traceSampleRate must be 0.0-1.0" }
    require(metricsExportIntervalMs >= 10_000L) { "metricsExportIntervalMs must be >= 10s" }
    if (tracingEnabled && otlpEndpoint != null) {
      require(otlpEndpoint.startsWith("https://")) { "OTLP endpoint requires HTTPS" }
    }
  }

  companion object {
    /**
     * Disabled defaults - all observability features off.
     * Suitable for minimal resource usage and maximum privacy.
     */
    fun disabledDefaults() = ObservabilityPolicy()

    /**
     * Development defaults - verbose logging and debug features enabled.
     * Use only in debug builds for development and testing.
     *
     * Security: Network logging exposes request/response details.
     * Never use in production.
     */
    fun developmentDefaults() = ObservabilityPolicy(
      structuredLoggingEnabled = true,
      loggingSinks = listOf(
        LoggingSinkConfig.Logcat(minLevel = LogLevel.DEBUG),
      ),
      correlationEnabled = true,
      debugFeaturesEnabled = true,
      networkLoggingEnabled = true,
    )

    /**
     * Production defaults - balanced observability for production monitoring.
     * Enables tracing and metrics with conservative sampling.
     *
     * Privacy: Requires OTLP endpoint to be configured separately.
     */
    fun productionDefaults() = ObservabilityPolicy(
      tracingEnabled = false, // Requires otlpEndpoint to be set
      structuredLoggingEnabled = true,
      loggingSinks = listOf(
        LoggingSinkConfig.Logcat(minLevel = LogLevel.INFO),
        LoggingSinkConfig.File(minLevel = LogLevel.WARN, maxLines = 5000),
      ),
      correlationEnabled = true,
      metricsEnabled = true,
      metricsExportIntervalMs = 60_000L,
      traceSampleRate = 0.01,
      debugFeaturesEnabled = false,
      networkLoggingEnabled = false,
    )

    /**
     * Full observability with all features enabled.
     * Requires otlpEndpoint configuration for trace export.
     */
    fun fullObservability(otlpEndpoint: String) = ObservabilityPolicy(
      tracingEnabled = true,
      otlpEndpoint = otlpEndpoint,
      structuredLoggingEnabled = true,
      loggingSinks = listOf(
        LoggingSinkConfig.Logcat(minLevel = LogLevel.INFO),
        LoggingSinkConfig.File(minLevel = LogLevel.INFO, maxLines = 10000),
      ),
      correlationEnabled = true,
      metricsEnabled = true,
      metricsExportIntervalMs = 30_000L,
      traceSampleRate = 0.1,
      debugFeaturesEnabled = false,
      networkLoggingEnabled = false,
    )
  }
}
