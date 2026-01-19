package com.peterz.kioskops.sdk.util

import android.content.Context
import java.util.UUID

object DeviceId {
  private const val PREFS = "kioskops_ids"
  private const val KEY = "sdk_device_id"

  fun get(context: Context): String {
    val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val existing = sp.getString(KEY, null)
    if (!existing.isNullOrBlank()) return existing
    val created = UUID.randomUUID().toString()
    sp.edit().putString(KEY, created).apply()
    return created
  }

  /**
   * Resets the SDK-local device identifier.
   * Useful for certain data-rights workflows.
   */
  fun reset(context: Context): String {
    val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val created = UUID.randomUUID().toString()
    sp.edit().putString(KEY, created).apply()
    return created
  }
}
