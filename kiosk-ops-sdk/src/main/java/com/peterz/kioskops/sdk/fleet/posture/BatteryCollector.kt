/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet.posture

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.getSystemService

/**
 * Collects battery status with minimal power impact.
 *
 * Power Efficiency (BSI SYS.3.2.2.A8):
 * - Uses sticky broadcast - no wake lock required
 * - No background registration - on-demand collection only
 * - Safe error handling with graceful degradation
 */
internal class BatteryCollector(private val context: Context) {

  /**
   * Collect current battery status.
   *
   * @return BatteryStatus or null if collection fails
   */
  fun collect(): BatteryStatus? = runCatching {
    val batteryManager = context.getSystemService<BatteryManager>()
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val powerManager = context.getSystemService<PowerManager>()

    val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
    val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1

    BatteryStatus(
      levelPercent = level.coerceIn(0, 100),
      status = mapStatus(status),
      pluggedType = mapPlugged(plugged),
      health = mapHealth(health),
      isBatterySaverOn = powerManager?.isPowerSaveMode == true,
      estimatedMinutesRemaining = computeTimeRemaining(batteryManager, status),
    )
  }.getOrNull()

  private fun mapStatus(status: Int): String = when (status) {
    BatteryManager.BATTERY_STATUS_CHARGING -> BatteryStatus.STATUS_CHARGING
    BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryStatus.STATUS_DISCHARGING
    BatteryManager.BATTERY_STATUS_FULL -> BatteryStatus.STATUS_FULL
    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryStatus.STATUS_NOT_CHARGING
    else -> BatteryStatus.STATUS_UNKNOWN
  }

  private fun mapPlugged(plugged: Int): String? = when (plugged) {
    BatteryManager.BATTERY_PLUGGED_AC -> BatteryStatus.PLUGGED_AC
    BatteryManager.BATTERY_PLUGGED_USB -> BatteryStatus.PLUGGED_USB
    BatteryManager.BATTERY_PLUGGED_WIRELESS -> BatteryStatus.PLUGGED_WIRELESS
    0 -> BatteryStatus.PLUGGED_NONE
    else -> null
  }

  private fun mapHealth(health: Int): String = when (health) {
    BatteryManager.BATTERY_HEALTH_GOOD -> BatteryStatus.HEALTH_GOOD
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryStatus.HEALTH_OVERHEAT
    BatteryManager.BATTERY_HEALTH_DEAD -> BatteryStatus.HEALTH_DEAD
    else -> BatteryStatus.HEALTH_UNKNOWN
  }

  private fun computeTimeRemaining(batteryManager: BatteryManager?, status: Int): Int? {
    if (status == BatteryManager.BATTERY_STATUS_CHARGING) return null
    if (batteryManager == null) return null

    // Get remaining energy in microampere-hours
    val chargeRemaining = batteryManager.getLongProperty(
      BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER
    ).takeIf { it > 0 } ?: return null

    // Get average current draw in microamperes (negative when discharging)
    val avgCurrent = batteryManager.getLongProperty(
      BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE
    ).takeIf { it < 0 } ?: return null

    // Calculate hours remaining and convert to minutes
    val hoursRemaining = chargeRemaining.toDouble() / (-avgCurrent)
    return (hoursRemaining * 60).toInt().coerceAtLeast(0)
  }
}
