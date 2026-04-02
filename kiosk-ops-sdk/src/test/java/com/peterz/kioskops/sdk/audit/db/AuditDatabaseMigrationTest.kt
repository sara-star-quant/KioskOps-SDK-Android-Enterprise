/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.audit.db

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
}
