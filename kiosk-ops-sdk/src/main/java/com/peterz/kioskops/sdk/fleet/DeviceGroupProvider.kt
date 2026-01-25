/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet

import android.content.Context
import android.content.RestrictionsManager
import android.content.SharedPreferences
import androidx.core.content.getSystemService

/**
 * Manages device group assignments for fleet segmentation.
 *
 * Groups are opaque string identifiers assigned via:
 * 1. Managed configuration (kioskops_device_groups)
 * 2. Programmatic API (for dynamic assignment)
 *
 * Privacy (GDPR): Group IDs should not contain PII. Use opaque identifiers
 * like "region-eu-west", "pilot-batch-3", "store-type-flagship".
 *
 * Security (ISO 27001 A.8.1): Groups support asset classification and access control.
 */
interface DeviceGroupProvider {
  /**
   * Get current device group assignments.
   * Merges managed config groups with local assignments.
   */
  fun getDeviceGroups(): List<String>

  /**
   * Add device to a group (persisted locally).
   */
  suspend fun addToGroup(groupId: String)

  /**
   * Remove device from a group.
   */
  suspend fun removeFromGroup(groupId: String)

  /**
   * Replace all local group assignments.
   */
  suspend fun setGroups(groupIds: List<String>)

  /**
   * Check if device is in a specific group.
   */
  fun isInGroup(groupId: String): Boolean = getDeviceGroups().contains(groupId)
}

/**
 * Default implementation using SharedPreferences and managed config merge.
 */
class DefaultDeviceGroupProvider(
  private val context: Context,
) : DeviceGroupProvider {

  private val prefs: SharedPreferences by lazy {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  override fun getDeviceGroups(): List<String> {
    val managedGroups = getManagedConfigGroups()
    val localGroups = getLocalGroups()
    return (managedGroups + localGroups).distinct().sorted()
  }

  override suspend fun addToGroup(groupId: String) {
    val current = getLocalGroups().toMutableSet()
    current.add(groupId)
    prefs.edit().putStringSet(KEY_LOCAL_GROUPS, current).apply()
  }

  override suspend fun removeFromGroup(groupId: String) {
    val current = getLocalGroups().toMutableSet()
    current.remove(groupId)
    prefs.edit().putStringSet(KEY_LOCAL_GROUPS, current).apply()
  }

  override suspend fun setGroups(groupIds: List<String>) {
    prefs.edit().putStringSet(KEY_LOCAL_GROUPS, groupIds.toSet()).apply()
  }

  private fun getManagedConfigGroups(): List<String> {
    val restrictionsManager = context.getSystemService<RestrictionsManager>()
    val bundle = restrictionsManager?.applicationRestrictions ?: return emptyList()
    return bundle.getStringArray(ManagedConfigReader.Keys.DEVICE_GROUPS)?.toList()
      ?: emptyList()
  }

  private fun getLocalGroups(): Set<String> {
    return prefs.getStringSet(KEY_LOCAL_GROUPS, emptySet()) ?: emptySet()
  }

  companion object {
    private const val PREFS_NAME = "kioskops_device_groups"
    private const val KEY_LOCAL_GROUPS = "local_groups"
  }
}
