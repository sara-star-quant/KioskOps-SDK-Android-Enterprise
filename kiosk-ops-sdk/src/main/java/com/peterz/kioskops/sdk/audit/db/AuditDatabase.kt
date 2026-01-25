/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.audit.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for persistent audit trail.
 *
 * Stores audit events with hash chain for tamper detection.
 * The database file is encrypted if the device supports it.
 *
 * This is a separate database from QueueDatabase to avoid
 * migration conflicts and allow independent versioning.
 */
@Database(
  entities = [AuditEventEntity::class, AuditChainState::class],
  version = 1,
  exportSchema = true,
)
abstract class AuditDatabase : RoomDatabase() {

  abstract fun auditDao(): AuditDao

  companion object {
    private const val DATABASE_NAME = "kioskops_audit.db"

    @Volatile
    private var INSTANCE: AuditDatabase? = null

    /**
     * Get or create the audit database singleton.
     *
     * @param context Android context.
     * @return The audit database instance.
     */
    fun getInstance(context: Context): AuditDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
      }
    }

    private fun buildDatabase(context: Context): AuditDatabase {
      return Room.databaseBuilder(
        context.applicationContext,
        AuditDatabase::class.java,
        DATABASE_NAME
      )
        .fallbackToDestructiveMigration()
        .build()
    }

    /**
     * Close and clear the database instance.
     * Used primarily for testing.
     */
    fun closeInstance() {
      synchronized(this) {
        INSTANCE?.close()
        INSTANCE = null
      }
    }
  }
}
