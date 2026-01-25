/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet.posture

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for BatteryStatus.
 *
 * Security (BSI SYS.3.2.2.A8): Validates battery monitoring for power management.
 * Privacy (GDPR): Verifies no PII in battery data.
 */
@RunWith(RobolectricTestRunner::class)
class BatteryStatusTest {

  @Test
  fun `BatteryStatus contains all fields`() {
    val status = BatteryStatus(
      levelPercent = 75,
      status = BatteryStatus.STATUS_CHARGING,
      pluggedType = BatteryStatus.PLUGGED_AC,
      health = BatteryStatus.HEALTH_GOOD,
      isBatterySaverOn = false,
      estimatedMinutesRemaining = 120,
    )

    assertThat(status.levelPercent).isEqualTo(75)
    assertThat(status.status).isEqualTo("CHARGING")
    assertThat(status.pluggedType).isEqualTo("AC")
    assertThat(status.health).isEqualTo("GOOD")
    assertThat(status.isBatterySaverOn).isFalse()
    assertThat(status.estimatedMinutesRemaining).isEqualTo(120)
  }

  @Test
  fun `isCritical returns true when discharging below 10 percent`() {
    val critical = BatteryStatus(
      levelPercent = 9,
      status = BatteryStatus.STATUS_DISCHARGING,
      pluggedType = BatteryStatus.PLUGGED_NONE,
      health = BatteryStatus.HEALTH_GOOD,
      isBatterySaverOn = false,
    )
    assertThat(critical.isCritical).isTrue()

    val notCritical = BatteryStatus(
      levelPercent = 10,
      status = BatteryStatus.STATUS_DISCHARGING,
      pluggedType = BatteryStatus.PLUGGED_NONE,
      health = BatteryStatus.HEALTH_GOOD,
      isBatterySaverOn = false,
    )
    assertThat(notCritical.isCritical).isFalse()
  }

  @Test
  fun `isCritical returns false when charging even at low level`() {
    val chargingLow = BatteryStatus(
      levelPercent = 5,
      status = BatteryStatus.STATUS_CHARGING,
      pluggedType = BatteryStatus.PLUGGED_USB,
      health = BatteryStatus.HEALTH_GOOD,
      isBatterySaverOn = false,
    )
    assertThat(chargingLow.isCritical).isFalse()
  }

  @Test
  fun `isLow returns true when discharging below 20 percent`() {
    val low = BatteryStatus(
      levelPercent = 19,
      status = BatteryStatus.STATUS_DISCHARGING,
      pluggedType = BatteryStatus.PLUGGED_NONE,
      health = BatteryStatus.HEALTH_GOOD,
      isBatterySaverOn = false,
    )
    assertThat(low.isLow).isTrue()

    val notLow = BatteryStatus(
      levelPercent = 20,
      status = BatteryStatus.STATUS_DISCHARGING,
      pluggedType = BatteryStatus.PLUGGED_NONE,
      health = BatteryStatus.HEALTH_GOOD,
      isBatterySaverOn = false,
    )
    assertThat(notLow.isLow).isFalse()
  }

  @Test
  fun `status constants are defined`() {
    assertThat(BatteryStatus.STATUS_CHARGING).isEqualTo("CHARGING")
    assertThat(BatteryStatus.STATUS_DISCHARGING).isEqualTo("DISCHARGING")
    assertThat(BatteryStatus.STATUS_FULL).isEqualTo("FULL")
    assertThat(BatteryStatus.STATUS_NOT_CHARGING).isEqualTo("NOT_CHARGING")
    assertThat(BatteryStatus.STATUS_UNKNOWN).isEqualTo("UNKNOWN")
  }

  @Test
  fun `plugged type constants are defined`() {
    assertThat(BatteryStatus.PLUGGED_AC).isEqualTo("AC")
    assertThat(BatteryStatus.PLUGGED_USB).isEqualTo("USB")
    assertThat(BatteryStatus.PLUGGED_WIRELESS).isEqualTo("WIRELESS")
    assertThat(BatteryStatus.PLUGGED_NONE).isEqualTo("NONE")
  }

  @Test
  fun `health constants are defined`() {
    assertThat(BatteryStatus.HEALTH_GOOD).isEqualTo("GOOD")
    assertThat(BatteryStatus.HEALTH_OVERHEAT).isEqualTo("OVERHEAT")
    assertThat(BatteryStatus.HEALTH_DEAD).isEqualTo("DEAD")
    assertThat(BatteryStatus.HEALTH_UNKNOWN).isEqualTo("UNKNOWN")
  }

  @Test
  fun `estimatedMinutesRemaining is null by default`() {
    val status = BatteryStatus(
      levelPercent = 50,
      status = BatteryStatus.STATUS_CHARGING,
      pluggedType = BatteryStatus.PLUGGED_AC,
      health = BatteryStatus.HEALTH_GOOD,
      isBatterySaverOn = false,
    )
    assertThat(status.estimatedMinutesRemaining).isNull()
  }

  @Test
  fun `copy preserves unchanged fields`() {
    val original = BatteryStatus(
      levelPercent = 75,
      status = BatteryStatus.STATUS_CHARGING,
      pluggedType = BatteryStatus.PLUGGED_AC,
      health = BatteryStatus.HEALTH_GOOD,
      isBatterySaverOn = false,
    )

    val modified = original.copy(levelPercent = 80)

    assertThat(modified.levelPercent).isEqualTo(80)
    assertThat(modified.status).isEqualTo(BatteryStatus.STATUS_CHARGING)
    assertThat(modified.pluggedType).isEqualTo(BatteryStatus.PLUGGED_AC)
    assertThat(modified.health).isEqualTo(BatteryStatus.HEALTH_GOOD)
  }
}
