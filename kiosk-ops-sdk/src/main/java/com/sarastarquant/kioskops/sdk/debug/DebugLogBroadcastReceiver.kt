/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.sarastarquant.kioskops.sdk.KioskOpsSdk

/**
 * Runtime debug log level toggle via ADB broadcast intent.
 *
 * Usage for field technicians:
 * ```
 * adb shell am broadcast -a com.sarastarquant.kioskops.sdk.SET_LOG_LEVEL --es level VERBOSE
 * adb shell am broadcast -a com.sarastarquant.kioskops.sdk.SET_LOG_LEVEL --es level OFF
 * ```
 *
 * Security (ISO 27001 A.14.2): Only functional in debug builds.
 * The receiver checks [DebugUtils.isDebugBuild] before acting.
 *
 * Register with [register] during SDK initialization in debug builds.
 *
 * @since 0.7.0
 */
@RequiresDebugBuild
class DebugLogBroadcastReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    if (!DebugUtils.isDebugBuild) return

    val level = intent.getStringExtra("level")?.uppercase() ?: return
    val validLevels = setOf("VERBOSE", "DEBUG", "INFO", "WARN", "ERROR", "OFF")
    if (level in validLevels) {
      currentLevel = level
      Log.i(TAG, "Log level set to $level via ADB broadcast")
      KioskOpsSdk.getOrNull()?.logger()?.i("DebugLog", "Log level changed to $level via ADB")
    } else {
      Log.w(TAG, "Invalid log level: $level. Valid: $validLevels")
    }
  }

  companion object {
    const val ACTION = "com.sarastarquant.kioskops.sdk.SET_LOG_LEVEL"
    private const val TAG = "KioskOpsDebugLog"

    @Volatile
    var currentLevel: String = "INFO"
      private set

    fun intentFilter(): IntentFilter = IntentFilter(ACTION)

    /**
     * Register the receiver on the given context.
     * Only registers in debug builds; no-op in release.
     */
    fun register(context: Context): DebugLogBroadcastReceiver? {
      if (!DebugUtils.isDebugBuild) return null
      val receiver = DebugLogBroadcastReceiver()
      context.registerReceiver(receiver, intentFilter(), Context.RECEIVER_NOT_EXPORTED)
      Log.i(TAG, "Registered debug log level broadcast receiver")
      return receiver
    }
  }
}
