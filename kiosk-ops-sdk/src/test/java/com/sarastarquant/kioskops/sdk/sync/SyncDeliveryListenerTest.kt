package com.sarastarquant.kioskops.sdk.sync

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.audit.PersistentAuditTrail
import com.sarastarquant.kioskops.sdk.compliance.RetentionPolicy
import com.sarastarquant.kioskops.sdk.compliance.SecurityPolicy
import com.sarastarquant.kioskops.sdk.compliance.TelemetryPolicy
import com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider
import com.sarastarquant.kioskops.sdk.logging.RingLog
import com.sarastarquant.kioskops.sdk.queue.QueueRepository
import com.sarastarquant.kioskops.sdk.telemetry.TelemetrySink
import com.sarastarquant.kioskops.sdk.transport.OkHttpTransport
import com.sarastarquant.kioskops.sdk.transport.TransportResult
import com.sarastarquant.kioskops.sdk.util.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncDeliveryListenerTest {
  private lateinit var server: MockWebServer
  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
  private val logs = RingLog(context)
  private val cfg by lazy {
    KioskOpsConfig(
      baseUrl = server.url("/").toString(),
      locationId = "TEST",
      kioskEnabled = false,
      syncPolicy = SyncPolicy(enabled = true, endpointPath = "events/batch", batchSize = 50),
      securityPolicy = SecurityPolicy.maximalistDefaults().copy(encryptQueuePayloads = false),
      retentionPolicy = RetentionPolicy.maximalistDefaults(),
      telemetryPolicy = TelemetryPolicy.maximalistDefaults(),
    )
  }
  private val queue = QueueRepository(context, logs, NoopCryptoProvider)

  /** Server ack shape for every event in the batch. */
  private class AckShape(
    val accepted: Boolean,
    val retryable: Boolean = true,
    val error: String? = null,
    val serverEventId: String? = null,
  )

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
  }

  @After
  fun tearDown() {
    server.close()
  }

  private fun respondWith(shape: AckShape) {
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        val events = Json.parseToJsonElement(request.body?.utf8() ?: "").jsonObject["events"]!!.jsonArray
        val acks = buildJsonArray {
          for (e in events) {
            add(
              buildJsonObject {
                put("id", JsonPrimitive(e.jsonObject["id"]!!.jsonPrimitive.content))
                put("idempotencyKey", JsonPrimitive(e.jsonObject["idempotencyKey"]!!.jsonPrimitive.content))
                put("accepted", JsonPrimitive(shape.accepted))
                put("retryable", JsonPrimitive(shape.retryable))
                put("error", shape.error?.let { JsonPrimitive(it) } ?: JsonNull)
                put("serverEventId", shape.serverEventId?.let { JsonPrimitive(it) } ?: JsonNull)
              },
            )
          }
        }
        val body = buildJsonObject {
          put("acceptedCount", JsonPrimitive(if (shape.accepted) events.size else 0))
          put("acks", acks)
        }
        return MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body.toString()).build()
      }
    }
  }

  private fun engine(onDelivery: (EventDelivery) -> Unit): SyncEngine {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    return SyncEngine(
      context = context,
      cfgProvider = { cfg },
      queue = queue,
      transport = OkHttpTransport(OkHttpClient.Builder().build(), json, logs, authProvider = null),
      logs = logs,
      telemetry = object : TelemetrySink { override fun emit(event: String, fields: Map<String, String>) = Unit },
      audit = PersistentAuditTrail(context, retentionProvider = { cfg.retentionPolicy }, clock = Clock.SYSTEM),
      clock = Clock.SYSTEM,
      onDelivery = onDelivery,
    )
  }

  @Test
  fun `accepted ack reports DELIVERED after the queue is updated`() = runTest {
    respondWith(AckShape(accepted = true, serverEventId = "srv-1"))
    queue.enqueue("T1", "{\"x\":1}", cfg)
    val seen = mutableListOf<EventDelivery>()
    var activeInsideCallback = -1L

    engine { d ->
      activeInsideCallback = kotlinx.coroutines.runBlocking { queue.countActive() }
      seen += d
    }.flushOnce()

    assertThat(seen).hasSize(1)
    assertThat(seen[0].status).isEqualTo(DeliveryStatus.DELIVERED)
    assertThat(seen[0].type).isEqualTo("T1")
    assertThat(seen[0].serverEventId).isEqualTo("srv-1")
    assertThat(activeInsideCallback).isEqualTo(0)
  }

  @Test
  fun `non-retryable rejection reports QUARANTINED with the server error`() = runTest {
    respondWith(AckShape(accepted = false, retryable = false, error = "schema_violation"))
    queue.enqueue("T1", "{\"x\":1}", cfg)
    val seen = mutableListOf<EventDelivery>()

    engine { seen += it }.flushOnce()

    assertThat(seen.map { it.status }).containsExactly(DeliveryStatus.QUARANTINED)
    assertThat(seen[0].error).isEqualTo("schema_violation")
  }

  @Test
  fun `retryable rejection reports RETRY_SCHEDULED`() = runTest {
    respondWith(AckShape(accepted = false, retryable = true, error = "busy"))
    queue.enqueue("T1", "{\"x\":1}", cfg)
    val seen = mutableListOf<EventDelivery>()

    engine { seen += it }.flushOnce()

    assertThat(seen.map { it.status }).containsExactly(DeliveryStatus.RETRY_SCHEDULED)
    assertThat(seen[0].error).isEqualTo("busy")
  }

  @Test
  fun `a throwing listener does not break the flush`() = runTest {
    respondWith(AckShape(accepted = true))
    queue.enqueue("T1", "{\"x\":1}", cfg)
    queue.enqueue("T2", "{\"y\":2}", cfg)
    var calls = 0

    val r = engine { calls++; error("host bug") }.flushOnce()

    assertThat(r).isInstanceOf(TransportResult.Success::class.java)
    assertThat((r as TransportResult.Success).value.sent).isEqualTo(2)
    assertThat(calls).isEqualTo(2)
    assertThat(queue.countActive()).isEqualTo(0)
  }

  @Test
  fun `batch level HTTP failure reports nothing`() = runTest {
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest) = MockResponse.Builder().code(500).body("oops").build()
    }
    queue.enqueue("T1", "{\"x\":1}", cfg)
    val seen = mutableListOf<EventDelivery>()

    engine { seen += it }.flushOnce()

    assertThat(seen).isEmpty()
  }
}
