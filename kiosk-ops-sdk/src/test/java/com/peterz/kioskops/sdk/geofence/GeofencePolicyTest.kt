/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.geofence

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for GeofencePolicy.
 *
 * Security (BSI SYS.3.2.2.A8): Validates geofence policy configuration
 * with proper validation.
 */
@RunWith(RobolectricTestRunner::class)
class GeofencePolicyTest {

  @Test
  fun `disabledDefaults has geofencing disabled`() {
    val policy = GeofencePolicy.disabledDefaults()

    assertThat(policy.enabled).isFalse()
    assertThat(policy.regions).isEmpty()
    assertThat(policy.defaultPolicyProfile).isEqualTo(PolicyProfile.DEFAULT_PROFILE_NAME)
  }

  @Test
  fun `default values are sensible`() {
    val policy = GeofencePolicy()

    assertThat(policy.dwellTimeMs).isEqualTo(30_000L)
    assertThat(policy.locationAccuracyMeters).isEqualTo(100f)
    assertThat(policy.loiteringDelayMs).isEqualTo(30_000)
    assertThat(policy.notificationResponsiveness).isEqualTo(300_000)
  }

  @Test
  fun `validates dwellTimeMs non-negative`() {
    val exception = runCatching {
      GeofencePolicy(dwellTimeMs = -1)
    }.exceptionOrNull()

    assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
    assertThat(exception?.message).contains("dwellTimeMs")
  }

  @Test
  fun `validates locationAccuracyMeters positive`() {
    val exception = runCatching {
      GeofencePolicy(locationAccuracyMeters = 0f)
    }.exceptionOrNull()

    assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
    assertThat(exception?.message).contains("locationAccuracyMeters")
  }

  @Test
  fun `validates unique region IDs`() {
    val exception = runCatching {
      GeofencePolicy(
        regions = listOf(
          GeofenceRegion("dup", 0.0, 0.0, 100f, "default"),
          GeofenceRegion("dup", 1.0, 1.0, 100f, "default"),
        ),
      )
    }.exceptionOrNull()

    assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
    assertThat(exception?.message).contains("Region IDs must be unique")
  }

  @Test
  fun `validates region radius minimum`() {
    val exception = runCatching {
      GeofencePolicy(
        regions = listOf(
          GeofenceRegion("small", 0.0, 0.0, 10f, "default"),
        ),
      )
    }.exceptionOrNull()

    assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
    assertThat(exception?.message).contains("radius must be >=")
  }

  @Test
  fun `validates region radius maximum`() {
    val exception = runCatching {
      GeofencePolicy(
        regions = listOf(
          GeofenceRegion("huge", 0.0, 0.0, 200_000f, "default"),
        ),
      )
    }.exceptionOrNull()

    assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
    assertThat(exception?.message).contains("radius must be <=")
  }

  @Test
  fun `findOverlappingRegions detects overlaps`() {
    val policy = GeofencePolicy(
      regions = listOf(
        GeofenceRegion("r1", 0.0, 0.0, 1000f, "default"),
        GeofenceRegion("r2", 0.001, 0.001, 1000f, "default"), // Very close, overlapping
        GeofenceRegion("r3", 10.0, 10.0, 100f, "default"), // Far away
      ),
    )

    val overlaps = policy.findOverlappingRegions()

    assertThat(overlaps).hasSize(1)
    assertThat(overlaps[0].first.id).isEqualTo("r1")
    assertThat(overlaps[0].second.id).isEqualTo("r2")
  }

  @Test
  fun `findOverlappingRegions returns empty for non-overlapping`() {
    val policy = GeofencePolicy(
      regions = listOf(
        GeofenceRegion("r1", 0.0, 0.0, 100f, "default"),
        GeofenceRegion("r2", 10.0, 10.0, 100f, "default"),
      ),
    )

    assertThat(policy.findOverlappingRegions()).isEmpty()
  }

  @Test
  fun `regionsByPriority sorts by priority descending`() {
    val policy = GeofencePolicy(
      regions = listOf(
        GeofenceRegion("low", 0.0, 0.0, 100f, "default", priority = 1),
        GeofenceRegion("high", 1.0, 1.0, 100f, "default", priority = 10),
        GeofenceRegion("medium", 2.0, 2.0, 100f, "default", priority = 5),
      ),
    )

    val sorted = policy.regionsByPriority()

    assertThat(sorted.map { it.id }).containsExactly("high", "medium", "low").inOrder()
  }

  @Test
  fun `findRegionAt returns highest priority matching region`() {
    val policy = GeofencePolicy(
      regions = listOf(
        GeofenceRegion("outer", 0.0, 0.0, 10000f, "outer-profile", priority = 1),
        GeofenceRegion("inner", 0.0, 0.0, 100f, "inner-profile", priority = 10),
      ),
    )

    // Point at center is in both, should get inner (higher priority)
    val region = policy.findRegionAt(0.0, 0.0)

    assertThat(region?.id).isEqualTo("inner")
  }

  @Test
  fun `findRegionAt returns null for point outside all regions`() {
    val policy = GeofencePolicy(
      regions = listOf(
        GeofenceRegion("r1", 0.0, 0.0, 100f, "default"),
      ),
    )

    val region = policy.findRegionAt(45.0, 45.0)

    assertThat(region).isNull()
  }

  @Test
  fun `MAX_REGIONS constant is 100`() {
    assertThat(GeofencePolicy.MAX_REGIONS).isEqualTo(100)
  }
}
