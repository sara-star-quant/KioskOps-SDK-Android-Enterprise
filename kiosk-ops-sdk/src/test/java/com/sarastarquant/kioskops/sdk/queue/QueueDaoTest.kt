/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.queue

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueueDaoTest {

  private lateinit var db: QueueDatabase
  private lateinit var dao: QueueDao

  private val now = 1_700_000_000_000L

  @Before
  fun setUp() {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(ctx, QueueDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    dao = db.queueDao()
  }

  @After
  fun tearDown() {
    db.close()
  }

  private fun entity(
    id: String = "evt-1",
    idempotencyKey: String = "idem-$id",
    type: String = "test_event",
    state: String = QueueStates.PENDING,
    createdAt: Long = now,
    nextAttemptAt: Long = 0L,
    permanentFailure: Int = 0,
    userId: String? = null,
    anomalyScore: Float? = null,
  ) = QueueEventEntity(
    id = id,
    idempotencyKey = idempotencyKey,
    type = type,
    payloadBlob = """{"key":"value"}""".toByteArray(),
    payloadEncoding = "json",
    payloadBytes = 15,
    createdAtEpochMs = createdAt,
    state = state,
    attempts = 0,
    nextAttemptAtEpochMs = nextAttemptAt,
    permanentFailure = permanentFailure,
    updatedAtEpochMs = createdAt,
    userId = userId,
    anomalyScore = anomalyScore,
  )

  @Test
  fun `insert and retrieve via loadNextBatch`() = runTest {
    dao.insert(entity())

    val batch = dao.loadNextBatch(now + 1000, 10)
    assertThat(batch).hasSize(1)
    assertThat(batch[0].id).isEqualTo("evt-1")
    assertThat(batch[0].type).isEqualTo("test_event")
  }

  @Test
  fun `loadNextBatch respects backoff gate`() = runTest {
    dao.insert(entity(id = "e1", nextAttemptAt = now + 5000))
    dao.insert(entity(id = "e2", idempotencyKey = "idem-e2", nextAttemptAt = 0))

    val batch = dao.loadNextBatch(now, 10)
    assertThat(batch).hasSize(1)
    assertThat(batch[0].id).isEqualTo("e2")
  }

  @Test
  fun `loadNextBatch excludes permanent failures`() = runTest {
    dao.insert(entity(id = "e1", permanentFailure = 1))
    dao.insert(entity(id = "e2", idempotencyKey = "idem-e2"))

    val batch = dao.loadNextBatch(now + 1000, 10)
    assertThat(batch).hasSize(1)
    assertThat(batch[0].id).isEqualTo("e2")
  }

  @Test
  fun `markSending transitions state`() = runTest {
    dao.insert(entity())
    dao.markSending("evt-1", now + 100)

    val batch = dao.loadNextBatch(now + 200, 10)
    assertThat(batch).isEmpty() // SENDING is not eligible
  }

  @Test
  fun `markSent transitions state`() = runTest {
    dao.insert(entity())
    dao.markSent("evt-1", now + 100)

    val notSent = dao.countNotSent()
    assertThat(notSent).isEqualTo(0)
  }

  @Test
  fun `markFailed with permanent sets quarantine`() = runTest {
    dao.insert(entity())
    dao.markFailed("evt-1", "schema_error", "Invalid schema", 0, 1, now + 100)

    val quarantined = dao.loadQuarantinedSummaries(10)
    assertThat(quarantined).hasSize(1)
    assertThat(quarantined[0].quarantineReason).isEqualTo("Invalid schema")
  }

  @Test
  fun `markFailed with transient sets FAILED state`() = runTest {
    dao.insert(entity())
    dao.markFailed("evt-1", "timeout", null, now + 30_000, 0, now + 100)

    // Should appear in next batch after backoff
    val batchBeforeBackoff = dao.loadNextBatch(now + 200, 10)
    assertThat(batchBeforeBackoff).isEmpty()

    val batchAfterBackoff = dao.loadNextBatch(now + 30_001, 10)
    assertThat(batchAfterBackoff).hasSize(1)
  }

  @Test
  fun `deleteOldestEligible removes oldest first`() = runTest {
    for (i in 1..5) {
      dao.insert(entity(id = "e$i", idempotencyKey = "k$i", createdAt = now + i * 100))
    }

    val deleted = dao.deleteOldestEligible(2)
    assertThat(deleted).isEqualTo(2)

    val remaining = dao.loadNextBatch(now + 10_000, 10)
    assertThat(remaining).hasSize(3)
    assertThat(remaining[0].id).isEqualTo("e3")
  }

  @Test
  fun `deleteSentOlderThan purges old sent events`() = runTest {
    dao.insert(entity(id = "e1", idempotencyKey = "k1", createdAt = now - 100_000))
    dao.insert(entity(id = "e2", idempotencyKey = "k2", createdAt = now))
    dao.markSent("e1", now - 100_000)
    dao.markSent("e2", now)

    val deleted = dao.deleteSentOlderThan(now - 50_000)
    assertThat(deleted).isEqualTo(1)
  }

  @Test
  fun `deleteByUserId removes only matching user`() = runTest {
    dao.insert(entity(id = "e1", idempotencyKey = "k1", userId = "user-A"))
    dao.insert(entity(id = "e2", idempotencyKey = "k2", userId = "user-B"))
    dao.insert(entity(id = "e3", idempotencyKey = "k3", userId = "user-A"))

    val deleted = dao.deleteByUserId("user-A")
    assertThat(deleted).isEqualTo(2)

    val remaining = dao.getByUserId("user-B")
    assertThat(remaining).hasSize(1)
  }

  @Test
  fun `getAnomalous filters by threshold`() = runTest {
    dao.insert(entity(id = "e1", idempotencyKey = "k1", anomalyScore = 0.9f))
    dao.insert(entity(id = "e2", idempotencyKey = "k2", anomalyScore = 0.3f))
    dao.insert(entity(id = "e3", idempotencyKey = "k3", anomalyScore = null))

    val anomalous = dao.getAnomalous(0.5f)
    assertThat(anomalous).hasSize(1)
    assertThat(anomalous[0].id).isEqualTo("e1")
  }

  @Test
  fun `countNotSent includes PENDING and FAILED`() = runTest {
    dao.insert(entity(id = "e1", idempotencyKey = "k1"))
    dao.insert(entity(id = "e2", idempotencyKey = "k2"))
    dao.markSent("e1", now)

    val count = dao.countNotSent()
    assertThat(count).isEqualTo(1)
  }

  @Test
  fun `quarantined summaries exclude payload blob`() = runTest {
    dao.insert(entity(id = "e1"))
    dao.markFailed("e1", "bad_schema", "Schema validation failed", 0, 1, now)

    val summaries = dao.loadQuarantinedSummaries(10)
    assertThat(summaries).hasSize(1)
    assertThat(summaries[0].id).isEqualTo("e1")
    assertThat(summaries[0].lastError).isEqualTo("bad_schema")
  }

  @Test(expected = SQLiteConstraintException::class)
  fun `duplicate idempotency key throws constraint exception`() = runTest {
    dao.insert(entity(id = "e1", idempotencyKey = "shared-key"))
    dao.insert(entity(id = "e2", idempotencyKey = "shared-key"))
  }

  @Test
  fun `reconcileStaleSending resets only old SENDING rows`() = runTest {
    // Simulate crash: three events marked SENDING at different times.
    dao.insert(entity(id = "old1", idempotencyKey = "k-old1"))
    dao.insert(entity(id = "old2", idempotencyKey = "k-old2"))
    dao.insert(entity(id = "fresh", idempotencyKey = "k-fresh"))
    dao.markSending("old1", now - 10 * 60 * 1000) // 10 min old
    dao.markSending("old2", now - 6 * 60 * 1000) // 6 min old
    dao.markSending("fresh", now - 30 * 1000) // 30s old

    val threshold = now - 5 * 60 * 1000 // 5 minute window
    val reset = dao.reconcileStaleSending(staleBeforeEpochMs = threshold, nowMs = now)
    assertThat(reset).isEqualTo(2)

    // Only the fresh row stays SENDING (not eligible); the two old rows are now PENDING.
    val eligible = dao.loadNextBatch(now + 1000, 10).map { it.id }.toSet()
    assertThat(eligible).containsExactly("old1", "old2")
  }

  @Test
  fun `reconcileStaleSending leaves PENDING and FAILED untouched`() = runTest {
    dao.insert(entity(id = "p1", idempotencyKey = "kp1"))
    dao.insert(entity(id = "f1", idempotencyKey = "kf1"))
    dao.markFailed("f1", "x", null, now + 1000, 0, now - 10 * 60 * 1000)

    val reset = dao.reconcileStaleSending(staleBeforeEpochMs = now, nowMs = now)
    assertThat(reset).isEqualTo(0)
  }

  @Test
  fun `repository caps lastError length on markFailed`() = runTest {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.sarastarquant.kioskops.sdk.queue.QueueRepository(
      ctx,
      com.sarastarquant.kioskops.sdk.logging.RingLog(ctx),
      com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider,
    )
    ctx.deleteDatabase("kiosk_ops_queue.db")
    val cfg = com.sarastarquant.kioskops.sdk.KioskOpsConfig(
      baseUrl = "https://example.invalid/",
      locationId = "LOC-1",
      kioskEnabled = false,
      securityPolicy = com.sarastarquant.kioskops.sdk.compliance.SecurityPolicy.maximalistDefaults()
        .copy(encryptQueuePayloads = false),
    )
    val res = repo.enqueue("T", "{\"x\":1}", cfg)
    val id = (res as com.sarastarquant.kioskops.sdk.queue.EnqueueResult.Accepted).id

    val huge = "x".repeat(5000)
    repo.markFailed(id, huge, nextAttemptAtMs = 0L, permanentFailure = 0)

    val stored = repo.nextBatch(nowMs = 1L, limit = 1).first()
    assertThat(stored.lastError).isNotNull()
    assertThat(stored.lastError!!.length).isAtMost(256)
    assertThat(stored.lastError).endsWith("...[truncated]")
  }

  @Test
  fun `markBatchFailureNoAttemptBump preserves attempts counter`() = runTest {
    dao.insert(entity())
    // Bump attempts once via a real per-event failure so we can observe the counter later.
    dao.markFailed("evt-1", "per_event_err", null, now + 1000, 0, now + 100)

    val afterFirst = dao.loadNextBatch(now + 2000, 10).first()
    assertThat(afterFirst.attempts).isEqualTo(1)

    // Batch transient: 5 times, attempts must stay at 1.
    repeat(5) {
      dao.markBatchFailureNoAttemptBump("evt-1", "network_5xx", now + 1000, now + 200)
    }

    val afterBatch = dao.loadNextBatch(now + 2000, 10).first()
    assertThat(afterBatch.attempts).isEqualTo(1)
    assertThat(afterBatch.lastError).isEqualTo("network_5xx")
    assertThat(afterBatch.state).isEqualTo(QueueStates.FAILED)
  }
}
