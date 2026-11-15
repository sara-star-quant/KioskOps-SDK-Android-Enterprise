/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.audit.db.AuditDatabase
import com.sarastarquant.kioskops.sdk.compliance.RetentionPolicy
import com.sarastarquant.kioskops.sdk.util.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuditChainAdversarialTest {

  private lateinit var ctx: Context
  private lateinit var db: AuditDatabase
  private lateinit var trail: PersistentAuditTrail

  private var nowMs = 1_700_000_000_000L
  private val clock = object : Clock {
    override fun nowMs(): Long = nowMs
  }
  private val dayMs = 24 * 60 * 60 * 1000L

  @Before
  fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(ctx, AuditDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    AuditDatabase.setInstance(db)
    trail = PersistentAuditTrail(
      context = ctx,
      retentionProvider = { RetentionPolicy.maximalistDefaults() },
      clock = clock,
    )
  }

  @After
  fun tearDown() {
    db.close()
    AuditDatabase.setInstance(null)
  }

  @Test
  fun `same millisecond events verify valid`() = runTest {
    for (i in 1..50) {
      trail.record("same_ms_$i", mapOf("seq" to i.toString()))
    }

    val result = trail.verifyChainIntegrity()
    assertThat(result).isInstanceOf(ChainVerificationResult.Valid::class.java)
    assertThat((result as ChainVerificationResult.Valid).eventCount).isEqualTo(50)
  }

  @Test
  fun `backward clock step verifies valid`() = runTest {
    trail.record("before_1")
    nowMs += 1000
    trail.record("before_2")
    nowMs -= 60 * 60 * 1000L
    trail.record("after_step_back")

    val result = trail.verifyChainIntegrity()
    assertThat(result).isInstanceOf(ChainVerificationResult.Valid::class.java)
    assertThat((result as ChainVerificationResult.Valid).eventCount).isEqualTo(3)
  }

  @Ignore("pending: hash input needs unambiguous field boundaries")
  @Test
  fun `pipe in event name cannot alias another event`() = runTest {
    trail.record("a|b", emptyMap())

    // Content is "id|ts|a|b|{}|prev" for name "a|b"; re-split it as name "a" with fields "b|{}".
    db.openHelper.writableDatabase.execSQL(
      "UPDATE audit_events SET name = 'a', fieldsJson = 'b|{}' WHERE name = 'a|b'",
    )

    val result = trail.verifyChainIntegrity()
    assertThat(result).isInstanceOf(ChainVerificationResult.HashMismatch::class.java)
  }

  @Ignore("pending: truncation needs a server-pinned checkpoint")
  @Test
  fun `deleting oldest events is detected`() = runTest {
    for (i in 1..5) {
      trail.record("event_$i")
      nowMs += 1000
    }

    db.openHelper.writableDatabase.execSQL(
      "DELETE FROM audit_events WHERE name IN ('event_1', 'event_2')",
    )

    val result = trail.verifyChainIntegrity()
    assertThat(result).isNotInstanceOf(ChainVerificationResult.Valid::class.java)
  }

  @Ignore("pending: truncation needs a server-pinned checkpoint")
  @Test
  fun `deleting tail events is detected`() = runTest {
    for (i in 1..5) {
      trail.record("event_$i")
      nowMs += 1000
    }

    db.openHelper.writableDatabase.execSQL(
      "DELETE FROM audit_events WHERE name IN ('event_4', 'event_5')",
    )

    val result = trail.verifyChainIntegrity()
    assertThat(result).isNotInstanceOf(ChainVerificationResult.Valid::class.java)
  }

  @Test
  fun `legitimate retention prune verifies valid`() = runTest {
    val shortRetention = RetentionPolicy(
      retainSentEventsDays = 1,
      retainFailedEventsDays = 1,
      retainAuditDays = 1,
      retainLogsDays = 1,
      retainTelemetryDays = 1,
    )
    val retentionTrail = PersistentAuditTrail(
      context = ctx,
      retentionProvider = { shortRetention },
      clock = clock,
    )

    retentionTrail.record("old_1")
    nowMs += 1000
    retentionTrail.record("old_2")
    nowMs += 2 * dayMs
    retentionTrail.record("new_1")
    nowMs += 1000
    retentionTrail.record("new_2")

    retentionTrail.applyRetention()

    assertThat(retentionTrail.getStatistics().totalEvents).isEqualTo(2)
    val result = retentionTrail.verifyChainIntegrity()
    assertThat(result).isInstanceOf(ChainVerificationResult.Valid::class.java)
  }

  @Test
  fun `new events get next seq under mutex`() = runTest {
    (1..50).map { i -> async(Dispatchers.Default) { trail.record("e_$i") } }.awaitAll()

    val seqs = db.auditDao().getEventsPageBySeq(0, 100).map { it.seq }
    assertThat(seqs).containsExactlyElementsIn((1L..50L).toList()).inOrder()
  }

  @Test
  fun `seq keeps increasing after retention prune`() = runTest {
    trail.record("old_1")
    trail.record("old_2")
    nowMs += 400 * dayMs
    trail.record("new_1")
    trail.applyRetention()
    trail.record("new_2")

    val seqs = db.auditDao().getEventsPageBySeq(0, 100).map { it.seq }
    assertThat(seqs).isEqualTo(listOf(3L, 4L))
  }
}
