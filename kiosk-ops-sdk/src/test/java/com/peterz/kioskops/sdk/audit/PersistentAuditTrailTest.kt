/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.audit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.audit.db.AuditDatabase
import com.peterz.kioskops.sdk.compliance.RetentionPolicy
import com.peterz.kioskops.sdk.util.Clock
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersistentAuditTrailTest {

  private lateinit var ctx: Context
  private lateinit var db: AuditDatabase
  private lateinit var trail: PersistentAuditTrail

  private var nowMs = 1_700_000_000_000L
  private val clock = object : Clock {
    override fun nowMs(): Long = nowMs
  }

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
  fun `record stores event and chain state is updated`() = runTest {
    trail.record("sdk_initialized", mapOf("version" to "0.7.0"))

    val stats = trail.getStatistics()
    assertThat(stats.totalEvents).isEqualTo(1)
    assertThat(stats.eventsByName).containsKey("sdk_initialized")
  }

  @Test
  fun `record with userId associates user`() = runTest {
    trail.record("user_action", mapOf("action" to "login"), userId = "user-123")
    trail.record("user_action", mapOf("action" to "logout"), userId = "user-456")

    val events = trail.getEventsByUserId("user-123")
    assertThat(events).hasSize(1)
    assertThat(events.first().name).isEqualTo("user_action")
  }

  @Test
  fun `verify chain integrity succeeds for valid chain`() = runTest {
    trail.record("event_1")
    nowMs += 1000
    trail.record("event_2")
    nowMs += 1000
    trail.record("event_3")

    val result = trail.verifyChainIntegrity()
    assertThat(result).isInstanceOf(ChainVerificationResult.Valid::class.java)
    val valid = result as ChainVerificationResult.Valid
    assertThat(valid.eventCount).isEqualTo(3)
  }

  @Test
  fun `verify chain integrity returns EmptyRange for no events`() = runTest {
    val result = trail.verifyChainIntegrity(fromTs = 5000L, toTs = 6000L)
    assertThat(result).isInstanceOf(ChainVerificationResult.EmptyRange::class.java)
  }

  @Test
  fun `chain integrity across multiple records preserves linkage`() = runTest {
    for (i in 1..10) {
      trail.record("event_$i", mapOf("seq" to i.toString()))
      nowMs += 100
    }

    val result = trail.verifyChainIntegrity()
    assertThat(result).isInstanceOf(ChainVerificationResult.Valid::class.java)
    assertThat((result as ChainVerificationResult.Valid).eventCount).isEqualTo(10)
  }

  @Test
  fun `getStatistics returns correct counts and time range`() = runTest {
    trail.record("type_a")
    nowMs += 1000
    trail.record("type_b")
    nowMs += 1000
    trail.record("type_a")

    val stats = trail.getStatistics()
    assertThat(stats.totalEvents).isEqualTo(3)
    assertThat(stats.oldestEventTs).isEqualTo(1_700_000_000_000L)
    assertThat(stats.newestEventTs).isEqualTo(1_700_000_002_000L)
    assertThat(stats.eventsByName["type_a"]).isEqualTo(2)
    assertThat(stats.eventsByName["type_b"]).isEqualTo(1)
  }

  @Test
  fun `deleteEventsByUserId removes only target user events`() = runTest {
    trail.record("login", userId = "user-A")
    nowMs += 100
    trail.record("login", userId = "user-B")
    nowMs += 100
    trail.record("action", userId = "user-A")

    trail.deleteEventsByUserId("user-A")

    val statsAfter = trail.getStatistics()
    assertThat(statsAfter.totalEvents).isEqualTo(1)

    val remaining = trail.getEventsByUserId("user-B")
    assertThat(remaining).hasSize(1)
  }

  @Test
  fun `retention enforcement deletes old events`() = runTest {
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

    retentionTrail.record("old_event")
    nowMs += 2 * 24 * 60 * 60 * 1000L // advance 2 days
    retentionTrail.record("new_event")

    retentionTrail.applyRetention()

    val stats = retentionTrail.getStatistics()
    assertThat(stats.totalEvents).isEqualTo(1)
  }

  @Test
  fun `export creates gzip file with events`() = runTest {
    trail.record("export_test", mapOf("key" to "value"))
    nowMs += 100
    trail.record("export_test_2")

    val start = 1_700_000_000_000L
    val end = nowMs + 1000
    val file = trail.exportSignedAuditRange(start, end)

    assertThat(file.exists()).isTrue()
    assertThat(file.length()).isGreaterThan(0)
    assertThat(file.name).startsWith("audit_export_")
    assertThat(file.name).endsWith(".jsonl.gz")
  }

  @Test
  fun `large batch of records maintains chain integrity`() = runTest {
    for (i in 1..50) {
      nowMs += 10
      trail.record("batch_$i", mapOf("seq" to i.toString()))
    }

    val stats = trail.getStatistics()
    assertThat(stats.totalEvents).isEqualTo(50)

    val result = trail.verifyChainIntegrity()
    assertThat(result).isInstanceOf(ChainVerificationResult.Valid::class.java)
    assertThat((result as ChainVerificationResult.Valid).eventCount).isEqualTo(50)
  }
}
