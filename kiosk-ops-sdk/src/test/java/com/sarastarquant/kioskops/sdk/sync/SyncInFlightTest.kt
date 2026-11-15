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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Ignore
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

  private fun cfg(checkpoints: Boolean = false) = KioskOpsConfig(
    baseUrl = "https://sync.test/",
    locationId = "TEST",
    kioskEnabled = false,
    syncPolicy = SyncPolicy(enabled = true, endpointPath = "events/batch", batchSize = 50),
    securityPolicy = SecurityPolicy.maximalistDefaults()
      .copy(encryptQueuePayloads = false, signedAuditCheckpoints = checkpoints),
  )

  private class Fixture(val queue: QueueRepository, val engine: SyncEngine)

  private suspend fun fixture(
    transport: Transport,
    cfg: KioskOpsConfig = cfg(),
    provider: DeviceAttestationProvider = SoftwareAttestationProvider(),
    events: Int = 2,
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

  private fun ackAll(request: BatchSendRequest) =
    request.events.map { EventAck(it.id, it.idempotencyKey, accepted = true) }

  @Ignore("pending: a cancelled send must return its events to retry")
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

    val job = launch(Dispatchers.Default, start = CoroutineStart.DEFAULT) { f.engine.flushOnce() }
    withTimeout(10_000) { entered.await() }
    job.cancel()
    job.join()

    assertThat(job.isCancelled).isTrue()
    val rows = rows()
    assertThat(rows.map { it.state }).doesNotContain("SENDING")
    assertThat(rows.map { it.attempts }).containsExactly(0, 0)
  }

  @Ignore("pending: a failed checkpoint signature must not strand events in sending")
  @Test
  fun `checkpoint failure does not strand events`() = runBlocking<Unit> {
    val software = SoftwareAttestationProvider()
    val throwing = object : DeviceAttestationProvider by software {
      override fun signAuditEntry(payload: String): String? = error("keystore unavailable")
    }
    val transport = object : Transport {
      override suspend fun sendBatch(cfg: KioskOpsConfig, request: BatchSendRequest) =
        TransportResult.Success(BatchSendResponse(request.events.size, ackAll(request)), httpStatus = 200)
    }
    val f = fixture(transport, cfg(checkpoints = true), throwing)

    runCatching { f.engine.flushOnce() }

    assertThat(rows().map { it.state }).doesNotContain("SENDING")
  }

  @Ignore("pending: an unexpected transport exception must become a transient batch failure")
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
}
