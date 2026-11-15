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

  @Test
  fun `migration 2 to 3 backfills seq in ts then id order and keeps hashes`() {
    ctx.deleteDatabase("mig_test.db")
    val path = ctx.getDatabasePath("mig_test.db")
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
    // Inserted out of order: b and a share ts, c is earliest.
    raw.execSQL("INSERT INTO audit_events VALUES ('b', 2000, 'n', '{}', 'h_c', 'h_b', NULL, NULL, 1, NULL)")
    raw.execSQL("INSERT INTO audit_events VALUES ('a', 2000, 'n', '{}', 'h_c', 'h_a', NULL, NULL, 1, NULL)")
    raw.execSQL("INSERT INTO audit_events VALUES ('c', 1000, 'n', '{}', 'GENESIS', 'h_c', NULL, NULL, 1, NULL)")
    raw.execSQL("INSERT INTO audit_chain_state VALUES (1, 'h_b', 2000, 1, 3)")
    raw.version = 2
    raw.close()

    val db = Room.databaseBuilder(ctx, AuditDatabase::class.java, "mig_test.db")
      .addMigrations(AuditMigrations.MIGRATION_1_2, AuditMigrations.MIGRATION_2_3)
      .allowMainThreadQueries()
      .build()
    val sdb = db.openHelper.writableDatabase
    val rows = mutableListOf<Triple<String, Long, String>>()
    sdb.query("SELECT id, seq, hash FROM audit_events ORDER BY seq").use {
      while (it.moveToNext()) rows += Triple(it.getString(0), it.getLong(1), it.getString(2))
    }
    assertThat(rows).containsExactly(
      Triple("c", 1L, "h_c"), Triple("a", 2L, "h_a"), Triple("b", 3L, "h_b"),
    ).inOrder()
    sdb.query("SELECT DISTINCT formatVersion FROM audit_events").use {
      assertThat(it.count).isEqualTo(1)
      it.moveToFirst()
      assertThat(it.getInt(0)).isEqualTo(1)
    }
    sdb.query("SELECT prunedThroughSeq, prunedThroughHash FROM audit_chain_state").use {
      it.moveToFirst()
      assertThat(it.getLong(0)).isEqualTo(0L)
      assertThat(it.getString(1)).isEqualTo("GENESIS")
    }
    db.close()
    ctx.deleteDatabase("mig_test.db")
  }
}
