/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.debug

import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.observability.logging.Logger
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NetworkLoggingInterceptorTest {

  private lateinit var server: MockWebServer
  private val logEntries = mutableListOf<LogEntry>()

  data class LogEntry(val level: String, val tag: String, val message: String, val fields: Map<String, String> = emptyMap())

  private val capturingLogger = object : Logger {
    override fun v(tag: String, message: String, fields: Map<String, String>) { logEntries.add(LogEntry("V", tag, message, fields)) }
    override fun d(tag: String, message: String, fields: Map<String, String>) { logEntries.add(LogEntry("D", tag, message, fields)) }
    override fun i(tag: String, message: String, fields: Map<String, String>) { logEntries.add(LogEntry("I", tag, message, fields)) }
    override fun w(tag: String, message: String, throwable: Throwable?, fields: Map<String, String>) {
      logEntries.add(LogEntry("W", tag, message, fields))
    }
    override fun e(tag: String, message: String, throwable: Throwable?, fields: Map<String, String>) {
      logEntries.add(LogEntry("E", tag, message, fields))
    }
    override fun log(
      level: com.sarastarquant.kioskops.sdk.observability.LogLevel,
      tag: String,
      message: String,
      throwable: Throwable?,
      fields: Map<String, String>,
    ) {
      logEntries.add(LogEntry(level.name, tag, message, fields))
    }
    override fun isEnabled(level: com.sarastarquant.kioskops.sdk.observability.LogLevel) = true
  }

  @Before
  fun setUp() {
    DebugUtils.isDebugBuild = true
    server = MockWebServer()
    server.start()
    logEntries.clear()
  }

  @After
  fun tearDown() {
    server.close()
    DebugUtils.isDebugBuild = false
  }

  private fun clientWith(interceptor: NetworkLoggingInterceptor): OkHttpClient {
    return OkHttpClient.Builder().addInterceptor(interceptor).build()
  }

  @Test
  fun `logs request method and URL`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())
    val interceptor = NetworkLoggingInterceptor(capturingLogger)
    val client = clientWith(interceptor)

    client.newCall(Request.Builder().url(server.url("/test")).build()).execute()

    val requestLog = logEntries.first { it.message.contains(">>>") }
    assertThat(requestLog.message).contains("GET")
    assertThat(requestLog.message).contains("/test")
  }

  @Test
  fun `logs response code and duration`() {
    server.enqueue(MockResponse.Builder().code(201).body("created").build())
    val interceptor = NetworkLoggingInterceptor(capturingLogger)
    val client = clientWith(interceptor)

    client.newCall(Request.Builder().url(server.url("/")).build()).execute()

    val responseLog = logEntries.first { it.message.contains("<<<") && it.message.contains("201") }
    assertThat(responseLog.fields["duration_ms"]).isNotNull()
  }

  @Test
  fun `redacts Authorization header`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())
    val interceptor = NetworkLoggingInterceptor(capturingLogger)
    val client = clientWith(interceptor)

    client.newCall(
      Request.Builder()
        .url(server.url("/"))
        .header("Authorization", "Bearer secret-token")
        .build()
    ).execute()

    val headerLogs = logEntries.filter { it.message.contains("authorization", ignoreCase = true) }
    assertThat(headerLogs).isNotEmpty()
    assertThat(headerLogs.first().message).contains("[REDACTED]")
    assertThat(headerLogs.first().message).doesNotContain("secret-token")
  }

  @Test
  fun `does not redact non-sensitive headers`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())
    val interceptor = NetworkLoggingInterceptor(capturingLogger)
    val client = clientWith(interceptor)

    client.newCall(
      Request.Builder()
        .url(server.url("/"))
        .header("X-Custom", "visible-value")
        .build()
    ).execute()

    val customHeaderLog = logEntries.find { it.message.contains("X-Custom", ignoreCase = true) }
    assertThat(customHeaderLog).isNotNull()
    assertThat(customHeaderLog!!.message).contains("visible-value")
  }

  @Test
  fun `respects logRequestHeaders false`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())
    val interceptor = NetworkLoggingInterceptor(capturingLogger, logRequestHeaders = false)
    val client = clientWith(interceptor)

    client.newCall(
      Request.Builder()
        .url(server.url("/"))
        .header("X-Test", "value")
        .build()
    ).execute()

    // Request headers should NOT appear (only >>> method line and <<< response)
    val requestHeaderLogs = logEntries.filter { it.message.startsWith(">>> ") && it.message.contains("X-Test") }
    assertThat(requestHeaderLogs).isEmpty()
  }

  @Test
  fun `logs error on network failure`() {
    server.close() // Force connection failure
    val interceptor = NetworkLoggingInterceptor(capturingLogger)
    val client = clientWith(interceptor)

    try {
      client.newCall(Request.Builder().url("http://localhost:1/fail").build()).execute()
    } catch (_: Exception) {
      // Expected
    }

    val errorLogs = logEntries.filter { it.level == "E" }
    assertThat(errorLogs).isNotEmpty()
    assertThat(errorLogs.first().message).contains("FAILED")
  }
}
