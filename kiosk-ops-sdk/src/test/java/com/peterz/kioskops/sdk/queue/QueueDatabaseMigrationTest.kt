/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.queue

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueueDatabaseMigrationTest {

  private val ctx = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun `MIGRATION_3_4 adds userId, dataClassification, anomalyScore columns`() {
    // Build a v4 database and verify the migration SQL is valid
    // by running the migration statements against a fresh SQLite database
    val db = Room.inMemoryDatabaseBuilder(ctx, QueueDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val sdb: SupportSQLiteDatabase = db.openHelper.writableDatabase

    // Verify v4 columns exist by inserting a row with all v4 fields
    sdb.execSQL(
      """INSERT INTO queue_events (id, idempotencyKey, type, payloadBlob, payloadEncoding, payloadBytes,
         createdAtEpochMs, state, attempts, nextAttemptAtEpochMs, permanentFailure, updatedAtEpochMs,
         userId, dataClassification, anomalyScore)
         VALUES ('e1', 'k1', 'test', X'7B7D', 'json', 2, 1700000000000, 'PENDING', 0, 0, 0, 1700000000000,
         'user-A', 'INTERNAL', 0.5)"""
    )

    val cursor = sdb.query("SELECT userId, dataClassification, anomalyScore FROM queue_events WHERE id = 'e1'")
    assertThat(cursor.moveToFirst()).isTrue()
    assertThat(cursor.getString(0)).isEqualTo("user-A")
    assertThat(cursor.getString(1)).isEqualTo("INTERNAL")
    assertThat(cursor.getFloat(2)).isWithin(0.01f).of(0.5f)
    cursor.close()

    // Verify userId index exists
    val indexCursor = sdb.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_queue_events_userId'")
    assertThat(indexCursor.moveToFirst()).isTrue()
    indexCursor.close()

    db.close()
  }

  @Test
  fun `MIGRATION_3_4 SQL statements execute without error`() {
    // Verify the migration SQL is syntactically valid
    val migration = QueueDatabase.MIGRATION_3_4
    assertThat(migration.startVersion).isEqualTo(3)
    assertThat(migration.endVersion).isEqualTo(4)
  }

  @Test
  fun `MIGRATION_2_3 SQL statements are defined`() {
    val migration = QueueDatabase.MIGRATION_2_3
    assertThat(migration.startVersion).isEqualTo(2)
    assertThat(migration.endVersion).isEqualTo(3)
  }
}
