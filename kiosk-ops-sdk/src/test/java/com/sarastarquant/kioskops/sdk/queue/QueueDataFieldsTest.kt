/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.queue

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueueDataFieldsTest : QueueTestBase() {

  // ---------------------------------------------------------------------------
  // State Tracking
  // ---------------------------------------------------------------------------

  @Test
  fun `freshly enqueued event has PENDING state`() = runTest {
    val repo = newRepo()

    val result = repo.enqueue("T", "{\"x\":1}", baseCfg)
    val id = (result as EnqueueResult.Accepted).id

    val batch = repo.nextBatch(System.currentTimeMillis() + 1000)
    val event = batch.first { it.id == id }
    assertThat(event.state).isEqualTo(QueueStates.PENDING)
  }

  @Test
  fun `countActive reflects number of enqueued events`() = runTest {
    val repo = newRepo()

    assertThat(repo.countActive()).isEqualTo(0)

    repo.enqueue("T", "{\"x\":1}", baseCfg)
    assertThat(repo.countActive()).isEqualTo(1)

    repo.enqueue("T", "{\"x\":2}", baseCfg)
    assertThat(repo.countActive()).isEqualTo(2)
  }

  @Test
  fun `countActive excludes sent events`() = runTest {
    val repo = newRepo()

    val r = repo.enqueue("T", "{\"x\":1}", baseCfg) as EnqueueResult.Accepted
    assertThat(repo.countActive()).isEqualTo(1)

    repo.markSent(r.id)
    assertThat(repo.countActive()).isEqualTo(0)
  }

  @Test
  fun `quarantinedSummaries returns empty for fresh queue`() = runTest {
    val repo = newRepo()

    val summaries = repo.quarantinedSummaries()
    assertThat(summaries).isEmpty()
  }

  @Test
  fun `quarantinedSummaries returns entries after permanent failure`() = runTest {
    val repo = newRepo()

    val r = repo.enqueue("T", "{\"x\":1}", baseCfg) as EnqueueResult.Accepted
    repo.markFailed(r.id, "schema_error", nextAttemptAtMs = 0, permanentFailure = 1, quarantineReason = "bad schema")

    val summaries = repo.quarantinedSummaries()
    assertThat(summaries).hasSize(1)
    assertThat(summaries[0].id).isEqualTo(r.id)
    assertThat(summaries[0].reason).isEqualTo("bad schema")
  }

  // ---------------------------------------------------------------------------
  // Data Fields
  // ---------------------------------------------------------------------------

  @Test
  fun `userId is preserved through enqueue`() = runTest {
    val repo = newRepo()

    val result = repo.enqueue("T", "{\"x\":1}", baseCfg, userId = "user-42")
    val id = (result as EnqueueResult.Accepted).id

    val batch = repo.nextBatch(System.currentTimeMillis() + 1000)
    val event = batch.first { it.id == id }
    assertThat(event.userId).isEqualTo("user-42")
  }

  @Test
  fun `dataClassification is preserved through enqueue`() = runTest {
    val repo = newRepo()

    val result = repo.enqueue("T", "{\"x\":1}", baseCfg, dataClassification = "CONFIDENTIAL")
    val id = (result as EnqueueResult.Accepted).id

    val batch = repo.nextBatch(System.currentTimeMillis() + 1000)
    val event = batch.first { it.id == id }
    assertThat(event.dataClassification).isEqualTo("CONFIDENTIAL")
  }

  @Test
  fun `anomalyScore is preserved through enqueue`() = runTest {
    val repo = newRepo()

    val result = repo.enqueue("T", "{\"x\":1}", baseCfg, anomalyScore = 0.85f)
    val id = (result as EnqueueResult.Accepted).id

    val batch = repo.nextBatch(System.currentTimeMillis() + 1000)
    val event = batch.first { it.id == id }
    assertThat(event.anomalyScore).isWithin(0.001f).of(0.85f)
  }

  @Test
  fun `null userId is stored as null`() = runTest {
    val repo = newRepo()

    val result = repo.enqueue("T", "{\"x\":1}", baseCfg)
    val id = (result as EnqueueResult.Accepted).id

    val batch = repo.nextBatch(System.currentTimeMillis() + 1000)
    val event = batch.first { it.id == id }
    assertThat(event.userId).isNull()
  }

  @Test
  fun `null dataClassification is stored as null`() = runTest {
    val repo = newRepo()

    val result = repo.enqueue("T", "{\"x\":1}", baseCfg)
    val id = (result as EnqueueResult.Accepted).id

    val batch = repo.nextBatch(System.currentTimeMillis() + 1000)
    val event = batch.first { it.id == id }
    assertThat(event.dataClassification).isNull()
  }

  @Test
  fun `null anomalyScore is stored as null`() = runTest {
    val repo = newRepo()

    val result = repo.enqueue("T", "{\"x\":1}", baseCfg)
    val id = (result as EnqueueResult.Accepted).id

    val batch = repo.nextBatch(System.currentTimeMillis() + 1000)
    val event = batch.first { it.id == id }
    assertThat(event.anomalyScore).isNull()
  }

  // ---------------------------------------------------------------------------
  // Delete and lifecycle
  // ---------------------------------------------------------------------------

  @Test
  fun `delete removes event from active count`() = runTest {
    val repo = newRepo()

    val r = repo.enqueue("T", "{\"x\":1}", baseCfg) as EnqueueResult.Accepted
    assertThat(repo.countActive()).isEqualTo(1)

    repo.delete(r.id)
    assertThat(repo.countActive()).isEqualTo(0)
  }

  @Test
  fun `deleteByUserId removes only matching user events`() = runTest {
    val repo = newRepo()

    repo.enqueue("T", "{\"x\":1}", baseCfg, userId = "alice")
    repo.enqueue("T", "{\"x\":2}", baseCfg, userId = "bob")
    repo.enqueue("T", "{\"x\":3}", baseCfg, userId = "alice")

    val deleted = repo.deleteByUserId("alice")
    assertThat(deleted).isEqualTo(2)
    assertThat(repo.countActive()).isEqualTo(1)
  }

  @Test
  fun `getByUserId returns only matching user events`() = runTest {
    val repo = newRepo()

    repo.enqueue("T", "{\"x\":1}", baseCfg, userId = "alice")
    repo.enqueue("T", "{\"x\":2}", baseCfg, userId = "bob")

    val aliceEvents = repo.getByUserId("alice")
    assertThat(aliceEvents).hasSize(1)
    assertThat(aliceEvents[0].userId).isEqualTo("alice")
  }

  @Test
  fun `getAnomalous returns events above threshold`() = runTest {
    val repo = newRepo()

    repo.enqueue("T", "{\"x\":1}", baseCfg, anomalyScore = 0.9f)
    repo.enqueue("T", "{\"x\":2}", baseCfg, anomalyScore = 0.2f)
    repo.enqueue("T", "{\"x\":3}", baseCfg, anomalyScore = null)

    val anomalous = repo.getAnomalous(0.5f)
    assertThat(anomalous).hasSize(1)
    assertThat(anomalous[0].anomalyScore).isWithin(0.001f).of(0.9f)
  }
}
