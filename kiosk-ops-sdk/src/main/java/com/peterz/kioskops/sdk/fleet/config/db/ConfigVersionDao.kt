/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet.config.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Data Access Object for configuration versions.
 *
 * Thread Safety: All methods are suspend functions for safe coroutine usage.
 */
@Dao
interface ConfigVersionDao {
  /**
   * Get the currently active configuration version.
   */
  @Query("SELECT * FROM config_versions WHERE isActive = 1 LIMIT 1")
  suspend fun getActiveVersion(): ConfigVersionEntity?

  /**
   * Get recent versions for rollback selection.
   */
  @Query("SELECT * FROM config_versions ORDER BY version DESC LIMIT :limit")
  suspend fun getRecentVersions(limit: Int): List<ConfigVersionEntity>

  /**
   * Get a specific version by number.
   */
  @Query("SELECT * FROM config_versions WHERE version = :version")
  suspend fun getVersion(version: Long): ConfigVersionEntity?

  /**
   * Get the highest version number.
   */
  @Query("SELECT MAX(version) FROM config_versions")
  suspend fun getMaxVersion(): Long?

  /**
   * Insert or update a version.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entity: ConfigVersionEntity)

  /**
   * Deactivate all versions.
   */
  @Query("UPDATE config_versions SET isActive = 0")
  suspend fun deactivateAll()

  /**
   * Count total versions.
   */
  @Query("SELECT COUNT(*) FROM config_versions")
  suspend fun countVersions(): Int

  /**
   * Delete old versions, keeping the most recent ones.
   *
   * Data Retention: Implements ISO 27001 A.8.10 retention controls.
   */
  @Query("""
    DELETE FROM config_versions
    WHERE version NOT IN (
      SELECT version FROM config_versions
      ORDER BY version DESC
      LIMIT :keepCount
    )
  """)
  suspend fun pruneOldVersions(keepCount: Int)

  /**
   * Activate a specific version.
   */
  @Transaction
  suspend fun activateVersion(version: Long): Boolean {
    val entity = getVersion(version) ?: return false
    deactivateAll()
    insert(entity.copy(isActive = true))
    return true
  }
}
