/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet.config.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.peterz.kioskops.sdk.fleet.config.ConfigSource
import com.peterz.kioskops.sdk.fleet.config.ConfigVersion

/**
 * Room entity for storing configuration versions.
 *
 * Data Retention (ISO 27001 A.8.10): Versions are pruned based on
 * maxRetainedVersions policy to limit storage consumption.
 */
@Entity(
  tableName = "config_versions",
  indices = [Index("version", unique = true)]
)
data class ConfigVersionEntity(
  @PrimaryKey
  val version: Long,
  val createdAtMs: Long,
  val contentHash: String,
  val source: String,
  val abVariant: String?,
  val signature: String?,
  val configJson: String,
  val isActive: Boolean,
) {
  /**
   * Convert to domain model.
   */
  fun toConfigVersion(): ConfigVersion = ConfigVersion(
    version = version,
    createdAtMs = createdAtMs,
    contentHash = contentHash,
    source = ConfigSource.valueOf(source),
    abVariant = abVariant,
    signature = signature,
  )

  companion object {
    /**
     * Create entity from domain model with serialized config.
     */
    fun fromConfigVersion(
      configVersion: ConfigVersion,
      configJson: String,
      isActive: Boolean,
    ): ConfigVersionEntity = ConfigVersionEntity(
      version = configVersion.version,
      createdAtMs = configVersion.createdAtMs,
      contentHash = configVersion.contentHash,
      source = configVersion.source.name,
      abVariant = configVersion.abVariant,
      signature = configVersion.signature,
      configJson = configJson,
      isActive = isActive,
    )
  }
}
