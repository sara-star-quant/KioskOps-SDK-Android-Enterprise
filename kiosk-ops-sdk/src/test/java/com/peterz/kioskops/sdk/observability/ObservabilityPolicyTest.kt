/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for ObservabilityPolicy.
 *
 * Security (ISO 27001 A.12.4): Validates observability configuration
 * with secure defaults and proper validation.
 */
@RunWith(RobolectricTestRunner::class)
class ObservabilityPolicyTest {

  @Test
  fun `disabledDefaults has all features disabled`() {
    val policy = ObservabilityPolicy.disabledDefaults()

    assertThat(policy.tracingEnabled).isFalse()
    assertThat(policy.otlpEndpoint).isNull()
    assertThat(policy.structuredLoggingEnabled).isFalse()
    assertThat(policy.correlationEnabled).isTrue() // Correlation is lightweight, default on
    assertThat(policy.metricsEnabled).isFalse()
    assertThat(policy.debugFeaturesEnabled).isFalse()
    assertThat(policy.networkLoggingEnabled).isFalse()
  }

  @Test
  fun `developmentDefaults enables debug features`() {
    val policy = ObservabilityPolicy.developmentDefaults()

    assertThat(policy.structuredLoggingEnabled).isTrue()
    assertThat(policy.correlationEnabled).isTrue()
    assertThat(policy.debugFeaturesEnabled).isTrue()
    assertThat(policy.networkLoggingEnabled).isTrue()
    assertThat(policy.tracingEnabled).isFalse()
    assertThat(policy.metricsEnabled).isFalse()
  }

  @Test
  fun `productionDefaults has conservative settings`() {
    val policy = ObservabilityPolicy.productionDefaults()

    assertThat(policy.structuredLoggingEnabled).isTrue()
    assertThat(policy.correlationEnabled).isTrue()
    assertThat(policy.metricsEnabled).isTrue()
    assertThat(policy.debugFeaturesEnabled).isFalse()
    assertThat(policy.networkLoggingEnabled).isFalse()
    assertThat(policy.traceSampleRate).isEqualTo(0.01)
    assertThat(policy.loggingSinks).hasSize(2)
  }

  @Test
  fun `fullObservability requires otlpEndpoint`() {
    val policy = ObservabilityPolicy.fullObservability("https://otel.example.com/v1/traces")

    assertThat(policy.tracingEnabled).isTrue()
    assertThat(policy.otlpEndpoint).isEqualTo("https://otel.example.com/v1/traces")
    assertThat(policy.metricsEnabled).isTrue()
    assertThat(policy.traceSampleRate).isEqualTo(0.1)
  }

  @Test
  fun `traceSampleRate must be between 0 and 1`() {
    val validLow = ObservabilityPolicy(traceSampleRate = 0.0)
    assertThat(validLow.traceSampleRate).isEqualTo(0.0)

    val validHigh = ObservabilityPolicy(traceSampleRate = 1.0)
    assertThat(validHigh.traceSampleRate).isEqualTo(1.0)

    val exception = runCatching {
      ObservabilityPolicy(traceSampleRate = 1.5)
    }.exceptionOrNull()
    assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
    assertThat(exception?.message).contains("traceSampleRate")
  }

  @Test
  fun `metricsExportIntervalMs must be at least 10 seconds`() {
    val valid = ObservabilityPolicy(metricsExportIntervalMs = 10_000L)
    assertThat(valid.metricsExportIntervalMs).isEqualTo(10_000L)

    val exception = runCatching {
      ObservabilityPolicy(metricsExportIntervalMs = 5_000L)
    }.exceptionOrNull()
    assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
    assertThat(exception?.message).contains("metricsExportIntervalMs")
  }

  @Test
  fun `otlpEndpoint requires HTTPS when tracing enabled`() {
    val exception = runCatching {
      ObservabilityPolicy(
        tracingEnabled = true,
        otlpEndpoint = "http://insecure.example.com/traces",
      )
    }.exceptionOrNull()
    assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
    assertThat(exception?.message).contains("HTTPS")
  }

  @Test
  fun `otlpEndpoint allows HTTPS`() {
    val policy = ObservabilityPolicy(
      tracingEnabled = true,
      otlpEndpoint = "https://secure.example.com/traces",
    )
    assertThat(policy.otlpEndpoint).isEqualTo("https://secure.example.com/traces")
  }

  @Test
  fun `otlpEndpoint null is valid even with tracing enabled`() {
    val policy = ObservabilityPolicy(tracingEnabled = true, otlpEndpoint = null)
    assertThat(policy.tracingEnabled).isTrue()
    assertThat(policy.otlpEndpoint).isNull()
  }

  @Test
  fun `default loggingSinks contains Logcat`() {
    val policy = ObservabilityPolicy()
    assertThat(policy.loggingSinks).hasSize(1)
    assertThat(policy.loggingSinks.first()).isInstanceOf(LoggingSinkConfig.Logcat::class.java)
  }

  @Test
  fun `copy preserves unchanged fields`() {
    val original = ObservabilityPolicy.productionDefaults()
    val modified = original.copy(metricsEnabled = false)

    assertThat(modified.structuredLoggingEnabled).isTrue()
    assertThat(modified.metricsEnabled).isFalse()
    assertThat(modified.traceSampleRate).isEqualTo(0.01)
  }
}
