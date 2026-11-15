/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.audit.ChainVerificationResult
import com.sarastarquant.kioskops.sdk.audit.core.AuditChainCore
import com.sarastarquant.kioskops.sdk.audit.core.AuditHashFormat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuditDatabaseMigrationTest {

  private val ctx = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun `v2 schema includes userId column`() {
    val db = Room.inMemoryDatabaseBuilder(ctx, AuditDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val sdb: SupportSQLiteDatabase = db.openHelper.writableDatabase

    // Insert a row with userId (v2 feature)
    sdb.execSQL(
      """INSERT INTO audit_events (id, ts, name, fieldsJson, prevHash, hash, chainGeneration, userId)
         VALUES ('a1', 1700000000000, 'sdk_initialized', '{}', 'GENESIS', 'hash1', 1, 'user-123')"""
    )

    val cursor = sdb.query("SELECT userId FROM audit_events WHERE id = 'a1'")
    assertThat(cursor.moveToFirst()).isTrue()
    assertThat(cursor.getString(0)).isEqualTo("user-123")
    cursor.close()

    // Verify userId index exists
    val indexCursor = sdb.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_audit_events_userId'")
    assertThat(indexCursor.moveToFirst()).isTrue()
    indexCursor.close()

    db.close()
  }

  @Test
  fun `MIGRATION_1_2 is defined with correct versions`() {
    val migration = AuditMigrations.MIGRATION_1_2
    assertThat(migration.startVersion).isEqualTo(1)
    assertThat(migration.endVersion).isEqualTo(2)
  }

  @Test
  fun `v2 schema supports null userId for pre-migration rows`() {
    val db = Room.inMemoryDatabaseBuilder(ctx, AuditDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val sdb: SupportSQLiteDatabase = db.openHelper.writableDatabase

    // Insert a row without userId (simulates pre-migration data)
    sdb.execSQL(
      """INSERT INTO audit_events (id, ts, name, fieldsJson, prevHash, hash, chainGeneration)
         VALUES ('a2', 1700000001000, 'event', '{}', 'hash1', 'hash2', 1)"""
    )

    val cursor = sdb.query("SELECT userId FROM audit_events WHERE id = 'a2'")
    assertThat(cursor.moveToFirst()).isTrue()
    assertThat(cursor.isNull(0)).isTrue()
    cursor.close()

    db.close()
  }

  private fun createV2(
    name: String,
    eventCount: Long,
    rows: List<Triple<String, Long, String>>,
    prevHashOf: (String) -> String = { "p" },
  ) {
    ctx.deleteDatabase(name)
    val path = ctx.getDatabasePath(name)
    path.parentFile?.mkdirs()
    val raw = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null)
    raw.execSQL(
      "CREATE TABLE IF NOT EXISTS `audit_events` (`id` TEXT NOT NULL, `ts` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
        "`fieldsJson` TEXT NOT NULL, `prevHash` TEXT NOT NULL, `hash` TEXT NOT NULL, `signature` TEXT, " +
        "`deviceAttestationBlob` BLOB, `chainGeneration` INTEGER NOT NULL, `userId` TEXT, PRIMARY KEY(`id`))"
    )
    raw.execSQL(
      "CREATE TABLE IF NOT EXISTS `audit_chain_state` (`id` INTEGER NOT NULL, `lastHash` TEXT NOT NULL, " +
        "`lastTs` INTEGER NOT NULL, `chainGeneration` INTEGER NOT NULL, `eventCount` INTEGER NOT NULL, PRIMARY KEY(`id`))"
    )
    for (c in listOf("ts", "name", "chainGeneration,ts", "userId")) {
      raw.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_events_${c.replace(",", "_")}` ON `audit_events` ($c)")
    }
    raw.beginTransaction()
    for ((id, ts, hash) in rows) {
      raw.execSQL(
        "INSERT INTO audit_events VALUES (?, ?, 'n', '{}', ?, ?, NULL, NULL, 1, NULL)",
        arrayOf<Any>(id, ts, prevHashOf(id), hash),
      )
    }
    raw.execSQL("INSERT INTO audit_chain_state VALUES (1, 'last', 0, 1, $eventCount)")
    raw.setTransactionSuccessful()
    raw.endTransaction()
    raw.version = 2
    raw.close()
  }

  private fun openMigrated(name: String): AuditDatabase =
    Room.databaseBuilder(ctx, AuditDatabase::class.java, name)
      .addMigrations(AuditMigrations.MIGRATION_1_2, AuditMigrations.MIGRATION_2_3)
      .allowMainThreadQueries()
      .build()

  private fun seqRows(db: AuditDatabase): List<Triple<String, Long, String>> {
    val rows = mutableListOf<Triple<String, Long, String>>()
    db.openHelper.writableDatabase.query("SELECT id, seq, hash FROM audit_events ORDER BY seq").use {
      while (it.moveToNext()) rows += Triple(it.getString(0), it.getLong(1), it.getString(2))
    }
    return rows
  }

  @Test
  fun `migration 2 to 3 backfills seq in ts then id order and keeps hashes`() {
    // Inserted out of order: b and a share ts, c is earliest.
    createV2(
      "mig_test.db", 3, listOf(Triple("b", 2000L, "h_b"), Triple("a", 2000L, "h_a"), Triple("c", 1000L, "h_c")),
    ) { "GENESIS" }
    val db = openMigrated("mig_test.db")
    assertThat(seqRows(db)).containsExactly(
      Triple("c", 1L, "h_c"), Triple("a", 2L, "h_a"), Triple("b", 3L, "h_b"),
    ).inOrder()
    val sdb = db.openHelper.writableDatabase
    sdb.query("SELECT DISTINCT formatVersion FROM audit_events").use {
      assertThat(it.count).isEqualTo(1)
      it.moveToFirst()
      assertThat(it.getInt(0)).isEqualTo(1)
    }
    sdb.query("SELECT prunedThroughSeq, prunedThroughHash, lastAckedSeq FROM audit_chain_state").use {
      it.moveToFirst()
      assertThat(it.getLong(0)).isEqualTo(0L)
      assertThat(it.getString(1)).isEqualTo("GENESIS")
      assertThat(it.getLong(2)).isEqualTo(0L)
    }
    db.close()
    ctx.deleteDatabase("mig_test.db")
  }

  @Test
  fun `migration 2 to 3 backfills 5000 rows correctly`() {
    val rows = (0 until 5000).map { Triple("id%05d".format(4999 - it), (it / 3).toLong(), "h$it") }
    createV2("mig_big.db", 5000, rows)
    val db = openMigrated("mig_big.db")
    val got = seqRows(db)
    val expected = rows.sortedWith(compareBy<Triple<String, Long, String>>({ it.second }, { it.first }))
    assertThat(got.map { it.first }).isEqualTo(expected.map { it.first })
    assertThat(got.map { it.second }).isEqualTo((1L..5000L).toList())
    assertThat(got.associate { it.first to it.third }).isEqualTo(rows.associate { it.first to it.third })
    db.close()
    ctx.deleteDatabase("mig_big.db")
  }

  @Test
  fun `migration 2 to 3 on pruned legacy db keeps next seq above backfilled seqs`() = kotlinx.coroutines.test.runTest {
    // 2 rows remain but eventCount says 10 were recorded before retention pruned 8.
    createV2("mig_pruned.db", 10, listOf(Triple("y", 6000L, "h_y"), Triple("x", 5000L, "h_x")))
    val db = openMigrated("mig_pruned.db")
    assertThat(seqRows(db)).containsExactly(Triple("x", 1L, "h_x"), Triple("y", 2L, "h_y")).inOrder()
    AuditDatabase.setInstance(db)
    try {
      com.sarastarquant.kioskops.sdk.audit.PersistentAuditTrail(
        context = ctx,
        retentionProvider = { com.sarastarquant.kioskops.sdk.compliance.RetentionPolicy.maximalistDefaults() },
        clock = object : com.sarastarquant.kioskops.sdk.util.Clock {
          override fun nowMs(): Long = 7000L
        },
      ).record("next")
      val last = db.auditDao().getEventsPageBySeq(2, 10).single()
      assertThat(last.seq).isEqualTo(11L)
    } finally {
      AuditDatabase.setInstance(null)
      db.close()
      ctx.deleteDatabase("mig_pruned.db")
    }
  }

  @Test
  fun `pruned legacy chain stays valid after migration`() = kotlinx.coroutines.test.runTest {
    // A v1-format chain of 5 events whose 2 oldest retention removed before the upgrade.
    var prev = "GENESIS"
    val chain = (1..5).map { i ->
      val id = "e$i"
      val ts = 1000L * i
      val hash = AuditChainCore.hash(AuditChainCore.content(AuditHashFormat.V1, id, ts, "n", "{}", prev))
      val row = Triple(id, ts, hash) to prev
      prev = hash
      row
    }
    val kept = chain.drop(2)
    createV2("mig_legacy.db", 5, kept.map { it.first }) { id -> kept.first { it.first.first == id }.second }
    val db = openMigrated("mig_legacy.db")
    AuditDatabase.setInstance(db)
    try {
      val state = db.auditDao().getChainState()!!
      assertThat(state.prunedThroughSeq).isEqualTo(0L)
      assertThat(state.prunedThroughHash).isEqualTo(chain[1].first.third)
      assertThat(state.lastAckedSeq).isEqualTo(0L)
      val result = com.sarastarquant.kioskops.sdk.audit.PersistentAuditTrail(
        context = ctx,
        retentionProvider = { com.sarastarquant.kioskops.sdk.compliance.RetentionPolicy.maximalistDefaults() },
      ).verifyChainIntegrity()
      assertThat(result).isInstanceOf(ChainVerificationResult.Valid::class.java)
      assertThat((result as ChainVerificationResult.Valid).eventCount).isEqualTo(3)
    } finally {
      AuditDatabase.setInstance(null)
      db.close()
      ctx.deleteDatabase("mig_legacy.db")
    }
  }
}
