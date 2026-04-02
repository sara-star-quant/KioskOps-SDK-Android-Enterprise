/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet.config.db

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for configuration version storage.
 *
 * Security: Stored separately from event queue and audit databases
 * for isolation (defense in depth).
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@Database(
  entities = [ConfigVersionEntity::class],
  version = 1,
  exportSchema = true,
)
abstract class ConfigDatabase : RoomDatabase() {
  abstract fun configVersionDao(): ConfigVersionDao

  companion object {
    private const val DATABASE_NAME = "kioskops_config.db"

    @Volatile
    private var INSTANCE: ConfigDatabase? = null

    fun getInstance(context: Context): ConfigDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
      }
    }

    private fun buildDatabase(context: Context): ConfigDatabase {
      return Room.databaseBuilder(
        context.applicationContext,
        ConfigDatabase::class.java,
        DATABASE_NAME
      )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    }

    /**
     * Replace the singleton instance (for testing with in-memory databases).
     * Pass null to clear.
     */
    @androidx.annotation.VisibleForTesting
    internal fun setInstance(database: ConfigDatabase?) {
      INSTANCE = database
    }
  }
}
