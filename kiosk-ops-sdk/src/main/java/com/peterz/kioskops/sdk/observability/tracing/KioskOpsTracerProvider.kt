/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability.tracing

import com.peterz.kioskops.sdk.observability.ObservabilityPolicy
import java.util.concurrent.ConcurrentHashMap

/**
 * KioskOps SDK TracerProvider implementation.
 *
 * Creates and caches Tracer instances for instrumentation libraries.
 *
 * Security (BSI APP.4.4.A3): Tracers respect the observability policy
 * for sampling and export configuration.
 *
 * @property policyProvider Provides current observability policy
 * @property exporter Optional span exporter
 *
 * @since 0.4.0
 */
class KioskOpsTracerProvider(
  private val policyProvider: () -> ObservabilityPolicy,
  private val exporter: SpanExporter? = null,
) : TracerProvider {

  private val tracers = ConcurrentHashMap<TracerKey, Tracer>()

  override fun getTracer(
    instrumentationName: String,
    instrumentationVersion: String?,
  ): Tracer {
    val key = TracerKey(instrumentationName, instrumentationVersion)
    return tracers.getOrPut(key) {
      KioskOpsTracer(
        instrumentationName = instrumentationName,
        instrumentationVersion = instrumentationVersion,
        policyProvider = policyProvider,
        exporter = exporter,
      )
    }
  }

  /**
   * Shutdown the tracer provider and flush pending spans.
   */
  fun shutdown() {
    exporter?.shutdown()
    tracers.clear()
  }

  /**
   * Flush any pending spans.
   */
  fun flush() {
    exporter?.flush()
  }

  private data class TracerKey(
    val name: String,
    val version: String?,
  )

  companion object {
    /**
     * Default instrumentation name for KioskOps SDK.
     */
    const val SDK_INSTRUMENTATION_NAME = "kioskops-sdk"

    /**
     * Create a provider from observability policy.
     */
    fun fromPolicy(policyProvider: () -> ObservabilityPolicy): TracerProvider {
      val policy = policyProvider()
      return if (policy.tracingEnabled) {
        KioskOpsTracerProvider(policyProvider)
      } else {
        NoOpTracerProvider
      }
    }
  }
}
