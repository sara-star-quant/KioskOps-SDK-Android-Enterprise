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
 * Tests for LoggingSinkConfig.
 *
 * Privacy (GDPR Art. 5): Validates logging sink configurations
 * with appropriate security constraints.
 */
@RunWith(RobolectricTestRunner::class)
class LoggingSinkConfigTest {

  @Test
  fun `Logcat sink has default minLevel DEBUG`() {
    val sink = LoggingSinkConfig.Logcat()
    assertThat(sink.minLevel).isEqualTo(LogLevel.DEBUG)
    assertThat(sink.tagPrefix).isEqualTo("KioskOps")
  }

  @Test
  fun `Logcat sink accepts custom minLevel`() {
    val sink = LoggingSinkConfig.Logcat(minLevel = LogLevel.WARN)
    assertThat(sink.minLevel).isEqualTo(LogLevel.WARN)
  }

  @Test
  fun `Logcat sink accepts custom tag prefix`() {
    val sink = LoggingSinkConfig.Logcat(tagPrefix = "MyApp")
    assertThat(sink.tagPrefix).isEqualTo("MyApp")
  }

  @Test
  fun `File sink has default minLevel INFO`() {
    val sink = LoggingSinkConfig.File()
    assertThat(sink.minLevel).isEqualTo(LogLevel.INFO)
    assertThat(sink.maxLines).isEqualTo(2000)
    assertThat(sink.includeTimestamps).isTrue()
  }

  @Test
  fun `File sink accepts custom maxLines`() {
    val sink = LoggingSinkConfig.File(maxLines = 5000)
    assertThat(sink.maxLines).isEqualTo(5000)
  }

  @Test
  fun `File sink accepts custom includeTimestamps`() {
    val sink = LoggingSinkConfig.File(includeTimestamps = false)
    assertThat(sink.includeTimestamps).isFalse()
  }

  @Test
  fun `Remote sink has default minLevel WARN`() {
    val sink = LoggingSinkConfig.Remote(endpoint = "https://logs.example.com")
    assertThat(sink.minLevel).isEqualTo(LogLevel.WARN)
    assertThat(sink.batchSize).isEqualTo(100)
    assertThat(sink.flushIntervalMs).isEqualTo(30_000L)
    assertThat(sink.headers).isEmpty()
  }

  @Test
  fun `Remote sink requires HTTPS endpoint`() {
    val exception = runCatching {
      LoggingSinkConfig.Remote(endpoint = "http://insecure.example.com")
    }.exceptionOrNull()
    assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
    assertThat(exception?.message).contains("HTTPS")
  }

  @Test
  fun `Remote sink accepts HTTPS endpoint`() {
    val sink = LoggingSinkConfig.Remote(endpoint = "https://secure.example.com/logs")
    assertThat(sink.endpoint).isEqualTo("https://secure.example.com/logs")
  }

  @Test
  fun `Remote sink batchSize must be 1-1000`() {
    val validLow = LoggingSinkConfig.Remote(endpoint = "https://logs.example.com", batchSize = 1)
    assertThat(validLow.batchSize).isEqualTo(1)

    val validHigh = LoggingSinkConfig.Remote(endpoint = "https://logs.example.com", batchSize = 1000)
    assertThat(validHigh.batchSize).isEqualTo(1000)

    val exceptionTooLow = runCatching {
      LoggingSinkConfig.Remote(endpoint = "https://logs.example.com", batchSize = 0)
    }.exceptionOrNull()
    assertThat(exceptionTooLow).isInstanceOf(IllegalArgumentException::class.java)

    val exceptionTooHigh = runCatching {
      LoggingSinkConfig.Remote(endpoint = "https://logs.example.com", batchSize = 1001)
    }.exceptionOrNull()
    assertThat(exceptionTooHigh).isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun `Remote sink flushIntervalMs must be at least 5 seconds`() {
    val valid = LoggingSinkConfig.Remote(endpoint = "https://logs.example.com", flushIntervalMs = 5_000L)
    assertThat(valid.flushIntervalMs).isEqualTo(5_000L)

    val exception = runCatching {
      LoggingSinkConfig.Remote(endpoint = "https://logs.example.com", flushIntervalMs = 4_999L)
    }.exceptionOrNull()
    assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
    assertThat(exception?.message).contains("flushIntervalMs")
  }

  @Test
  fun `Remote sink accepts custom headers`() {
    val headers = mapOf("Authorization" to "Bearer token123")
    val sink = LoggingSinkConfig.Remote(
      endpoint = "https://logs.example.com",
      headers = headers,
    )
    assertThat(sink.headers).containsEntry("Authorization", "Bearer token123")
  }

  @Test
  fun `LogLevel values are ordered by severity`() {
    val levels = LogLevel.values()
    assertThat(levels).hasLength(5)
    assertThat(levels[0]).isEqualTo(LogLevel.VERBOSE)
    assertThat(levels[1]).isEqualTo(LogLevel.DEBUG)
    assertThat(levels[2]).isEqualTo(LogLevel.INFO)
    assertThat(levels[3]).isEqualTo(LogLevel.WARN)
    assertThat(levels[4]).isEqualTo(LogLevel.ERROR)
  }

  @Test
  fun `LogLevel comparison by ordinal works for filtering`() {
    assertThat(LogLevel.WARN >= LogLevel.INFO).isTrue()
    assertThat(LogLevel.DEBUG >= LogLevel.WARN).isFalse()
    assertThat(LogLevel.ERROR >= LogLevel.VERBOSE).isTrue()
  }

  @Test
  fun `sealed class hierarchy is complete`() {
    val sinks: List<LoggingSinkConfig> = listOf(
      LoggingSinkConfig.Logcat(),
      LoggingSinkConfig.File(),
      LoggingSinkConfig.Remote(endpoint = "https://logs.example.com"),
    )

    sinks.forEach { sink ->
      when (sink) {
        is LoggingSinkConfig.Logcat -> assertThat(sink.tagPrefix).isNotEmpty()
        is LoggingSinkConfig.File -> assertThat(sink.maxLines).isGreaterThan(0)
        is LoggingSinkConfig.Remote -> assertThat(sink.endpoint).startsWith("https://")
      }
    }
  }
}
