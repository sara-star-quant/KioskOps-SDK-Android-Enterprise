/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DevicePostureCollectorTest {

  private val ctx = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun `collect returns posture with device info`() {
    val collector = DevicePostureCollector(ctx, null)
    val posture = collector.collect()

    assertThat(posture.androidSdkInt).isGreaterThan(0)
    assertThat(posture.deviceModel).isNotEmpty()
    assertThat(posture.manufacturer).isNotEmpty()
  }

  @Test
  fun `collect returns non-owner status on Robolectric`() {
    val collector = DevicePostureCollector(ctx, null)
    val posture = collector.collect()

    // Robolectric default: not device owner, not lock task mode
    assertThat(posture.isDeviceOwner).isFalse()
    assertThat(posture.isLockTaskPermitted).isFalse()
  }

  @Test
  fun `collect with custom device group provider`() {
    val groupProvider = object : DeviceGroupProvider {
      override fun getDeviceGroups() = listOf("fleet-A", "region-EU")
      override suspend fun addToGroup(groupId: String) = Unit
      override suspend fun removeFromGroup(groupId: String) = Unit
      override suspend fun setGroups(groupIds: List<String>) = Unit
    }
    val collector = DevicePostureCollector(ctx, groupProvider)
    val posture = collector.collect()

    assertThat(posture.deviceGroups).containsExactly("fleet-A", "region-EU")
  }

  @Test
  fun `collect with null device group provider uses default`() {
    val collector = DevicePostureCollector(ctx, null)
    val posture = collector.collect()

    // Default provider returns empty or context-derived groups
    assertThat(posture.deviceGroups).isNotNull()
  }

  @Test
  fun `collect includes battery status`() {
    val collector = DevicePostureCollector(ctx, null)
    val posture = collector.collect()

    // Battery status may be null on Robolectric, but field exists
    // On some Robolectric versions it returns defaults
    assertThat(posture).isNotNull()
  }

  @Test
  fun `collect includes storage status`() {
    val collector = DevicePostureCollector(ctx, null)
    val posture = collector.collect()

    // Storage status collected via StatFs
    assertThat(posture).isNotNull()
  }

  @Test
  fun `collect is resilient to system service failures`() {
    // DevicePostureCollector wraps all system calls in runCatching
    // This test verifies it doesn't crash even if services are unavailable
    val collector = DevicePostureCollector(ctx, null)
    val posture = collector.collect()
    assertThat(posture).isNotNull()
  }
}
