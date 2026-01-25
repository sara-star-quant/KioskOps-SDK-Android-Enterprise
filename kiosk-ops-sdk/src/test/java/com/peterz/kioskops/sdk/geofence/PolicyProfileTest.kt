/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.geofence

import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.KioskOpsConfig
import com.peterz.kioskops.sdk.sync.SyncPolicy
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for PolicyProfile.
 *
 * Validates policy profile merging for geofence-based configuration.
 */
@RunWith(RobolectricTestRunner::class)
class PolicyProfileTest {

  @Test
  fun `creates profile with name`() {
    val profile = PolicyProfile(name = "test-profile")

    assertThat(profile.name).isEqualTo("test-profile")
    assertThat(profile.syncPolicy).isNull()
    assertThat(profile.telemetryPolicy).isNull()
    assertThat(profile.diagnosticsSchedulePolicy).isNull()
    assertThat(profile.description).isNull()
  }

  @Test
  fun `name must not be blank`() {
    val exception = runCatching {
      PolicyProfile(name = "")
    }.exceptionOrNull()

    assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
    assertThat(exception?.message).contains("Profile name must not be blank")
  }

  @Test
  fun `applyTo overrides only non-null policies`() {
    val baseConfig = KioskOpsConfig(
      baseUrl = "https://api.example.com",
      locationId = "loc-123",
      kioskEnabled = true,
      syncPolicy = SyncPolicy.disabledDefaults(),
    )

    val enabledSync = SyncPolicy(enabled = true)
    val profile = PolicyProfile(
      name = "high-sync",
      syncPolicy = enabledSync,
    )

    val modified = profile.applyTo(baseConfig)

    assertThat(modified.syncPolicy).isEqualTo(enabledSync)
    // Other policies unchanged
    assertThat(modified.telemetryPolicy).isEqualTo(baseConfig.telemetryPolicy)
    assertThat(modified.diagnosticsSchedulePolicy).isEqualTo(baseConfig.diagnosticsSchedulePolicy)
    // Non-policy fields unchanged
    assertThat(modified.baseUrl).isEqualTo(baseConfig.baseUrl)
    assertThat(modified.locationId).isEqualTo(baseConfig.locationId)
  }

  @Test
  fun `applyTo with no overrides returns equivalent config`() {
    val baseConfig = KioskOpsConfig(
      baseUrl = "https://api.example.com",
      locationId = "loc-123",
      kioskEnabled = true,
    )

    val profile = PolicyProfile(name = "no-overrides")
    val modified = profile.applyTo(baseConfig)

    assertThat(modified.syncPolicy).isEqualTo(baseConfig.syncPolicy)
    assertThat(modified.telemetryPolicy).isEqualTo(baseConfig.telemetryPolicy)
    assertThat(modified.diagnosticsSchedulePolicy).isEqualTo(baseConfig.diagnosticsSchedulePolicy)
  }

  @Test
  fun `hasOverrides returns true when policies set`() {
    val withOverride = PolicyProfile(
      name = "with-override",
      syncPolicy = SyncPolicy(enabled = true),
    )
    assertThat(withOverride.hasOverrides()).isTrue()

    val withoutOverride = PolicyProfile(name = "without-override")
    assertThat(withoutOverride.hasOverrides()).isFalse()
  }

  @Test
  fun `DEFAULT_PROFILE_NAME is 'default'`() {
    assertThat(PolicyProfile.DEFAULT_PROFILE_NAME).isEqualTo("default")
  }

  @Test
  fun `highConnectivity creates sync-focused profile`() {
    val profile = PolicyProfile.highConnectivity("fast-sync")

    assertThat(profile.name).isEqualTo("fast-sync")
    assertThat(profile.syncPolicy).isNotNull()
    assertThat(profile.syncPolicy?.enabled).isTrue()
    assertThat(profile.syncPolicy?.batchSize).isEqualTo(100)
    assertThat(profile.description).contains("connectivity")
  }

  @Test
  fun `batterySaver creates reduced sync profile`() {
    val profile = PolicyProfile.batterySaver("save-battery")

    assertThat(profile.name).isEqualTo("save-battery")
    assertThat(profile.syncPolicy).isNotNull()
    assertThat(profile.syncPolicy?.enabled).isTrue()
    assertThat(profile.syncPolicy?.requireUnmeteredNetwork).isTrue()
    assertThat(profile.description).contains("Battery")
  }

  @Test
  fun `offlineFirst creates minimal sync profile`() {
    val profile = PolicyProfile.offlineFirst("offline")

    assertThat(profile.name).isEqualTo("offline")
    assertThat(profile.syncPolicy).isNotNull()
    assertThat(profile.syncPolicy?.enabled).isTrue()
    assertThat(profile.syncPolicy?.requireUnmeteredNetwork).isTrue()
  }

  @Test
  fun `description is preserved`() {
    val profile = PolicyProfile(
      name = "documented",
      description = "A well-documented profile",
    )

    assertThat(profile.description).isEqualTo("A well-documented profile")
  }
}
