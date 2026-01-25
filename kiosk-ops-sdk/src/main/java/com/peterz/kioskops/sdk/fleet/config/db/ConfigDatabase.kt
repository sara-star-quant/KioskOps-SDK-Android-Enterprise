/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet.config.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for configuration version storage.
 *
 * Security: Stored separately from event queue and audit databases
 * for isolation (defense in depth).
 */
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
        .fallbackToDestructiveMigration()
        .build()
    }

    /**
     * For testing only.
     */
    internal fun setInstance(database: ConfigDatabase) {
      INSTANCE = database
    }
  }
}
