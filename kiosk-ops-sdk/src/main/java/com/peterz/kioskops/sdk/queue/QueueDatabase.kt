package com.peterz.kioskops.sdk.queue

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [QueueEventEntity::class],
  version = 3,
  exportSchema = true
)
abstract class QueueDatabase : RoomDatabase() {
  abstract fun queueDao(): QueueDao

  companion object {
    /** v2 -> v3: add payloadBytes and quarantineReason columns. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE queue_events ADD COLUMN payloadBytes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE queue_events ADD COLUMN quarantineReason TEXT")
        // Backfill for existing rows so storage quotas are meaningful.
        db.execSQL("UPDATE queue_events SET payloadBytes = LENGTH(payloadBlob) WHERE payloadBytes = 0")
      }
    }
  }
}
