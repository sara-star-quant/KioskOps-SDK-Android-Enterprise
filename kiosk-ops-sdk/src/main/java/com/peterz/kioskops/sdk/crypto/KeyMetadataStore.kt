/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.crypto

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent storage for key version metadata.
 *
 * Tracks key creation timestamps and version lifecycle to support
 * key rotation and backward-compatible decryption.
 *
 * @property context Android context for SharedPreferences access.
 * @property baseAlias Base alias for the key family (e.g., "kioskops_queue").
 */
class KeyMetadataStore(
  private val context: Context,
  private val baseAlias: String,
) {
  private val prefs = context.getSharedPreferences(
    "kioskops_key_metadata_$baseAlias",
    Context.MODE_PRIVATE
  )

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  private companion object {
    const val KEY_CURRENT_VERSION = "current_version"
    const val KEY_METADATA_PREFIX = "key_meta_v"
  }

  /**
   * Get metadata for a specific key version.
   *
   * @param version The key version number.
   * @return Metadata for the key, or null if not found.
   */
  fun getKeyMetadata(version: Int): KeyMetadata? {
    val metaJson = prefs.getString("$KEY_METADATA_PREFIX$version", null) ?: return null
    return try {
      json.decodeFromString<KeyMetadata>(metaJson)
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Store metadata for a key version.
   *
   * @param version The key version number.
   * @param metadata The metadata to store.
   */
  fun setKeyMetadata(version: Int, metadata: KeyMetadata) {
    prefs.edit()
      .putString("$KEY_METADATA_PREFIX$version", json.encodeToString(metadata))
      .apply()
  }

  /**
   * Get the current (active) key version.
   *
   * @return The current version number, or 1 if not set.
   */
  fun getCurrentVersion(): Int {
    return prefs.getInt(KEY_CURRENT_VERSION, 1)
  }

  /**
   * Set the current (active) key version.
   *
   * @param version The new current version.
   */
  fun setCurrentVersion(version: Int) {
    prefs.edit().putInt(KEY_CURRENT_VERSION, version).apply()
  }

  /**
   * Get all known key versions.
   *
   * @return List of version numbers with stored metadata.
   */
  fun getAllVersions(): List<Int> {
    return prefs.all.keys
      .filter { it.startsWith(KEY_METADATA_PREFIX) }
      .mapNotNull { it.removePrefix(KEY_METADATA_PREFIX).toIntOrNull() }
      .sorted()
  }

  /**
   * Delete metadata for a key version.
   *
   * Note: This only removes the metadata, not the actual key from Keystore.
   *
   * @param version The version to delete.
   */
  fun deleteKeyMetadata(version: Int) {
    prefs.edit()
      .remove("$KEY_METADATA_PREFIX$version")
      .apply()
  }

  /**
   * Initialize metadata for version 1 if not already present.
   *
   * Called during first SDK initialization to ensure there's always
   * a valid key metadata entry.
   *
   * @param nowMs Current timestamp in milliseconds.
   */
  fun initializeIfNeeded(nowMs: Long) {
    if (getKeyMetadata(1) == null) {
      setKeyMetadata(
        version = 1,
        metadata = KeyMetadata(
          version = 1,
          createdAtMs = nowMs,
          algorithm = "AES/GCM/NoPadding",
          keyLengthBits = 256,
        )
      )
      setCurrentVersion(1)
    }
  }

  /**
   * Get metadata for the current key version.
   */
  fun getCurrentKeyMetadata(): KeyMetadata? {
    return getKeyMetadata(getCurrentVersion())
  }

  /**
   * Calculate the age of the current key in days.
   *
   * @param nowMs Current timestamp in milliseconds.
   * @return Age in days, or null if metadata not found.
   */
  fun getCurrentKeyAgeDays(nowMs: Long): Int? {
    val metadata = getCurrentKeyMetadata() ?: return null
    val ageMs = nowMs - metadata.createdAtMs
    return (ageMs / (24 * 60 * 60 * 1000)).toInt()
  }
}

/**
 * Metadata for an encryption key version.
 *
 * @property version The key version number.
 * @property createdAtMs Timestamp when the key was created (epoch milliseconds).
 * @property algorithm The encryption algorithm (e.g., "AES/GCM/NoPadding").
 * @property keyLengthBits The key length in bits.
 * @property rotatedFromVersion If this key was created by rotation, the previous version.
 * @property isHardwareBacked Whether the key is stored in hardware-backed keystore.
 */
@Serializable
data class KeyMetadata(
  val version: Int,
  val createdAtMs: Long,
  val algorithm: String,
  val keyLengthBits: Int,
  val rotatedFromVersion: Int? = null,
  val isHardwareBacked: Boolean? = null,
)
