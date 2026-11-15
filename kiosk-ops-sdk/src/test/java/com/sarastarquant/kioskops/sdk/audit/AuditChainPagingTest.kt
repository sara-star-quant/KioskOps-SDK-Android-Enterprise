/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.audit.core.AuditChainCore
import com.sarastarquant.kioskops.sdk.audit.core.AuditHashFormat
import com.sarastarquant.kioskops.sdk.audit.core.ChainLink
import com.sarastarquant.kioskops.sdk.audit.core.PageResult
import com.sarastarquant.kioskops.sdk.audit.db.AuditDatabase
import com.sarastarquant.kioskops.sdk.audit.db.SeqBounds
import com.sarastarquant.kioskops.sdk.compliance.RetentionPolicy
import com.sarastarquant.kioskops.sdk.util.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
class AuditChainPagingTest {

  private lateinit var db: AuditDatabase
  private lateinit var trail: PersistentAuditTrail
  private val pageQueries = CopyOnWriteArrayList<List<Any?>>()

  /** Runs once when a page query after the given seq is issued, then clears itself. */
  @Volatile private var onPageAfter: Pair<Long, () -> Unit>? = null

  private var nowMs = 1_000L
  private val clock = object : Clock {
    override fun nowMs(): Long = nowMs
  }

  @Before
  fun setUp() {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(ctx, AuditDatabase::class.java)
      .allowMainThreadQueries()
      .setQueryCallback(
        { sql, args ->
          if (sql.contains("FROM audit_events WHERE seq >")) {
            pageQueries += args
            onPageAfter?.takeIf { it.first == args[0] }?.let { (_, action) ->
              onPageAfter = null
              action()
            }
          }
        },
        Executor { it.run() },
      )
      .build()
    AuditDatabase.setInstance(db)
    trail = PersistentAuditTrail(ctx, { RetentionPolicy.maximalistDefaults() }, clock)
  }

  @After
  fun tearDown() {
    db.close()
    AuditDatabase.setInstance(null)
  }

  @Test
  fun `verification never loads more than one page`() = runTest {
    repeat(2000) {
      trail.record("e$it")
      nowMs++
    }
    pageQueries.clear()

    val result = trail.verifyChainIntegrity()

    assertThat(result).isInstanceOf(ChainVerificationResult.Valid::class.java)
    assertThat((result as ChainVerificationResult.Valid).eventCount).isEqualTo(2000)
    // Every page query is bound by the page size, and 2000 rows need more than one query.
    assertThat(pageQueries.map { it[1] }.toSet()).containsExactly(PersistentAuditTrail.VERIFY_PAGE_SIZE.toLong())
    assertThat(pageQueries.size).isAtLeast(4)
  }

  @Test
  fun `a break on page 3 reports the right seq`() = runTest {
    repeat(1500) {
      trail.record("e$it")
      nowMs++
    }
    val victim = db.auditDao().getEventsPageBySeq(1199, 1).single()
    assertThat(victim.seq).isEqualTo(1200L)
    db.openHelper.writableDatabase.execSQL("UPDATE audit_events SET prevHash = 'forged' WHERE seq = 1200")

    val result = trail.verifyChainIntegrity()

    assertThat(result).isInstanceOf(ChainVerificationResult.Broken::class.java)
    result as ChainVerificationResult.Broken
    assertThat(result.brokenAtId).isEqualTo(victim.id)
    assertThat(result.actualPrevHash).isEqualTo("forged")
    val previous = db.auditDao().getEventsPageBySeq(1198, 1).single()
    assertThat(result.expectedPrevHash).isEqualTo(previous.hash)
  }

