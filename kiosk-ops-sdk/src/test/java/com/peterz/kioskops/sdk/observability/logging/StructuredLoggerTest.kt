/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability.logging

import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.observability.CorrelationContext
import com.peterz.kioskops.sdk.observability.LogLevel
import com.peterz.kioskops.sdk.observability.LoggingSinkConfig
import com.peterz.kioskops.sdk.observability.ObservabilityPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tests for StructuredLogger.
 *
 * Security (BSI SYS.3.2.2.A12): Validates centralized logging
 * with multi-sink dispatch and correlation enrichment.
 */
@RunWith(RobolectricTestRunner::class)
class StructuredLoggerTest {

  private lateinit var testSink: TestLoggingSink
  private lateinit var logger: StructuredLogger

  @Before
  fun setup() {
    CorrelationContext.clear()
    testSink = TestLoggingSink(LogLevel.DEBUG)
    logger = StructuredLogger(
      sinks = listOf(testSink),
      policyProvider = { ObservabilityPolicy(structuredLoggingEnabled = true) },
    )
  }

  @After
  fun tearDown() {
    CorrelationContext.clear()
  }

  @Test
  fun `logs info message to sink`() {
    logger.i("TestTag", "Test message")

    assertThat(testSink.entries).hasSize(1)
    assertThat(testSink.entries[0].tag).isEqualTo("TestTag")
    assertThat(testSink.entries[0].message).isEqualTo("Test message")
    assertThat(testSink.entries[0].level).isEqualTo(LogLevel.INFO)
  }

  @Test
  fun `logs all severity levels`() {
    // Use VERBOSE minLevel to capture all levels
    val verboseSink = TestLoggingSink(LogLevel.VERBOSE)
    val verboseLogger = StructuredLogger(
      sinks = listOf(verboseSink),
      policyProvider = { ObservabilityPolicy(structuredLoggingEnabled = true) },
    )

    verboseLogger.v("Tag", "Verbose")
    verboseLogger.d("Tag", "Debug")
    verboseLogger.i("Tag", "Info")
    verboseLogger.w("Tag", "Warning")
    verboseLogger.e("Tag", "Error")

    assertThat(verboseSink.entries).hasSize(5)
    assertThat(verboseSink.entries.map { it.level }).containsExactly(
      LogLevel.VERBOSE,
      LogLevel.DEBUG,
      LogLevel.INFO,
      LogLevel.WARN,
      LogLevel.ERROR,
    ).inOrder()
  }

  @Test
  fun `enriches fields with correlation ID`() {
    CorrelationContext.set(CorrelationContext.KEY_CORRELATION_ID, "test-correlation-id")

    logger.i("Tag", "Message")

    assertThat(testSink.entries[0].fields["correlation_id"]).isEqualTo("test-correlation-id")
  }

  @Test
  fun `enriches fields with trace ID when present`() {
    CorrelationContext.set(CorrelationContext.KEY_TRACE_ID, "test-trace-id")

    logger.i("Tag", "Message")

    assertThat(testSink.entries[0].fields["trace_id"]).isEqualTo("test-trace-id")
  }

  @Test
  fun `enriches fields with operation name when present`() {
    CorrelationContext.set(CorrelationContext.KEY_OPERATION_NAME, "sync_operation")

    logger.i("Tag", "Message")

    assertThat(testSink.entries[0].fields["operation"]).isEqualTo("sync_operation")
  }

  @Test
  fun `enriches fields with thread name`() {
    logger.i("Tag", "Message")

    assertThat(testSink.entries[0].fields["thread"]).isNotNull()
    assertThat(testSink.entries[0].fields["thread"]).isNotEmpty()
  }

  @Test
  fun `includes caller-provided fields`() {
    logger.i("Tag", "Message", mapOf("custom_key" to "custom_value"))

    assertThat(testSink.entries[0].fields["custom_key"]).isEqualTo("custom_value")
  }

  @Test
  fun `caller fields override enriched fields`() {
    CorrelationContext.set(CorrelationContext.KEY_CORRELATION_ID, "auto-id")

    logger.i("Tag", "Message", mapOf("correlation_id" to "override-id"))

    assertThat(testSink.entries[0].fields["correlation_id"]).isEqualTo("override-id")
  }

