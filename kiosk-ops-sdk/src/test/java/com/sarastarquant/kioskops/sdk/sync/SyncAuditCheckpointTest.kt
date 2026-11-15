/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.audit.PersistentAuditTrail
import com.sarastarquant.kioskops.sdk.audit.SoftwareAttestationProvider
import com.sarastarquant.kioskops.sdk.audit.db.AuditDatabase
import com.sarastarquant.kioskops.sdk.compliance.SecurityPolicy
import com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider
import com.sarastarquant.kioskops.sdk.logging.RingLog
import com.sarastarquant.kioskops.sdk.queue.QueueRepository
import com.sarastarquant.kioskops.sdk.telemetry.TelemetrySink
import com.sarastarquant.kioskops.sdk.transport.BatchSendRequest
import com.sarastarquant.kioskops.sdk.transport.BatchSendResponse
import com.sarastarquant.kioskops.sdk.transport.EventAck
import com.sarastarquant.kioskops.sdk.transport.Transport
import com.sarastarquant.kioskops.sdk.transport.TransportEvent
import com.sarastarquant.kioskops.sdk.transport.TransportResult
import com.sarastarquant.kioskops.sdk.util.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncAuditCheckpointTest {

  private lateinit var ctx: Context
  private lateinit var db: AuditDatabase

  @Before
  fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(ctx, AuditDatabase::class.java).allowMainThreadQueries().build()
    AuditDatabase.setInstance(db)
  }

  @After
  fun tearDown() {
    db.close()
    AuditDatabase.setInstance(null)
  }

  private class CapturingTransport : Transport {
    val requests = mutableListOf<BatchSendRequest>()
    override suspend fun sendBatch(cfg: KioskOpsConfig, request: BatchSendRequest): TransportResult<BatchSendResponse> {
      requests += request
      val acks = request.events.map { EventAck(it.id, it.idempotencyKey, accepted = true) }
      return TransportResult.Success(BatchSendResponse(acks.size, acks), httpStatus = 200)
    }
  }

  private suspend fun flushWith(checkpoints: Boolean): BatchSendRequest {
    val cfg = KioskOpsConfig(
      baseUrl = "https://sync.test/",
      locationId = "TEST",
      kioskEnabled = false,
      syncPolicy = SyncPolicy(enabled = true, endpointPath = "events/batch", batchSize = 50),
      securityPolicy = SecurityPolicy.maximalistDefaults()
        .copy(encryptQueuePayloads = false, signedAuditCheckpoints = checkpoints),
    )
    val logs = RingLog(ctx)
    val queue = QueueRepository(ctx, logs, NoopCryptoProvider)
    val provider = SoftwareAttestationProvider()
    val audit = PersistentAuditTrail(ctx, { cfg.retentionPolicy }, Clock.SYSTEM, attestationProvider = { provider })
    val transport = CapturingTransport()
    assertThat(queue.enqueue("T1", "{\"x\":1}", cfg).isAccepted).isTrue()

    SyncEngine(
      context = ctx,
      cfgProvider = { cfg },
      queue = queue,
      transport = transport,
      logs = logs,
      telemetry = object : TelemetrySink { override fun emit(event: String, fields: Map<String, String>) = Unit },
      audit = audit,
      clock = Clock.SYSTEM,
    ).flushOnce()

    val request = transport.requests.single()
    request.auditCheckpoint?.let { cp ->
      assertThat(provider.verifySignature(cp.signingPayload(), cp.signature!!)).isTrue()
    }
    return request
  }

  @Test
  fun `sync batch carries checkpoint only when policy enabled`() = runTest {
    assertThat(flushWith(checkpoints = false).auditCheckpoint).isNull()

    val checkpoint = flushWith(checkpoints = true).auditCheckpoint
    assertThat(checkpoint).isNotNull()
    // sync_batch_start is recorded before the request is built, so the head covers it.
    assertThat(checkpoint!!.headSeq).isAtLeast(1L)
  }

  @Test
  fun `request body without checkpoint is unchanged`() {
    // Same Json settings the SDK hands to OkHttpTransport.
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    val request = BatchSendRequest(
      batchId = "b1",
      deviceId = "d1",
      appVersion = "1.0",
      locationId = "L1",
      sentAtEpochMs = 42,
      events = listOf(TransportEvent("e1", "k1", "T", "{\"x\":1}", 7)),
    )
    assertThat(json.encodeToString(request)).isEqualTo(
      "{\"batchId\":\"b1\",\"deviceId\":\"d1\",\"appVersion\":\"1.0\",\"locationId\":\"L1\",\"sentAtEpochMs\":42," +
        "\"events\":[{\"id\":\"e1\",\"idempotencyKey\":\"k1\",\"type\":\"T\",\"payloadJson\":\"{\\\"x\\\":1}\"," +
        "\"createdAtEpochMs\":7}]}"
    )
  }
}