  @Test
  fun `forged prevHash at the first row of the second page is broken`() = runTest {
    repeat(PersistentAuditTrail.VERIFY_PAGE_SIZE + 1) {
      trail.record("e$it")
      nowMs++
    }
    val dao = db.auditDao()
    val lastOfFirstPage = dao.getEventsPageBySeq(499, 1).single()
    val victim = dao.getEventsPageBySeq(500, 1).single()
    assertThat(victim.seq).isEqualTo(501L)
    // Re-hash the row over the forged prevHash so only the chain link is wrong.
    val format = AuditHashFormat.forStoredVersion(victim.formatVersion)!!
    val rehash = AuditChainCore.hash(
      AuditChainCore.content(format, victim.id, victim.ts, victim.name, victim.fieldsJson, "forged"),
    )
    db.openHelper.writableDatabase.execSQL(
      "UPDATE audit_events SET prevHash = 'forged', hash = '$rehash' WHERE seq = 501",
    )

    val result = trail.verifyChainIntegrity()

    assertThat(result).isInstanceOf(ChainVerificationResult.Broken::class.java)
    result as ChainVerificationResult.Broken
    assertThat(result.brokenAtId).isEqualTo(victim.id)
    assertThat(result.expectedPrevHash).isEqualTo(lastOfFirstPage.hash)
    assertThat(result.actualPrevHash).isEqualTo("forged")
  }

  @Test
  fun `ranged verification across a backward clock step is valid`() = runTest {
    for (ts in listOf(1000L, 2000L, 3000L, 1500L, 4000L)) {
      nowMs = ts
      trail.record("e$ts")
    }

    val result = trail.verifyChainIntegrity(fromTs = 1500, toTs = 2000)

    // ts 1500 and 2000 sit at seq 4 and 2; the row between them (ts 3000) is verified too.
    assertThat(result).isInstanceOf(ChainVerificationResult.Valid::class.java)
    assertThat((result as ChainVerificationResult.Valid).eventCount).isEqualTo(3)
  }

  @Test
  fun `ranged verification with no rows in range is EmptyRange`() = runTest {
    trail.record("only")

    val result = trail.verifyChainIntegrity(fromTs = 5_000, toTs = 6_000)

    assertThat(result).isEqualTo(ChainVerificationResult.EmptyRange(5_000, 6_000))
  }

  @Test
  fun `seq bounds query returns min and max inside the ts window`() = runBlocking {
    for (ts in listOf(1000L, 2000L, 3000L, 1500L)) {
      nowMs = ts
      trail.record("e$ts")
    }
    val dao = db.auditDao()

    assertThat(dao.getSeqBoundsInRange(1500, 2000)).isEqualTo(SeqBounds(2L, 4L))
    assertThat(dao.getSeqBoundsInRange(9000, 9999)).isEqualTo(SeqBounds(null, null))
  }

  @Test
  fun `a page with a repeated seq is broken`() {
    fun link(seq: Long, prev: String): ChainLink {
      val hash = AuditChainCore.hash(AuditChainCore.content(AuditHashFormat.V2, "id$seq", seq, "n", "{}", prev))
      return ChainLink(seq, "id$seq", seq, "n", "{}", prev, hash, AuditHashFormat.V2.version)
    }
    val first = link(1, "GENESIS")
    val repeated = link(1, first.hash)

    val result = AuditChainCore.verifyPage(listOf(first, repeated), "GENESIS")

    assertThat(result).isInstanceOf(PageResult.Broken::class.java)
    assertThat((result as PageResult.Broken).reason).contains("does not follow")
  }

  @Test
  fun `retention between pages does not report a false break`() = runTest {
    repeat(1500) {
      trail.record("e$it")
      nowMs++
    }
    // seq k has ts 999 + k; a 30-day retention run at this clock prunes seq 1..1200.
    val retentionNow = 2200L + 30L * 24 * 60 * 60 * 1000
    onPageAfter = PersistentAuditTrail.VERIFY_PAGE_SIZE.toLong() to {
      nowMs = retentionNow
      runBlocking { trail.applyRetention() }
    }

    val result = trail.verifyChainIntegrity()

    assertThat(onPageAfter).isNull()
    assertThat(db.auditDao().getChainState()!!.prunedThroughSeq).isEqualTo(1200L)
    assertThat(result).isInstanceOf(ChainVerificationResult.Valid::class.java)
    assertThat((result as ChainVerificationResult.Valid).eventCount).isEqualTo(300)
  }
}
