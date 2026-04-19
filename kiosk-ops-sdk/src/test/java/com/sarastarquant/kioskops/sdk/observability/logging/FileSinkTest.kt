/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.observability.logging

import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.observability.LogLevel
import org.junit.Test
import java.time.Instant

class FileSinkTest {

  private fun entry(
    level: LogLevel = LogLevel.INFO,
    tag: String = "Test",
    message: String = "test message",
    fields: Map<String, String> = emptyMap(),
    timestamp: Instant = Instant.ofEpochMilli(1_700_000_000_000L),
  ) = LogEntry(timestamp = timestamp, level = level, tag = tag, message = message, fields = fields)

  @Test
  fun `emit stores entry and getEntries retrieves it`() {
    val sink = FileSink()
    sink.emit(entry())
    assertThat(sink.getEntries()).hasSize(1)
    assertThat(sink.size()).isEqualTo(1)
  }

  @Test
  fun `respects maxLines capacity`() {
    val sink = FileSink(maxLines = 3)
    for (i in 1..5) {
      sink.emit(entry(message = "msg-$i"))
    }
    assertThat(sink.getEntries()).hasSize(3)
    assertThat(sink.size()).isEqualTo(3)
  }

  @Test
  fun `concurrent emit keeps size in step with buffer and never goes negative`() {
    val sink = FileSink(maxLines = 50)
    val threads = 8
    val perThread = 200
    val pool = java.util.concurrent.Executors.newFixedThreadPool(threads)
    val latch = java.util.concurrent.CountDownLatch(threads)
    repeat(threads) { t ->
      pool.submit {
        repeat(perThread) { i -> sink.emit(entry(message = "t$t-$i")) }
        latch.countDown()
      }
    }
    latch.await()
    pool.shutdown()

    assertThat(sink.size()).isEqualTo(sink.getEntries().size)
    assertThat(sink.size()).isAtLeast(0)
    assertThat(sink.size()).isAtMost(50)
    // With 1600 writes and cap 50, the buffer must be at capacity, not drained.
    assertThat(sink.size()).isEqualTo(50)
  }

  @Test
  fun `formatForExport produces text`() {
    val sink = FileSink()
    sink.emit(entry(tag = "MyTag", message = "hello world"))
    val output = sink.formatForExport()
    assertThat(output).contains("MyTag")
    assertThat(output).contains("hello world")
  }

  @Test
  fun `formatAsJsonArray produces valid JSON`() {
    val sink = FileSink()
    sink.emit(entry(tag = "A", message = "first"))
    sink.emit(entry(tag = "B", message = "second"))
    val json = sink.formatAsJsonArray()
    assertThat(json).startsWith("[")
    assertThat(json).endsWith("]")
  }

  @Test
  fun `filter by level`() {
    val sink = FileSink(minLevel = LogLevel.DEBUG)
    sink.emit(entry(level = LogLevel.DEBUG, message = "debug"))
    sink.emit(entry(level = LogLevel.ERROR, message = "error"))
    sink.emit(entry(level = LogLevel.INFO, message = "info"))

    val errors = sink.filter(minLevel = LogLevel.ERROR)
    assertThat(errors).hasSize(1)
    assertThat(errors.first().message).isEqualTo("error")
  }

  @Test
  fun `filter by tag`() {
    val sink = FileSink()
    sink.emit(entry(tag = "Network"))
    sink.emit(entry(tag = "Queue"))
    sink.emit(entry(tag = "Network"))

    val networkLogs = sink.filter(tag = "Network")
    assertThat(networkLogs).hasSize(2)
  }

  @Test
  fun `filter by correlationId`() {
    val sink = FileSink()
    sink.emit(entry(fields = mapOf("correlation_id" to "abc")))
    sink.emit(entry(fields = mapOf("correlation_id" to "def")))

    val filtered = sink.filter(correlationId = "abc")
    assertThat(filtered).hasSize(1)
  }

  @Test
  fun `clear empties buffer`() {
    val sink = FileSink()
    sink.emit(entry())
    sink.emit(entry())
    sink.clear()
    assertThat(sink.size()).isEqualTo(0)
    assertThat(sink.getEntries()).isEmpty()
  }

  @Test
  fun `entriesSince filters by timestamp`() {
    val sink = FileSink()
    sink.emit(entry(timestamp = Instant.ofEpochMilli(1000)))
    sink.emit(entry(timestamp = Instant.ofEpochMilli(2000)))
    sink.emit(entry(timestamp = Instant.ofEpochMilli(3000)))

    val recent = sink.entriesSince(2000)
    assertThat(recent).hasSize(2)
  }

  @Test
  fun `getStatistics returns correct counts`() {
    val sink = FileSink(maxLines = 100)
    sink.emit(entry(level = LogLevel.INFO))
    sink.emit(entry(level = LogLevel.ERROR))
    sink.emit(entry(level = LogLevel.INFO))

    val stats = sink.getStatistics()
    assertThat(stats.totalEntries).isEqualTo(3)
    assertThat(stats.maxCapacity).isEqualTo(100)
    assertThat(stats.levelBreakdown[LogLevel.INFO]).isEqualTo(2)
    assertThat(stats.levelBreakdown[LogLevel.ERROR]).isEqualTo(1)
  }
}
