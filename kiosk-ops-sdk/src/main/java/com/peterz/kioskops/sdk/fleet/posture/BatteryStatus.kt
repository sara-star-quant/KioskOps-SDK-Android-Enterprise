/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet.posture

import kotlinx.serialization.Serializable

/**
 * Battery status snapshot for fleet operations.
 *
 * Privacy (GDPR): Contains no PII. Only aggregate device status.
 * Power Efficiency: Collected via sticky broadcast, no wake lock.
 *
 * @property levelPercent Battery level 0-100
 * @property status CHARGING, DISCHARGING, FULL, NOT_CHARGING, UNKNOWN
 * @property pluggedType AC, USB, WIRELESS, NONE
 * @property health Battery health: GOOD, OVERHEAT, DEAD, UNKNOWN
 * @property isBatterySaverOn True if battery saver mode is active
 * @property estimatedMinutesRemaining Minutes until empty (null if charging)
 */
@Serializable
data class BatteryStatus(
  val levelPercent: Int,
  val status: String,
  val pluggedType: String?,
  val health: String,
  val isBatterySaverOn: Boolean,
  val estimatedMinutesRemaining: Int? = null,
) {
  /**
   * Check if battery is in a critical state.
   */
  val isCritical: Boolean
    get() = levelPercent < 10 && status == "DISCHARGING"

  /**
   * Check if battery is in a low state.
   */
  val isLow: Boolean
    get() = levelPercent < 20 && status == "DISCHARGING"

  companion object {
    const val STATUS_CHARGING = "CHARGING"
    const val STATUS_DISCHARGING = "DISCHARGING"
    const val STATUS_FULL = "FULL"
    const val STATUS_NOT_CHARGING = "NOT_CHARGING"
    const val STATUS_UNKNOWN = "UNKNOWN"

    const val PLUGGED_AC = "AC"
    const val PLUGGED_USB = "USB"
    const val PLUGGED_WIRELESS = "WIRELESS"
    const val PLUGGED_NONE = "NONE"

    const val HEALTH_GOOD = "GOOD"
    const val HEALTH_OVERHEAT = "OVERHEAT"
    const val HEALTH_DEAD = "DEAD"
    const val HEALTH_UNKNOWN = "UNKNOWN"
  }
}
