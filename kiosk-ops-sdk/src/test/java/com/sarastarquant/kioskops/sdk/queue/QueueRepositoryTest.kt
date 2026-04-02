/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.queue

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.compliance.IdempotencyConfig
import com.sarastarquant.kioskops.sdk.compliance.OverflowStrategy
import com.sarastarquant.kioskops.sdk.compliance.QueueLimits
import com.sarastarquant.kioskops.sdk.compliance.SecurityPolicy
import com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider
import com.sarastarquant.kioskops.sdk.logging.RingLog
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Comprehensive non-happy-path tests for [QueueRepository].
 *
 * Tests exercise the repository directly (not through KioskOpsSdk) covering
 * size guardrails, queue pressure, idempotency, payload codec round-trips,
 * state tracking, and data field preservation.
 */
@RunWith(RobolectricTestRunner::class)
class QueueRepositoryTest {

  private lateinit var ctx: Context

  /** Default config with encryption disabled for simpler payload assertions. */
  private val baseCfg = KioskOpsConfig(
    baseUrl = "https://example.invalid/",
    locationId = "LOC-1",
    kioskEnabled = false,
    securityPolicy = SecurityPolicy.maximalistDefaults().copy(
      encryptQueuePayloads = false,
      maxEventPayloadBytes = 64 * 1024,
    ),
    queueLimits = QueueLimits.maximalistDefaults(),
    idempotencyConfig = IdempotencyConfig.maximalistDefaults(),
  )

