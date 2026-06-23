/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.reliability

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.KioskOpsSdk
import com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider
import com.sarastarquant.kioskops.sdk.queue.EnqueueResult
import com.sarastarquant.kioskops.sdk.sync.SyncOnceResult
import com.sarastarquant.kioskops.sdk.sync.SyncPolicy
import com.sarastarquant.kioskops.sdk.transport.TransportResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real sync pipeline end to end against a local server: enqueue ->
 * queue -> batch -> POST -> server ack -> mark sent -> queue drains. The server
 * echoes an `accepted` ack for every posted event id, the way the batch ingest
 * contract expects.
 */
@RunWith(AndroidJUnit4::class)
class SyncPipelineInstrumentedTest : ReliabilitySdkTest() {

  private lateinit var server: MockWebServer

  @Before
  fun startServer() {
    server = MockWebServer()
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        val events = Json.parseToJsonElement(request.body?.utf8() ?: "")
          .jsonObject["events"]!!.jsonArray
        val acks = buildJsonArray {
          for (e in events) {
            val obj = e.jsonObject
            add(
              buildJsonObject {
                put("id", JsonPrimitive(obj["id"]!!.jsonPrimitive.content))
                put("idempotencyKey", JsonPrimitive(obj["idempotencyKey"]!!.jsonPrimitive.content))
                put("accepted", JsonPrimitive(true))
              },
            )
          }
        }
        val body = buildJsonObject {
          put("acceptedCount", JsonPrimitive(events.size))
          put("acks", acks)
        }
        return MockResponse.Builder()
          .code(200)
          .addHeader("Content-Type", "application/json")
          .body(body.toString())
          .build()
      }
    }
    server.start()
  }

  @After
  fun stopServer() {
    server.close()
  }

  @Test
  fun enqueueThenSyncOnce_marksEventsSentAndDrainsQueue() = runBlocking {
    val config = KioskOpsConfig(
      baseUrl = server.url("/").toString(),
      locationId = LOCATION_ID,
      kioskEnabled = true,
      syncPolicy = SyncPolicy.enabledDefaults(),
    )
    val sdk = KioskOpsSdk.init(ctx, { config }, cryptoProviderOverride = NoopCryptoProvider)
    try {
      val enqueue = sdk.enqueueDetailed(EVENT_TYPE, """{"scan":"SYNC-1"}""")
      assertThat(enqueue).isInstanceOf(EnqueueResult.Accepted::class.java)
      assertThat(sdk.queueDepth()).isEqualTo(1L)

      val result = sdk.syncOnce()
      assertThat(result).isInstanceOf(TransportResult.Success::class.java)
      assertThat((result as TransportResult.Success<SyncOnceResult>).value.sent).isEqualTo(1)

      // The event was acked, so it leaves the active queue.
      assertThat(sdk.queueDepth()).isEqualTo(0L)

      // Confirm the SDK actually posted the batch to the server.
      val recorded = server.takeRequest()
      assertThat(recorded.headers["X-KioskOps-SDK"]).isNotEmpty()
    } finally {
      sdk.shutdown()
    }
  }
}
