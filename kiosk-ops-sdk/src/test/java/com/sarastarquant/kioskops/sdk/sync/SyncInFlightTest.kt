/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.audit.DeviceAttestationProvider
import com.sarastarquant.kioskops.sdk.audit.PersistentAuditTrail
import com.sarastarquant.kioskops.sdk.audit.SoftwareAttestationProvider
import com.sarastarquant.kioskops.sdk.audit.db.AuditDatabase
import com.sarastarquant.kioskops.sdk.compliance.RetentionPolicy
import com.sarastarquant.kioskops.sdk.compliance.SecurityPolicy
import com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider
import com.sarastarquant.kioskops.sdk.logging.RingLog
import com.sarastarquant.kioskops.sdk.queue.QueueRepository
import com.sarastarquant.kioskops.sdk.telemetry.TelemetrySink
import com.sarastarquant.kioskops.sdk.transport.BatchSendRequest
import com.sarastarquant.kioskops.sdk.transport.BatchSendResponse
import com.sarastarquant.kioskops.sdk.transport.EventAck
import com.sarastarquant.kioskops.sdk.transport.Transport
import com.sarastarquant.kioskops.sdk.transport.TransportResult
import com.sarastarquant.kioskops.sdk.util.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncInFlightTest {

  private lateinit var ctx: Context
  private lateinit var auditDb: AuditDatabase
  private val clock = object : Clock {
    var now = 1_000L
    override fun nowMs(): Long = now
  }

  private data class Row(val state: String, val attempts: Int, val nextAttemptAt: Long)

  @Before
  fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()
    auditDb = Room.inMemoryDatabaseBuilder(ctx, AuditDatabase::class.java).allowMainThreadQueries().build()
    AuditDatabase.setInstance(auditDb)
  }

  @After
  fun tearDown() {
    auditDb.close()
    AuditDatabase.setInstance(null)
  }

  private fun cfg(checkpoints: Boolean = false, batchSize: Int = 50, maxAttempts: Int = 12) = KioskOpsConfig(
    baseUrl = "https://sync.test/",
    locationId = "TEST",
    kioskEnabled = false,
    syncPolicy = SyncPolicy(enabled = true, endpointPath = "events/batch", batchSize = batchSize,
      maxAttemptsPerEvent = maxAttempts),
    securityPolicy = SecurityPolicy.maximalistDefaults()
      .copy(encryptQueuePayloads = false, signedAuditCheckpoints = checkpoints),
  )

  private class Fixture(val queue: QueueRepository, val engine: SyncEngine)

  private suspend fun fixture(
    transport: Transport,
    cfg: KioskOpsConfig = cfg(),
    provider: DeviceAttestationProvider = SoftwareAttestationProvider(),
    events: Int = 2,
    onDelivery: (EventDelivery) -> Unit = {},
  ): Fixture {
    val logs = RingLog(ctx)
    val queue = QueueRepository(ctx, logs, NoopCryptoProvider)
    repeat(events) { assertThat(queue.enqueue("T$it", "{\"x\":$it}", cfg).isAccepted).isTrue() }
    val engine = SyncEngine(
      context = ctx,
      cfgProvider = { cfg },
      queue = queue,
      transport = transport,
      logs = logs,
      telemetry = object : TelemetrySink { override fun emit(event: String, fields: Map<String, String>) = Unit },
      audit = PersistentAuditTrail(ctx, { RetentionPolicy.maximalistDefaults() }, clock,
        attestationProvider = { provider }),
      clock = clock,
      onDelivery = onDelivery,
    )
    return Fixture(queue, engine)
  }

  /** Reads stored rows straight from the queue database file. */
  private fun rows(): List<Row> {
    val db = SQLiteDatabase.openDatabase(
      ctx.getDatabasePath("kiosk_ops_queue.db").path, null, SQLiteDatabase.OPEN_READONLY,
    )
    db.use {
      it.rawQuery("SELECT state, attempts, nextAttemptAtEpochMs FROM queue_events ORDER BY createdAtEpochMs", null)
        .use { c ->
          val out = mutableListOf<Row>()
          while (c.moveToNext()) out += Row(c.getString(0), c.getInt(1), c.getLong(2))
          return out
        }
    }
  }

  /** Stored rows keyed by event type, for tests that need per-event state. */
  private fun rowsByType(): Map<String, Row> {
    val db = SQLiteDatabase.openDatabase(
      ctx.getDatabasePath("kiosk_ops_queue.db").path, null, SQLiteDatabase.OPEN_READONLY,
    )
    db.use {
      it.rawQuery("SELECT type, state, attempts, nextAttemptAtEpochMs FROM queue_events", null).use { c ->
        val out = mutableMapOf<String, Row>()
        while (c.moveToNext()) out[c.getString(0)] = Row(c.getString(1), c.getInt(2), c.getLong(3))
        return out
      }
    }
  }

  /** Makes the stored payload of [type] undecodable by giving it an encoding the codec does not know. */
  private fun corruptPayload(type: String) {
    val db = SQLiteDatabase.openDatabase(
      ctx.getDatabasePath("kiosk_ops_queue.db").path, null, SQLiteDatabase.OPEN_READWRITE,
    )
    db.use { it.execSQL("UPDATE queue_events SET payloadEncoding = 'unknown' WHERE type = ?", arrayOf(type)) }
  }

  private fun ackAll(request: BatchSendRequest) =
    request.events.map { EventAck(it.id, it.idempotencyKey, accepted = true) }

  @Test
  fun `cancellation during send leaves no event in SENDING`() = runBlocking<Unit> {
    val entered = CompletableDeferred<Unit>()
    val transport = object : Transport {
      override suspend fun sendBatch(cfg: KioskOpsConfig, request: BatchSendRequest): TransportResult<BatchSendResponse> {
        entered.complete(Unit)
        CompletableDeferred<Unit>().await()
        error("unreachable")
      }
    }
    val f = fixture(transport)

    var outcome: Result<TransportResult<SyncOnceResult>>? = null
    val job = launch(Dispatchers.Default, start = CoroutineStart.DEFAULT) {
      val r = runCatching { f.engine.flushOnce() }
      outcome = r
      r.getOrThrow()
    }
    withTimeout(10_000) { entered.await() }
    job.cancel()
    job.join()

    assertThat(outcome!!.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
    val rows = rows()
    assertThat(rows.map { it.state }).doesNotContain("SENDING")
    assertThat(rows.map { it.attempts }).containsExactly(0, 0)
  }

  @Test
  fun `checkpoint failure does not strand events`() = runBlocking<Unit> {
    val software = SoftwareAttestationProvider()
    var signCalls = 0
    val throwing = object : DeviceAttestationProvider by software {
      override fun signAuditEntry(payload: String): String? {
        signCalls++
        error("keystore unavailable")
      }
    }
    val transport = object : Transport {
      override suspend fun sendBatch(cfg: KioskOpsConfig, request: BatchSendRequest) =
        TransportResult.Success(BatchSendResponse(request.events.size, ackAll(request)), httpStatus = 200)
    }
    val f = fixture(transport, cfg(checkpoints = true), throwing)

    runCatching { f.engine.flushOnce() }

    assertThat(signCalls).isAtLeast(1)
    assertThat(rows().map { it.state }).doesNotContain("SENDING")
  }

  @Test
  fun `unexpected transport exception is a transient batch failure`() = runBlocking<Unit> {
    val transport = object : Transport {
      override suspend fun sendBatch(cfg: KioskOpsConfig, request: BatchSendRequest): TransportResult<BatchSendResponse> =
        error("boom")
    }
    val f = fixture(transport)

    val result = runCatching { f.engine.flushOnce() }

    assertThat(result.exceptionOrNull()).isNull()
    assertThat(result.getOrNull()).isInstanceOf(TransportResult.TransientFailure::class.java)
    val rows = rows()
    assertThat(rows.map { it.state }).doesNotContain("SENDING")
    assertThat(rows.map { it.attempts }).containsExactly(0, 0)
    assertThat(rows.map { it.nextAttemptAt }).containsExactly(
      clock.now + Backoff.nextDelayMs(0), clock.now + Backoff.nextDelayMs(0),
    )
  }

  @Test
  fun `duplicate and unknown ack ids are ignored safely`() = runBlocking<Unit> {
    val transport = object : Transport {
      override suspend fun sendBatch(cfg: KioskOpsConfig, request: BatchSendRequest): TransportResult<BatchSendResponse> {
        val first = request.events.first()
        val acks = ackAll(request) +
          EventAck(first.id, first.idempotencyKey, accepted = true) +
          EventAck("unknown-id", "unknown-key", accepted = true)
        return TransportResult.Success(BatchSendResponse(acks.size, acks), httpStatus = 200)
      }
    }
    val f = fixture(transport)

    val result = f.engine.flushOnce()

    val success = result as TransportResult.Success
    assertThat(success.value.attempted).isEqualTo(2)
    assertThat(success.value.sent).isEqualTo(2)
    assertThat(rows().map { it.state }).containsExactly("SENT", "SENT")
  }

  @Test
  fun `second flush after failure respects backoff`() = runBlocking<Unit> {
    var calls = 0
    val transport = object : Transport {
      override suspend fun sendBatch(cfg: KioskOpsConfig, request: BatchSendRequest): TransportResult<BatchSendResponse> {
        calls++
        if (calls == 1) return TransportResult.TransientFailure("down", httpStatus = 503)
        return TransportResult.Success(BatchSendResponse(request.events.size, ackAll(request)), httpStatus = 200)
      }
    }
    val f = fixture(transport)

    assertThat(f.engine.flushOnce()).isInstanceOf(TransportResult.TransientFailure::class.java)

    val early = f.engine.flushOnce() as TransportResult.Success
    assertThat(early.value.attempted).isEqualTo(0)
    assertThat(calls).isEqualTo(1)

    clock.now += Backoff.nextDelayMs(0) + 1
    val retry = f.engine.flushOnce() as TransportResult.Success
    assertThat(retry.value.attempted).isEqualTo(2)
    assertThat(retry.value.sent).isEqualTo(2)
    assertThat(rows().map { it.state }).containsExactly("SENT", "SENT")
  }

  @Test
  fun `checkpoint failure returns a transient failure and backs the batch off`() = runBlocking<Unit> {
    val throwing = object : DeviceAttestationProvider by SoftwareAttestationProvider() {
      override fun signAuditEntry(payload: String): String? = error("keystore unavailable")
    }
    var sends = 0
    val transport = object : Transport {
      override suspend fun sendBatch(cfg: KioskOpsConfig, request: BatchSendRequest): TransportResult<BatchSendResponse> {
        sends++
        return TransportResult.Success(BatchSendResponse(request.events.size, ackAll(request)), httpStatus = 200)
      }
    }
    val f = fixture(transport, cfg(checkpoints = true), throwing)

    val result = f.engine.flushOnce()

    assertThat(result).isInstanceOf(TransportResult.TransientFailure::class.java)
    assertThat((result as TransportResult.TransientFailure).message).isEqualTo("checkpoint_failed")
    assertThat(result.cause).isInstanceOf(IllegalStateException::class.java)
    assertThat(sends).isEqualTo(0)
    val rows = rows()
    assertThat(rows.map { it.state }).containsExactly("FAILED", "FAILED")
    assertThat(rows.map { it.attempts }).containsExactly(0, 0)
    assertThat(rows.map { it.nextAttemptAt }).containsExactly(
      clock.now + Backoff.nextDelayMs(0), clock.now + Backoff.nextDelayMs(0),
    )
    assertThat(rows.all { it.nextAttemptAt > clock.now }).isTrue()
  }

  @Test
  fun `acknowledge failure does not strand the batch`() = runBlocking<Unit> {
    // Any write that advances lastAckedSeq fails, so acknowledgeCheckpoint throws after the server accepted the batch.
    val sql = auditDb.openHelper.writableDatabase
    sql.execSQL(
      "CREATE TRIGGER fail_ack_insert BEFORE INSERT ON audit_chain_state WHEN NEW.lastAckedSeq > 0 " +
        "BEGIN SELECT RAISE(ABORT, 'ack write failed'); END"
    )
    sql.execSQL(
      "CREATE TRIGGER fail_ack_update BEFORE UPDATE ON audit_chain_state WHEN NEW.lastAckedSeq > 0 " +
        "BEGIN SELECT RAISE(ABORT, 'ack write failed'); END"
    )
    var checkpointSent = false
    val transport = object : Transport {
      override suspend fun sendBatch(cfg: KioskOpsConfig, request: BatchSendRequest): TransportResult<BatchSendResponse> {
        checkpointSent = request.auditCheckpoint != null
        return TransportResult.Success(BatchSendResponse(request.events.size, ackAll(request)), httpStatus = 200)
      }
    }
    val f = fixture(transport, cfg(checkpoints = true))

    val result = f.engine.flushOnce() as TransportResult.Success

    assertThat(checkpointSent).isTrue()
    assertThat(result.value.sent).isEqualTo(2)
    assertThat(rows().map { it.state }).containsExactly("SENT", "SENT")
    sql.query("SELECT lastAckedSeq FROM audit_chain_state").use { c ->
      assertThat(c.moveToFirst()).isTrue()
      assertThat(c.getLong(0)).isEqualTo(0L)
    }
  }

  private data class OutcomeCase(
    val name: String,
    val respond: (BatchSendRequest) -> TransportResult<BatchSendResponse>,
    val rows: Map<String, Row>,
    val deliveries: List<Triple<String, DeliveryStatus, String?>>,
  )

  @Test
  fun `transport result and ack combinations map to stored rows and deliveries`() = runBlocking<Unit> {
    val now = clock.now
    val retryAt = now + Backoff.nextDelayMs(0)
    val mixedAcks = { request: BatchSendRequest ->
      val byType = request.events.associateBy { it.type }
      val acks = listOf(
        byType.getValue("T0").let { EventAck(it.id, it.idempotencyKey, accepted = true, serverEventId = "srv-0") },
        byType.getValue("T1").let { EventAck(it.id, it.idempotencyKey, accepted = false, retryable = true, error = "busy") },
        byType.getValue("T2").let { EventAck(it.id, it.idempotencyKey, accepted = false, retryable = false, error = "bad") },
      )
      TransportResult.Success(BatchSendResponse(acks.size, acks), httpStatus = 200)
    }
    val backedOff = (0..3).associate { "T$it" to Row("FAILED", 0, retryAt) }
    val cases = listOf(
      OutcomeCase(
        name = "success with accepted, rejected retryable, rejected non-retryable, and missing ack",
        respond = mixedAcks,
        rows = mapOf(
          "T0" to Row("SENT", 0, 0L),
          "T1" to Row("FAILED", 1, retryAt),
          "T2" to Row("QUARANTINED", 1, Long.MAX_VALUE),
          "T3" to Row("FAILED", 1, retryAt),
        ),
        deliveries = listOf(
          Triple("T0", DeliveryStatus.DELIVERED, "srv-0"),
          Triple("T1", DeliveryStatus.RETRY_SCHEDULED, "busy"),
          Triple("T2", DeliveryStatus.QUARANTINED, "bad"),
          Triple("T3", DeliveryStatus.RETRY_SCHEDULED, "missing_ack"),
        ),
      ),
      OutcomeCase("transient failure", { TransportResult.TransientFailure("down", httpStatus = 503) }, backedOff, emptyList()),
      OutcomeCase("permanent failure", { TransportResult.PermanentFailure("auth", httpStatus = 401) }, backedOff, emptyList()),
    )

    for (case in cases) {
      val sentOrder = mutableListOf<String>()
      val sentIds = mutableListOf<String>()
      val delivered = mutableListOf<EventDelivery>()
      val transport = object : Transport {
        override suspend fun sendBatch(cfg: KioskOpsConfig, request: BatchSendRequest): TransportResult<BatchSendResponse> {
          sentOrder += request.events.map { it.type }
          sentIds += request.events.map { it.id }
          return case.respond(request)
        }
      }
      val f = fixture(transport, events = 4, onDelivery = { delivered += it })

      f.engine.flushOnce()

      val rows = rowsByType()
      val nextAt = { r: Row -> if (r.state == "SENT") 0L else r.nextAttemptAt }
      assertWithMessage(case.name).that(rows.mapValues { (_, r) -> r.copy(nextAttemptAt = nextAt(r)) }).isEqualTo(case.rows)
      assertWithMessage(case.name).that(delivered.map { it.type }).isEqualTo(
        sentOrder.filter { t -> case.deliveries.any { it.first == t } },
      )
      assertWithMessage(case.name).that(
        delivered.map { Triple(it.type, it.status, if (it.status == DeliveryStatus.DELIVERED) it.serverEventId else it.error) },
      ).containsExactlyElementsIn(case.deliveries)
      sentIds.forEach { f.queue.delete(it) }
    }
  }

  @Test
  fun `undecodable event is quarantined or backed off without stalling the batch`() = runBlocking<Unit> {
    val sentTypes = mutableListOf<List<String>>()
    val transport = object : Transport {
      override suspend fun sendBatch(cfg: KioskOpsConfig, request: BatchSendRequest): TransportResult<BatchSendResponse> {
        sentTypes += request.events.map { it.type }
        return TransportResult.Success(BatchSendResponse(request.events.size, ackAll(request)), httpStatus = 200)
      }
    }
    val delivered = mutableListOf<EventDelivery>()
    val f = fixture(transport, cfg(maxAttempts = 2), events = 3, onDelivery = { delivered += it })
    corruptPayload("T1")

    val first = f.engine.flushOnce() as TransportResult.Success

    assertThat(first.value).isEqualTo(
      SyncOnceResult(attempted = 3, sent = 2, permanentFailed = 0, transientFailed = 1, rejected = 1),
    )
    assertThat(sentTypes).containsExactly(listOf("T0", "T2"))
    val retryAt = clock.now + Backoff.nextDelayMs(0)
    assertThat(rowsByType()).isEqualTo(
      mapOf(
        "T0" to rowsByType().getValue("T0").copy(state = "SENT", attempts = 0),
        "T1" to Row("FAILED", 1, retryAt),
        "T2" to rowsByType().getValue("T2").copy(state = "SENT", attempts = 0),
      ),
    )
    assertThat(delivered.map { Triple(it.type, it.status, it.error) }).containsExactly(
      Triple("T0", DeliveryStatus.DELIVERED, null),
      Triple("T1", DeliveryStatus.RETRY_SCHEDULED, "payload_decode_failed"),
      Triple("T2", DeliveryStatus.DELIVERED, null),
    ).inOrder()

    val early = f.engine.flushOnce() as TransportResult.Success
    assertThat(early.value.attempted).isEqualTo(0)

    clock.now = retryAt
    delivered.clear()
    val capped = f.engine.flushOnce() as TransportResult.Success

    assertThat(capped.value).isEqualTo(
      SyncOnceResult(attempted = 1, sent = 0, permanentFailed = 1, transientFailed = 0, rejected = 1),
    )
    assertThat(sentTypes).hasSize(1)
    assertThat(rowsByType().getValue("T1")).isEqualTo(Row("QUARANTINED", 2, Long.MAX_VALUE))
    assertThat(delivered.map { Triple(it.type, it.status, it.error) }).containsExactly(
      Triple("T1", DeliveryStatus.QUARANTINED, "payload_decode_failed"),
    )
  }

  @Test
  fun `undecodable head event does not stall the queue`() = runBlocking<Unit> {
    val sentTypes = mutableListOf<String>()
    val transport = object : Transport {
      override suspend fun sendBatch(cfg: KioskOpsConfig, request: BatchSendRequest): TransportResult<BatchSendResponse> {
        sentTypes += request.events.map { it.type }
        return TransportResult.Success(BatchSendResponse(request.events.size, ackAll(request)), httpStatus = 200)
      }
    }
    val f = fixture(transport, cfg(batchSize = 1), events = 2)
    corruptPayload("T0")

    f.engine.flushOnce()
    val second = f.engine.flushOnce() as TransportResult.Success

    assertThat(second.value.sent).isEqualTo(1)
    assertThat(sentTypes).containsExactly("T1")
    assertThat(rowsByType().mapValues { it.value.state }).isEqualTo(mapOf("T0" to "FAILED", "T1" to "SENT"))
  }
}