  @Test
  fun `respects sink minLevel filtering`() {
    val warnSink = TestLoggingSink(LogLevel.WARN)
    val restrictedLogger = StructuredLogger(
      sinks = listOf(warnSink),
      policyProvider = { ObservabilityPolicy(structuredLoggingEnabled = true) },
    )

    restrictedLogger.d("Tag", "Debug - should not appear")
    restrictedLogger.i("Tag", "Info - should not appear")
    restrictedLogger.w("Tag", "Warning - should appear")
    restrictedLogger.e("Tag", "Error - should appear")

    assertThat(warnSink.entries).hasSize(2)
    assertThat(warnSink.entries.map { it.level }).containsExactly(
      LogLevel.WARN,
      LogLevel.ERROR,
    ).inOrder()
  }

  @Test
  fun `dispatches to multiple sinks`() {
    val sink1 = TestLoggingSink(LogLevel.DEBUG)
    val sink2 = TestLoggingSink(LogLevel.INFO)
    val multiLogger = StructuredLogger(
      sinks = listOf(sink1, sink2),
      policyProvider = { ObservabilityPolicy(structuredLoggingEnabled = true) },
    )

    multiLogger.d("Tag", "Debug only")
    multiLogger.i("Tag", "Both sinks")

    assertThat(sink1.entries).hasSize(2)
    assertThat(sink2.entries).hasSize(1)
  }

  @Test
  fun `does not log when disabled`() {
    val disabledLogger = StructuredLogger(
      sinks = listOf(testSink),
      policyProvider = { ObservabilityPolicy(structuredLoggingEnabled = false) },
    )

    disabledLogger.i("Tag", "Should not appear")

    assertThat(testSink.entries).isEmpty()
  }

  @Test
  fun `isEnabled returns false when logging disabled`() {
    val disabledLogger = StructuredLogger(
      sinks = listOf(testSink),
      policyProvider = { ObservabilityPolicy(structuredLoggingEnabled = false) },
    )

    assertThat(disabledLogger.isEnabled(LogLevel.INFO)).isFalse()
  }

  @Test
  fun `isEnabled returns true for enabled levels`() {
    assertThat(logger.isEnabled(LogLevel.DEBUG)).isTrue()
    assertThat(logger.isEnabled(LogLevel.INFO)).isTrue()
    assertThat(logger.isEnabled(LogLevel.ERROR)).isTrue()
  }

  @Test
  fun `log method works with level parameter`() {
    logger.log(LogLevel.WARN, "Tag", "Warning message")

    assertThat(testSink.entries).hasSize(1)
    assertThat(testSink.entries[0].level).isEqualTo(LogLevel.WARN)
  }

  @Test
  fun `includes throwable in log entry`() {
    val exception = RuntimeException("Test exception")

    logger.e("Tag", "Error with exception", exception)

    assertThat(testSink.entries[0].throwable).isEqualTo(exception)
  }

  @Test
  fun `warning includes throwable`() {
    val exception = IllegalStateException("Warning exception")

    logger.w("Tag", "Warning with exception", exception)

    assertThat(testSink.entries[0].throwable).isEqualTo(exception)
  }

  @Test
  fun `flush calls flush on all sinks`() {
    logger.flush()

    assertThat(testSink.flushCount).isEqualTo(1)
  }

  @Test
  fun `close calls close on all sinks`() {
    logger.close()

    assertThat(testSink.closeCount).isEqualTo(1)
  }

  @Test
  fun `fromPolicy creates logger with configured sinks`() {
    val policy = ObservabilityPolicy(
      structuredLoggingEnabled = true,
      loggingSinks = listOf(
        LoggingSinkConfig.Logcat(minLevel = LogLevel.INFO),
        LoggingSinkConfig.File(minLevel = LogLevel.WARN, maxLines = 500),
      ),
    )

    val policyLogger = StructuredLogger.fromPolicy { policy }

    // Logger should be created without error
    assertThat(policyLogger).isNotNull()
  }

  @Test
  fun `NoOpLogger does nothing`() {
    NoOpLogger.i("Tag", "Message")
    NoOpLogger.e("Tag", "Error", RuntimeException())
    NoOpLogger.log(LogLevel.WARN, "Tag", "Warning")

    assertThat(NoOpLogger.isEnabled(LogLevel.DEBUG)).isFalse()
    assertThat(NoOpLogger.isEnabled(LogLevel.ERROR)).isFalse()
  }

  /**
   * Test sink that captures log entries for verification.
   */
  private class TestLoggingSink(
    override val minLevel: LogLevel,
  ) : LoggingSink {
    val entries = CopyOnWriteArrayList<LogEntry>()
    var flushCount = 0
    var closeCount = 0

    override fun emit(entry: LogEntry) {
      entries.add(entry)
    }

    override fun flush() {
      flushCount++
    }

    override fun close() {
      closeCount++
    }
  }
}