  @Before
  fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()
    // Ensure a clean database for each test.
    ctx.deleteDatabase("kiosk_ops_queue.db")
  }

  private fun newRepo(): QueueRepository =
    QueueRepository(ctx, RingLog(ctx), NoopCryptoProvider)

  // ---------------------------------------------------------------------------
  // Size Guardrails
  // ---------------------------------------------------------------------------

  @Test
  fun `payload exceeding maxEventPayloadBytes is rejected as PayloadTooLarge`() = runTest {
    val tinyCfg = baseCfg.copy(
      securityPolicy = baseCfg.securityPolicy.copy(maxEventPayloadBytes = 10),
    )
    val repo = newRepo()

    val result = repo.enqueue("T", "{\"data\":\"this-exceeds-ten-bytes\"}", tinyCfg)

    assertThat(result.isAccepted).isFalse()
    assertThat(result).isInstanceOf(EnqueueResult.Rejected.PayloadTooLarge::class.java)
    val rejected = result as EnqueueResult.Rejected.PayloadTooLarge
    assertThat(rejected.max).isEqualTo(10)
    assertThat(rejected.bytes).isGreaterThan(10)
  }

  @Test
  fun `empty payload is accepted`() = runTest {
    val repo = newRepo()

    val result = repo.enqueue("T", "", baseCfg)

    assertThat(result.isAccepted).isTrue()
  }

  @Test
  fun `payload at exact byte limit is accepted`() = runTest {
    val limit = 20
    val cfg = baseCfg.copy(
      securityPolicy = baseCfg.securityPolicy.copy(maxEventPayloadBytes = limit),
    )
    // Build a payload whose UTF-8 encoding is exactly `limit` bytes.
    val payload = "a".repeat(limit)
    assertThat(payload.toByteArray(Charsets.UTF_8).size).isEqualTo(limit)

    val repo = newRepo()
    val result = repo.enqueue("T", payload, cfg)

    assertThat(result.isAccepted).isTrue()
  }

  @Test
  fun `payload one byte over limit is rejected`() = runTest {
    val limit = 20
    val cfg = baseCfg.copy(
      securityPolicy = baseCfg.securityPolicy.copy(maxEventPayloadBytes = limit),
    )
    val payload = "a".repeat(limit + 1)

    val repo = newRepo()
    val result = repo.enqueue("T", payload, cfg)

    assertThat(result.isAccepted).isFalse()
    assertThat(result).isInstanceOf(EnqueueResult.Rejected.PayloadTooLarge::class.java)
  }

  @Test
  fun `multibyte UTF-8 payload is measured in bytes not chars`() = runTest {
    // Each char below is 3 UTF-8 bytes. 4 chars = 12 bytes.
    val multibytePayload = "\u4e16\u754c\u4f60\u597d"
    val byteLen = multibytePayload.toByteArray(Charsets.UTF_8).size
    assertThat(byteLen).isEqualTo(12)

    val cfg = baseCfg.copy(
      securityPolicy = baseCfg.securityPolicy.copy(maxEventPayloadBytes = 11),
    )
    val repo = newRepo()
    val result = repo.enqueue("T", multibytePayload, cfg)

    assertThat(result.isAccepted).isFalse()
    assertThat(result).isInstanceOf(EnqueueResult.Rejected.PayloadTooLarge::class.java)
  }

  // ---------------------------------------------------------------------------
  // Queue Pressure -- DROP_OLDEST
  // ---------------------------------------------------------------------------

  private fun dropOldestCfg(maxActive: Int) = baseCfg.copy(
    queueLimits = QueueLimits(
      maxActiveEvents = maxActive,
      maxActiveBytes = 1024 * 1024,
      overflowStrategy = OverflowStrategy.DROP_OLDEST,
    ),
  )

  @Test
  fun `enqueue up to maxActiveEvents succeeds with zero drops`() = runTest {
    val cfg = dropOldestCfg(3)
    val repo = newRepo()

    for (i in 1..3) {
      val r = repo.enqueue("T", "{\"i\":$i}", cfg)
      assertThat(r.isAccepted).isTrue()
      val accepted = r as EnqueueResult.Accepted
      assertThat(accepted.droppedOldest).isEqualTo(0)
    }
    assertThat(repo.countActive()).isEqualTo(3)
  }

  @Test
  fun `DROP_OLDEST removes oldest and reports droppedOldest on overflow`() = runTest {
    val cfg = dropOldestCfg(3)
    val repo = newRepo()

    repo.enqueue("T", "{\"i\":1}", cfg)
    repo.enqueue("T", "{\"i\":2}", cfg)
    repo.enqueue("T", "{\"i\":3}", cfg)

    val fourth = repo.enqueue("T", "{\"i\":4}", cfg)
    assertThat(fourth.isAccepted).isTrue()
    val accepted = fourth as EnqueueResult.Accepted
    assertThat(accepted.droppedOldest).isAtLeast(1)
    assertThat(repo.countActive()).isEqualTo(3)
  }

  @Test
  fun `DROP_OLDEST maintains count ceiling across multiple overflows`() = runTest {
    val cfg = dropOldestCfg(2)
    val repo = newRepo()

    for (i in 1..10) {
      val r = repo.enqueue("T", "{\"i\":$i}", cfg)
      assertThat(r.isAccepted).isTrue()
    }
    assertThat(repo.countActive()).isEqualTo(2)
  }

  // ---------------------------------------------------------------------------
  // Queue Pressure -- DROP_NEWEST
  // ---------------------------------------------------------------------------

  private fun dropNewestCfg(maxActive: Int) = baseCfg.copy(
    queueLimits = QueueLimits(
      maxActiveEvents = maxActive,
      maxActiveBytes = 1024 * 1024,
      overflowStrategy = OverflowStrategy.DROP_NEWEST,
    ),
  )

  @Test
  fun `DROP_NEWEST rejects new event when at capacity`() = runTest {
    val cfg = dropNewestCfg(2)
    val repo = newRepo()

    repo.enqueue("T", "{\"i\":1}", cfg)
    repo.enqueue("T", "{\"i\":2}", cfg)

    val third = repo.enqueue("T", "{\"i\":3}", cfg)
    assertThat(third.isAccepted).isFalse()
    assertThat(third).isInstanceOf(EnqueueResult.Rejected.QueueFull::class.java)
    assertThat(repo.countActive()).isEqualTo(2)
  }

  @Test
  fun `DROP_NEWEST preserves existing events unchanged`() = runTest {
    val cfg = dropNewestCfg(2)
    val repo = newRepo()

    val first = repo.enqueue("T", "{\"i\":1}", cfg) as EnqueueResult.Accepted
    val second = repo.enqueue("T", "{\"i\":2}", cfg) as EnqueueResult.Accepted

    // Overflow attempt
    repo.enqueue("T", "{\"i\":3}", cfg)

    // Original events still retrievable
    val batch = repo.nextBatch(System.currentTimeMillis() + 1000)
    val ids = batch.map { it.id }.toSet()
    assertThat(ids).contains(first.id)
    assertThat(ids).contains(second.id)
  }

  // ---------------------------------------------------------------------------
  // Queue Pressure -- BLOCK
  // ---------------------------------------------------------------------------

  @Test
  fun `BLOCK strategy rejects new event when at capacity`() = runTest {
    val cfg = baseCfg.copy(
      queueLimits = QueueLimits(
        maxActiveEvents = 1,
        maxActiveBytes = 1024 * 1024,
        overflowStrategy = OverflowStrategy.BLOCK,
      ),
    )
    val repo = newRepo()

    repo.enqueue("T", "{\"i\":1}", cfg)
    val second = repo.enqueue("T", "{\"i\":2}", cfg)

    assertThat(second.isAccepted).isFalse()
    assertThat(second).isInstanceOf(EnqueueResult.Rejected.QueueFull::class.java)
  }

  // ---------------------------------------------------------------------------
  // Queue Pressure -- byte-based limits
  // ---------------------------------------------------------------------------

  @Test
  fun `maxActiveBytes triggers overflow even when event count is under limit`() = runTest {
    // First payload: {"i":1} = 7 bytes. Set limit to 10 so the first fits
    // but a second identical payload (7 more bytes, total 14) exceeds the cap.
    val cfg = baseCfg.copy(
      queueLimits = QueueLimits(
        maxActiveEvents = 100,
        maxActiveBytes = 10,
        overflowStrategy = OverflowStrategy.DROP_NEWEST,
      ),
    )
    val repo = newRepo()

    val first = repo.enqueue("T", "{\"i\":1}", cfg)
    assertThat(first.isAccepted).isTrue()

    // Second event pushes total bytes over maxActiveBytes
    val second = repo.enqueue("T", "{\"i\":2}", cfg)
    assertThat(second.isAccepted).isFalse()
    assertThat(second).isInstanceOf(EnqueueResult.Rejected.QueueFull::class.java)
  }

  // ---------------------------------------------------------------------------
  // Idempotency
  // ---------------------------------------------------------------------------

  @Test
  fun `duplicate idempotencyKeyOverride is rejected as DuplicateIdempotency`() = runTest {
    val repo = newRepo()

    val first = repo.enqueue("T", "{\"x\":1}", baseCfg, idempotencyKeyOverride = "dup-key")
    assertThat(first.isAccepted).isTrue()

    val second = repo.enqueue("T", "{\"x\":2}", baseCfg, idempotencyKeyOverride = "dup-key")
    assertThat(second.isAccepted).isFalse()
    assertThat(second).isInstanceOf(EnqueueResult.Rejected.DuplicateIdempotency::class.java)
  }

  @Test
  fun `same stableEventId produces same idempotency key (deterministic)`() = runTest {
    val repo = newRepo()

    val r1 = repo.enqueue("T", "{\"x\":1}", baseCfg, stableEventId = "stable-1")
    assertThat(r1.isAccepted).isTrue()
    val key1 = (r1 as EnqueueResult.Accepted).idempotencyKey

    // Second enqueue with same stableEventId will hit the unique constraint (same key),
    // confirming determinism.
    val r2 = repo.enqueue("T", "{\"x\":2}", baseCfg, stableEventId = "stable-1")
    assertThat(r2.isAccepted).isFalse()
    assertThat(r2).isInstanceOf(EnqueueResult.Rejected.DuplicateIdempotency::class.java)
  }

  @Test
  fun `different stableEventIds produce different idempotency keys`() = runTest {
    val repo = newRepo()

    val r1 = repo.enqueue("T", "{\"x\":1}", baseCfg, stableEventId = "stable-A")
    val r2 = repo.enqueue("T", "{\"x\":2}", baseCfg, stableEventId = "stable-B")

    assertThat(r1.isAccepted).isTrue()
    assertThat(r2.isAccepted).isTrue()
    val key1 = (r1 as EnqueueResult.Accepted).idempotencyKey
    val key2 = (r2 as EnqueueResult.Accepted).idempotencyKey
    assertThat(key1).isNotEqualTo(key2)
  }

  @Test
  fun `explicit idempotencyKeyOverride is used verbatim`() = runTest {
    val repo = newRepo()

    val result = repo.enqueue(
      "T", "{\"x\":1}", baseCfg,
      idempotencyKeyOverride = "my-exact-key-123",
    )

    assertThat(result.isAccepted).isTrue()
    val accepted = result as EnqueueResult.Accepted
    assertThat(accepted.idempotencyKey).isEqualTo("my-exact-key-123")
  }

  @Test
  fun `without stableEventId or override each enqueue gets a unique key`() = runTest {
    val repo = newRepo()

    val r1 = repo.enqueue("T", "{\"x\":1}", baseCfg)
    val r2 = repo.enqueue("T", "{\"x\":2}", baseCfg)

    assertThat(r1.isAccepted).isTrue()
    assertThat(r2.isAccepted).isTrue()
    val key1 = (r1 as EnqueueResult.Accepted).idempotencyKey
    val key2 = (r2 as EnqueueResult.Accepted).idempotencyKey
    assertThat(key1).isNotEqualTo(key2)
  }

  // ---------------------------------------------------------------------------
  // Payload Codec round-trip through QueueRepository
  // ---------------------------------------------------------------------------

  @Test
  fun `encrypt and decrypt round-trip with NoopCryptoProvider`() = runTest {
    val repo = newRepo()
    val payload = "{\"sensor\":\"temp\",\"value\":42}"

    val result = repo.enqueue("T", payload, baseCfg)
    assertThat(result.isAccepted).isTrue()
    val id = (result as EnqueueResult.Accepted).id

    val batch = repo.nextBatch(System.currentTimeMillis() + 1000)
    val event = batch.first { it.id == id }
    val decoded = repo.decodePayloadJson(event)
    assertThat(decoded).isEqualTo(payload)
  }

  @Test
  fun `stored event is retrievable in nextBatch`() = runTest {
    val repo = newRepo()

    val result = repo.enqueue("T", "{\"k\":\"v\"}", baseCfg)
    assertThat(result.isAccepted).isTrue()
    val id = (result as EnqueueResult.Accepted).id

    val batch = repo.nextBatch(System.currentTimeMillis() + 1000)
    assertThat(batch).isNotEmpty()
    assertThat(batch.map { it.id }).contains(id)
  }

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
