/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.queue

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
}
