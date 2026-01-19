package com.peterz.kioskops.sdk.sync

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.KioskOpsConfig
import com.peterz.kioskops.sdk.audit.AuditTrail
import com.peterz.kioskops.sdk.compliance.RetentionPolicy
import com.peterz.kioskops.sdk.compliance.SecurityPolicy
import com.peterz.kioskops.sdk.compliance.TelemetryPolicy
import com.peterz.kioskops.sdk.crypto.NoopCryptoProvider
import com.peterz.kioskops.sdk.logging.RingLog
import com.peterz.kioskops.sdk.queue.QueueRepository
import com.peterz.kioskops.sdk.telemetry.TelemetrySink
import com.peterz.kioskops.sdk.transport.OkHttpTransport
import com.peterz.kioskops.sdk.transport.TransportResult
import com.peterz.kioskops.sdk.util.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test

class SyncEngineTest {
  private lateinit var server: MockWebServer

  private class TestClock(var now: Long) : Clock {
    override fun nowMs(): Long = now
  }

  @Before
  fun setUp() {
    server = MockWebServer()
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        val body = request.body.readUtf8()
        val parsed = Json.parseToJsonElement(body).jsonObject
        val events = parsed["events"]!!.jsonArray

        val acks = buildJsonArray {
          for (e in events) {
            val obj = e.jsonObject
            add(
              buildJsonObject {
                put("id", obj["id"]!!.jsonPrimitive.content)
                put("idempotencyKey", obj["idempotencyKey"]!!.jsonPrimitive.content)
                put("accepted", true)
              }
            )
          }
        }

        val resp = buildJsonObject {
          put("acceptedCount", events.size)
          put("acks", acks)
        }

        return MockResponse()
          .setResponseCode(200)
          .setHeader("Content-Type", "application/json")
          .setBody(resp.toString())
      }
    }
    server.start()
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun `flushOnce sends batch and marks events as sent`() = runTest {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val logs = RingLog(context)
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    val cfg = KioskOpsConfig(
      baseUrl = server.url("/").toString(),
      locationId = "TEST",
      kioskEnabled = false,
      syncPolicy = SyncPolicy(enabled = true, endpointPath = "events/batch", batchSize = 50),
      securityPolicy = SecurityPolicy.maximalistDefaults().copy(encryptQueuePayloads = false),
      retentionPolicy = RetentionPolicy.maximalistDefaults(),
      telemetryPolicy = TelemetryPolicy.maximalistDefaults()
    )

    val queue = QueueRepository(context, logs, NoopCryptoProvider)
    val audit = AuditTrail(context, retentionProvider = { cfg.retentionPolicy }, clock = com.peterz.kioskops.sdk.util.Clock.SYSTEM, crypto = NoopCryptoProvider)
    val transport = OkHttpTransport(OkHttpClient.Builder().build(), json, logs, authProvider = null)

    // two events
    assertThat(queue.enqueue("T1", "{\"x\":1}", cfg).isAccepted).isTrue()
    assertThat(queue.enqueue("T2", "{\"y\":2}", cfg).isAccepted).isTrue()

    val engine = SyncEngine(
      context = context,
      cfgProvider = { cfg },
      queue = queue,
      transport = transport,
      logs = logs,
      telemetry = object : TelemetrySink { override fun emit(event: String, fields: Map<String, String>) { } },
      audit = audit,
      clock = Clock.SYSTEM
    )

    val r = engine.flushOnce()
    assertThat(r).isInstanceOf(TransportResult.Success::class.java)
    val success = r as TransportResult.Success
    assertThat(success.value.sent).isEqualTo(2)
    assertThat(queue.countActive()).isEqualTo(0)
  }

  @Test
  fun `transient HTTP 500 applies backoff and does not make events eligible immediately`() = runTest {
    // Always 500
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        return MockResponse().setResponseCode(500).setBody("oops")
      }
    }

    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val logs = RingLog(context)
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    val cfg = KioskOpsConfig(
      baseUrl = server.url("/").toString(),
      locationId = "TEST",
      kioskEnabled = false,
      syncPolicy = SyncPolicy(enabled = true, endpointPath = "events/batch", batchSize = 50),
      securityPolicy = SecurityPolicy.maximalistDefaults().copy(encryptQueuePayloads = false),
      retentionPolicy = RetentionPolicy.maximalistDefaults(),
      telemetryPolicy = TelemetryPolicy.maximalistDefaults()
    )

    val queue = QueueRepository(context, logs, NoopCryptoProvider)
    val audit = AuditTrail(context, retentionProvider = { cfg.retentionPolicy }, clock = Clock.SYSTEM, crypto = NoopCryptoProvider)
    val transport = OkHttpTransport(OkHttpClient.Builder().build(), json, logs, authProvider = null)

    assertThat(queue.enqueue("T1", "{\"x\":1}", cfg).isAccepted).isTrue()

    val clock = TestClock(now = 1_000L)
    val engine = SyncEngine(
      context = context,
      cfgProvider = { cfg },
      queue = queue,
      transport = transport,
      logs = logs,
      telemetry = object : TelemetrySink { override fun emit(event: String, fields: Map<String, String>) {} },
      audit = audit,
      clock = clock
    )

    val r = engine.flushOnce()
    assertThat(r).isInstanceOf(TransportResult.TransientFailure::class.java)
    assertThat(queue.countActive()).isEqualTo(1)

    // Immediately after failure, event should not be eligible due to nextAttemptAtMs
    val none = queue.nextBatch(nowMs = clock.now, limit = 10)
    assertThat(none).isEmpty()

    // After backoff (>= 5s), it becomes eligible again
    clock.now += 6_000L
    val eligible = queue.nextBatch(nowMs = clock.now, limit = 10)
    assertThat(eligible).isNotEmpty()
  }

  @Test
  fun `per-event retryable false makes event permanently ineligible for retry`() = runTest {
    // Respond with a per-event rejection (retryable=false)
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        val body = request.body.readUtf8()
        val parsed = Json.parseToJsonElement(body).jsonObject
        val events = parsed["events"]!!.jsonArray
        val e0 = events[0].jsonObject
        val resp = buildJsonObject {
          put("acceptedCount", 0)
          put(
            "acks",
            buildJsonArray {
              add(
                buildJsonObject {
                  put("id", e0["id"]!!.jsonPrimitive.content)
                  put("idempotencyKey", e0["idempotencyKey"]!!.jsonPrimitive.content)
                  put("accepted", false)
                  put("retryable", false)
                  put("error", "schema_violation")
                }
              )
            }
          )
        }
        return MockResponse()
          .setResponseCode(200)
          .setHeader("Content-Type", "application/json")
          .setBody(resp.toString())
      }
    }

    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val logs = RingLog(context)
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    val cfg = KioskOpsConfig(
      baseUrl = server.url("/").toString(),
      locationId = "TEST",
      kioskEnabled = false,
      syncPolicy = SyncPolicy(enabled = true, endpointPath = "events/batch", batchSize = 50),
      securityPolicy = SecurityPolicy.maximalistDefaults().copy(encryptQueuePayloads = false),
      retentionPolicy = RetentionPolicy.maximalistDefaults(),
      telemetryPolicy = TelemetryPolicy.maximalistDefaults()
    )

    val queue = QueueRepository(context, logs, NoopCryptoProvider)
    val audit = AuditTrail(context, retentionProvider = { cfg.retentionPolicy }, clock = Clock.SYSTEM, crypto = NoopCryptoProvider)
    val transport = OkHttpTransport(OkHttpClient.Builder().build(), json, logs, authProvider = null)
    assertThat(queue.enqueue("T1", "{\"x\":1}", cfg).isAccepted).isTrue()

    val clock = TestClock(now = 1_000L)
    val engine = SyncEngine(
      context = context,
      cfgProvider = { cfg },
      queue = queue,
      transport = transport,
      logs = logs,
      telemetry = object : TelemetrySink { override fun emit(event: String, fields: Map<String, String>) {} },
      audit = audit,
      clock = clock
    )

    val r = engine.flushOnce()
    assertThat(r).isInstanceOf(TransportResult.Success::class.java)
    assertThat(queue.countActive()).isEqualTo(1)

    val quarantined = queue.quarantinedSummaries(limit = 10)
    assertThat(quarantined).isNotEmpty()
    assertThat(quarantined.first().reason).contains("server_non_retryable")

    // Even far in the future, event remains ineligible due to permanentFailure=1 (quarantined)
    clock.now += 365L * 24L * 60L * 60L * 1000L
    val eligible = queue.nextBatch(nowMs = clock.now, limit = 10)
    assertThat(eligible).isEmpty()
    assertThat(queue.quarantinedSummaries(10)).isNotEmpty()
  }

  @Test
  fun `maxAttemptsPerEvent moves event to quarantine even if retryable`() = runTest {
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        val body = request.body.readUtf8()
        val parsed = Json.parseToJsonElement(body).jsonObject
        val events = parsed["events"]!!.jsonArray
        val e0 = events[0].jsonObject
        val resp = buildJsonObject {
          put("acceptedCount", 0)
          put(
            "acks",
            buildJsonArray {
              add(
                buildJsonObject {
                  put("id", e0["id"]!!.jsonPrimitive.content)
                  put("idempotencyKey", e0["idempotencyKey"]!!.jsonPrimitive.content)
                  put("accepted", false)
                  put("retryable", true)
                  put("error", "validation_failed")
                }
              )
            }
          )
        }
        return MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(resp.toString())
      }
    }

    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val logs = RingLog(context)
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    val cfg = KioskOpsConfig(
      baseUrl = server.url("/").toString(),
      locationId = "TEST",
      kioskEnabled = false,
      syncPolicy = SyncPolicy(enabled = true, endpointPath = "events/batch", batchSize = 50, maxAttemptsPerEvent = 1),
      securityPolicy = SecurityPolicy.maximalistDefaults().copy(encryptQueuePayloads = false),
      retentionPolicy = RetentionPolicy.maximalistDefaults(),
      telemetryPolicy = TelemetryPolicy.maximalistDefaults()
    )

    val queue = QueueRepository(context, logs, NoopCryptoProvider)
    val audit = AuditTrail(context, retentionProvider = { cfg.retentionPolicy }, clock = Clock.SYSTEM, crypto = NoopCryptoProvider)
    val transport = OkHttpTransport(OkHttpClient.Builder().build(), json, logs, authProvider = null)

    assertThat(queue.enqueue("T1", "{\"x\":1}", cfg).isAccepted).isTrue()

    val clock = TestClock(now = 1_000L)
    val engine = SyncEngine(
      context = context,
      cfgProvider = { cfg },
      queue = queue,
      transport = transport,
      logs = logs,
      telemetry = object : TelemetrySink { override fun emit(event: String, fields: Map<String, String>) {} },
      audit = audit,
      clock = clock
    )

    val r = engine.flushOnce()
    assertThat(r).isInstanceOf(TransportResult.Success::class.java)
    val quarantined = queue.quarantinedSummaries(limit = 10)
    assertThat(quarantined).isNotEmpty()
    assertThat(quarantined.first().reason).contains("max_attempts_exceeded")
  }
}
