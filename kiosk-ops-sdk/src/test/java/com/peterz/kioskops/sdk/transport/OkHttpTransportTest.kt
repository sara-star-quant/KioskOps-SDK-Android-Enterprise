/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.transport

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.KioskOpsConfig
import com.peterz.kioskops.sdk.logging.RingLog
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OkHttpTransportTest {

  private lateinit var server: MockWebServer
  private lateinit var transport: OkHttpTransport
  private lateinit var logs: RingLog

  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  @Before
  fun setUp() {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    logs = RingLog(ctx)
    server = MockWebServer()
    server.start()
    transport = OkHttpTransport(
      client = OkHttpClient(),
      json = json,
      logs = logs,
      authProvider = null,
    )
  }

  @After
  fun tearDown() {
    server.close()
  }

  private fun config(baseUrl: String = server.url("/").toString()) = KioskOpsConfig(
    baseUrl = baseUrl,
    locationId = "test-loc",
    kioskEnabled = true,
  )

  private fun request() = BatchSendRequest(
    batchId = "batch-1",
    deviceId = "device-1",
    appVersion = "1.0.0",
    locationId = "test-loc",
    sentAtEpochMs = 1_700_000_000_000L,
    events = listOf(
      TransportEvent(
        id = "evt-1",
        idempotencyKey = "idem-1",
        type = "test_event",
        payloadJson = """{"key":"value"}""",
        createdAtEpochMs = 1_700_000_000_000L,
      )
    )
  )

  private fun mockResponse(code: Int, body: String) =
    MockResponse.Builder()
      .code(code)
      .addHeader("Content-Type", "application/json")
      .body(body)
      .build()

  @Test
  fun `successful batch send returns Success`() = runTest {
    server.enqueue(
      mockResponse(200, """{"acceptedCount":1,"acks":[{"id":"evt-1","idempotencyKey":"idem-1","accepted":true}]}""")
    )

    val result = transport.sendBatch(config(), request())
    assertThat(result).isInstanceOf(TransportResult.Success::class.java)
    val success = result as TransportResult.Success
    assertThat(success.value.acceptedCount).isEqualTo(1)
    assertThat(success.httpStatus).isEqualTo(200)
  }

  @Test
  fun `HTTP 500 returns TransientFailure`() = runTest {
    server.enqueue(mockResponse(500, "Internal Server Error"))

    val result = transport.sendBatch(config(), request())
    assertThat(result).isInstanceOf(TransportResult.TransientFailure::class.java)
    assertThat((result as TransportResult.TransientFailure).httpStatus).isEqualTo(500)
  }

  @Test
  fun `HTTP 429 returns TransientFailure`() = runTest {
    server.enqueue(mockResponse(429, "Too Many Requests"))

    val result = transport.sendBatch(config(), request())
    assertThat(result).isInstanceOf(TransportResult.TransientFailure::class.java)
    assertThat((result as TransportResult.TransientFailure).httpStatus).isEqualTo(429)
  }

  @Test
  fun `HTTP 400 returns PermanentFailure`() = runTest {
    server.enqueue(mockResponse(400, "Bad Request"))

    val result = transport.sendBatch(config(), request())
    assertThat(result).isInstanceOf(TransportResult.PermanentFailure::class.java)
    assertThat((result as TransportResult.PermanentFailure).httpStatus).isEqualTo(400)
  }

  @Test
  fun `HTTP 401 returns TransientFailure for auth retry`() = runTest {
    server.enqueue(mockResponse(401, "Unauthorized"))

    val result = transport.sendBatch(config(), request())
    assertThat(result).isInstanceOf(TransportResult.TransientFailure::class.java)
  }

  @Test
  fun `example_invalid baseUrl returns PermanentFailure without network call`() = runTest {
    val result = transport.sendBatch(config(baseUrl = "https://example.invalid"), request())
    assertThat(result).isInstanceOf(TransportResult.PermanentFailure::class.java)
    assertThat(server.requestCount).isEqualTo(0)
  }

  @Test
  fun `auth provider injects headers`() = runTest {
    val authedTransport = OkHttpTransport(
      client = OkHttpClient(),
      json = json,
      logs = logs,
      authProvider = AuthProvider { builder ->
        builder.addHeader("Authorization", "Bearer test-token")
      },
    )

    server.enqueue(
      mockResponse(200, """{"acceptedCount":1,"acks":[]}""")
    )

    authedTransport.sendBatch(config(), request())
    val recorded = server.takeRequest()
    assertThat(recorded.headers["Authorization"]).isEqualTo("Bearer test-token")
  }

  @Test
  fun `request includes SDK version header`() = runTest {
    server.enqueue(
      mockResponse(200, """{"acceptedCount":1,"acks":[]}""")
    )

    transport.sendBatch(config(), request())
    val recorded = server.takeRequest()
    assertThat(recorded.headers["X-KioskOps-SDK"]).isNotEmpty()
  }
}
