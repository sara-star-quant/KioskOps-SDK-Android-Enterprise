/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.audit.core.AuditChainCore
import com.sarastarquant.kioskops.sdk.audit.core.AuditHashFormat
import com.sarastarquant.kioskops.sdk.audit.core.PinResult
import com.sarastarquant.kioskops.sdk.audit.db.AuditDatabase
import com.sarastarquant.kioskops.sdk.audit.db.createLegacyV2Database
import com.sarastarquant.kioskops.sdk.audit.db.legacyV1Chain
import com.sarastarquant.kioskops.sdk.audit.db.openLegacyMigrated
import com.sarastarquant.kioskops.sdk.compliance.RetentionPolicy
import com.sarastarquant.kioskops.sdk.util.Clock
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The path a device takes on upgrade: a v2 database holding a v1-format chain migrates, records V2
 * events, verifies across the V1/V2 boundary, and produces checkpoints a pinning server accepts.
 */
@RunWith(RobolectricTestRunner::class)
class AuditUpgradePathTest {

  private val ctx = ApplicationProvider.getApplicationContext<Context>()
  private var nowMs = 10_000L
  private val clock = object : Clock {
    override fun nowMs(): Long = nowMs++
  }

  @Test
  fun `legacy chain upgrades and checkpoints consistently`() = runTest {
    upgradeAndCheckpoint(legacy = (1..4).map { "e$it" to 1000L * it }, keepRows = true)
  }

  @Test
  fun `legacy same millisecond and backward clock rows upgrade and checkpoint consistently`() = runTest {
    upgradeAndCheckpoint(
      legacy = listOf("z" to 1000L, "m" to 1000L, "a" to 1000L, "k" to 3000L, "b" to 2000L),
      keepRows = true,
    )
  }

  @Test
  fun `fully pruned legacy database upgrades and checkpoints consistently`() = runTest {
    upgradeAndCheckpoint(legacy = (1..4).map { "e$it" to 1000L * it }, keepRows = false)
  }

  private suspend fun upgradeAndCheckpoint(legacy: List<Pair<String, Long>>, keepRows: Boolean) {
    val name = "audit_upgrade_path.db"
    val chain = legacyV1Chain(legacy)
    val legacyHead = chain.last().first.third
    val rows = if (keepRows) chain.map { it.first } else emptyList()
    createLegacyV2Database(ctx, name, chain.size.toLong(), legacyHead, rows) { id ->
      chain.first { it.first.first == id }.second
    }
    val db = openLegacyMigrated(ctx, name)
    AuditDatabase.setInstance(db)
    try {
      val trail = PersistentAuditTrail(ctx, { RetentionPolicy.maximalistDefaults() }, clock)

      trail.record("after_upgrade")
      val firstV2 = db.auditDao().getHeadEvent()!!
      assertThat(firstV2.seq).isEqualTo(chain.size + 1L)
      assertThat(firstV2.formatVersion).isEqualTo(AuditHashFormat.V2.version)
      assertThat(firstV2.prevHash).isEqualTo(legacyHead)
      assertValid(trail, rows.size + 1)

      val first = trail.createCheckpoint(sign = false)
      assertThat(first.headSeq).isEqualTo(firstV2.seq)
      assertThat(first.linkSeq).isNull()
      assertThat(first.linkHash).isNull()
      trail.acknowledgeCheckpoint(first)

      trail.record("more")
      val second = trail.createCheckpoint(sign = false)
      assertThat(second.linkSeq).isEqualTo(first.headSeq)
      assertThat(second.linkHash).isEqualTo(first.headHash)
      assertValid(trail, rows.size + 2)

      val result = AuditChainCore.compareToPin(
        pin = first,
        pinReceivedAtMs = 0L,
        current = second,
        nowMs = 1_000L,
        retentionWindowMs = 30L * 24 * 60 * 60 * 1000,
      )
      assertThat(result).isEqualTo(PinResult.Consistent)
    } finally {
      AuditDatabase.setInstance(null)
      db.close()
      ctx.deleteDatabase(name)
    }
  }

  private suspend fun assertValid(trail: PersistentAuditTrail, events: Int) {
    val result = trail.verifyChainIntegrity()
    assertThat(result).isInstanceOf(ChainVerificationResult.Valid::class.java)
    assertThat((result as ChainVerificationResult.Valid).eventCount).isEqualTo(events)
  }
}
