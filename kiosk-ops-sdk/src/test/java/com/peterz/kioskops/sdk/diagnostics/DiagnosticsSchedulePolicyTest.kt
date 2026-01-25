/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for DiagnosticsSchedulePolicy.
 *
 * Security (BSI APP.4.4.A7): Validates policy defaults for scheduled diagnostics.
 */
@RunWith(RobolectricTestRunner::class)
class DiagnosticsSchedulePolicyTest {

  @Test
  fun `default policy is disabled`() {
    val policy = DiagnosticsSchedulePolicy()
    assertThat(policy.scheduledEnabled).isFalse()
    assertThat(policy.remoteTriggerEnabled).isFalse()
    assertThat(policy.autoUploadEnabled).isFalse()
  }

  @Test
  fun `disabledDefaults returns disabled policy`() {
    val policy = DiagnosticsSchedulePolicy.disabledDefaults()
    assertThat(policy.scheduledEnabled).isFalse()
    assertThat(policy.remoteTriggerEnabled).isFalse()
  }

  @Test
  fun `enterpriseDefaults enables daily schedule`() {
    val policy = DiagnosticsSchedulePolicy.enterpriseDefaults()
    assertThat(policy.scheduledEnabled).isTrue()
    assertThat(policy.scheduleType).isEqualTo(DiagnosticsSchedulePolicy.ScheduleType.DAILY)
    assertThat(policy.scheduleHour).isEqualTo(3) // 3 AM - low activity
    assertThat(policy.remoteTriggerEnabled).isTrue()
    assertThat(policy.maxRemoteTriggersPerDay).isEqualTo(3)
    assertThat(policy.remoteTriggerCooldownMs).isEqualTo(3_600_000L) // 1 hour
    assertThat(policy.autoUploadEnabled).isFalse()
    assertThat(policy.includeExtendedPosture).isTrue()
  }

  @Test
  fun `weeklyDefaults enables weekly schedule`() {
    val policy = DiagnosticsSchedulePolicy.weeklyDefaults()
    assertThat(policy.scheduledEnabled).isTrue()
    assertThat(policy.scheduleType).isEqualTo(DiagnosticsSchedulePolicy.ScheduleType.WEEKLY)
    assertThat(policy.scheduleHour).isEqualTo(2) // 2 AM
    assertThat(policy.scheduleDayOfWeek).isEqualTo(1) // Monday
    assertThat(policy.remoteTriggerEnabled).isTrue()
    assertThat(policy.maxRemoteTriggersPerDay).isEqualTo(5)
  }

  @Test
  fun `scheduleHour valid range 0-23`() {
    for (hour in 0..23) {
      val policy = DiagnosticsSchedulePolicy(scheduleHour = hour)
      assertThat(policy.scheduleHour).isEqualTo(hour)
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun `scheduleHour negative throws`() {
    DiagnosticsSchedulePolicy(scheduleHour = -1)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `scheduleHour above 23 throws`() {
    DiagnosticsSchedulePolicy(scheduleHour = 24)
  }

  @Test
  fun `scheduleDayOfWeek valid range 1-7`() {
    for (day in 1..7) {
      val policy = DiagnosticsSchedulePolicy(scheduleDayOfWeek = day)
      assertThat(policy.scheduleDayOfWeek).isEqualTo(day)
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun `scheduleDayOfWeek zero throws`() {
    DiagnosticsSchedulePolicy(scheduleDayOfWeek = 0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `scheduleDayOfWeek above 7 throws`() {
    DiagnosticsSchedulePolicy(scheduleDayOfWeek = 8)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `negative maxRemoteTriggersPerDay throws`() {
    DiagnosticsSchedulePolicy(maxRemoteTriggersPerDay = -1)
  }

  @Test
  fun `zero maxRemoteTriggersPerDay allowed`() {
    val policy = DiagnosticsSchedulePolicy(maxRemoteTriggersPerDay = 0)
    assertThat(policy.maxRemoteTriggersPerDay).isEqualTo(0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `negative remoteTriggerCooldownMs throws`() {
    DiagnosticsSchedulePolicy(remoteTriggerCooldownMs = -1)
  }

  @Test
  fun `zero remoteTriggerCooldownMs allowed`() {
    val policy = DiagnosticsSchedulePolicy(remoteTriggerCooldownMs = 0)
    assertThat(policy.remoteTriggerCooldownMs).isEqualTo(0)
  }

  @Test
  fun `ScheduleType has expected values`() {
    val types = DiagnosticsSchedulePolicy.ScheduleType.values()
    assertThat(types).hasLength(2)
    assertThat(types).asList().containsExactly(
      DiagnosticsSchedulePolicy.ScheduleType.DAILY,
      DiagnosticsSchedulePolicy.ScheduleType.WEEKLY,
    )
  }

  @Test
  fun `custom policy with all fields`() {
    val policy = DiagnosticsSchedulePolicy(
      scheduledEnabled = true,
      scheduleType = DiagnosticsSchedulePolicy.ScheduleType.WEEKLY,
      scheduleHour = 4,
      scheduleDayOfWeek = 5, // Friday
      remoteTriggerEnabled = true,
      maxRemoteTriggersPerDay = 10,
      remoteTriggerCooldownMs = 1_800_000L, // 30 minutes
      autoUploadEnabled = true,
      includeExtendedPosture = false,
    )

    assertThat(policy.scheduledEnabled).isTrue()
    assertThat(policy.scheduleType).isEqualTo(DiagnosticsSchedulePolicy.ScheduleType.WEEKLY)
    assertThat(policy.scheduleHour).isEqualTo(4)
    assertThat(policy.scheduleDayOfWeek).isEqualTo(5)
    assertThat(policy.remoteTriggerEnabled).isTrue()
    assertThat(policy.maxRemoteTriggersPerDay).isEqualTo(10)
    assertThat(policy.remoteTriggerCooldownMs).isEqualTo(1_800_000L)
    assertThat(policy.autoUploadEnabled).isTrue()
    assertThat(policy.includeExtendedPosture).isFalse()
  }
}
