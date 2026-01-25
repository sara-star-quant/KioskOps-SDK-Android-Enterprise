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
 * Tests for GeofenceRegion.
 *
 * Privacy (GDPR Art. 5): Validates geofence region handling
 * without PII storage.
 */
@RunWith(RobolectricTestRunner::class)
class GeofenceRegionTest {

  @Test
  fun `creates region with valid parameters`() {
    val region = GeofenceRegion(
      id = "store-123",
      latitude = 37.7749,
      longitude = -122.4194,
      radiusMeters = 100f,
      policyProfile = "retail",
    )

    assertThat(region.id).isEqualTo("store-123")
    assertThat(region.latitude).isEqualTo(37.7749)
    assertThat(region.longitude).isEqualTo(-122.4194)
    assertThat(region.radiusMeters).isEqualTo(100f)
    assertThat(region.policyProfile).isEqualTo("retail")
  }

  @Test
  fun `default priority is 0`() {
    val region = GeofenceRegion(
      id = "test",
      latitude = 0.0,
      longitude = 0.0,
      radiusMeters = 50f,
      policyProfile = "default",
    )

    assertThat(region.priority).isEqualTo(0)
  }

  @Test
  fun `default transition types are ENTER and EXIT`() {
    val region = GeofenceRegion(
      id = "test",
      latitude = 0.0,
      longitude = 0.0,
      radiusMeters = 50f,
      policyProfile = "default",
    )

    assertThat(region.transitionTypes).containsExactly(
      TransitionType.ENTER,
      TransitionType.EXIT,
    )
  }

  @Test
  fun `custom transition types are preserved`() {
    val region = GeofenceRegion(
      id = "test",
      latitude = 0.0,
      longitude = 0.0,
      radiusMeters = 50f,
      policyProfile = "default",
      transitionTypes = setOf(TransitionType.DWELL),
    )

    assertThat(region.transitionTypes).containsExactly(TransitionType.DWELL)
  }

  @Test
  fun `id must not be blank`() {
    val exception = runCatching {
      GeofenceRegion(
        id = "",
        latitude = 0.0,
        longitude = 0.0,
        radiusMeters = 50f,
        policyProfile = "default",
      )
    }.exceptionOrNull()

    assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
    assertThat(exception?.message).contains("Region ID must not be blank")
  }

  @Test
  fun `latitude must be valid range`() {
    val tooLow = runCatching {
      GeofenceRegion(
        id = "test",
        latitude = -91.0,
        longitude = 0.0,
        radiusMeters = 50f,
        policyProfile = "default",
      )
    }.exceptionOrNull()
    assertThat(tooLow).isInstanceOf(IllegalArgumentException::class.java)

    val tooHigh = runCatching {
      GeofenceRegion(
        id = "test",
        latitude = 91.0,
        longitude = 0.0,
        radiusMeters = 50f,
        policyProfile = "default",
      )
    }.exceptionOrNull()
    assertThat(tooHigh).isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun `longitude must be valid range`() {
    val tooLow = runCatching {
      GeofenceRegion(
        id = "test",
        latitude = 0.0,
        longitude = -181.0,
        radiusMeters = 50f,
        policyProfile = "default",
      )
    }.exceptionOrNull()
    assertThat(tooLow).isInstanceOf(IllegalArgumentException::class.java)

    val tooHigh = runCatching {
      GeofenceRegion(
        id = "test",
        latitude = 0.0,
        longitude = 181.0,
        radiusMeters = 50f,
        policyProfile = "default",
      )
    }.exceptionOrNull()
    assertThat(tooHigh).isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun `radiusMeters must be positive`() {
    val exception = runCatching {
      GeofenceRegion(
        id = "test",
        latitude = 0.0,
        longitude = 0.0,
        radiusMeters = 0f,
        policyProfile = "default",
      )
    }.exceptionOrNull()

    assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
    assertThat(exception?.message).contains("Radius must be positive")
  }

  @Test
  fun `policyProfile must not be blank`() {
    val exception = runCatching {
      GeofenceRegion(
        id = "test",
        latitude = 0.0,
        longitude = 0.0,
        radiusMeters = 50f,
        policyProfile = "",
      )
    }.exceptionOrNull()

    assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
    assertThat(exception?.message).contains("Policy profile must not be blank")
  }

  @Test
  fun `contains returns true for point inside radius`() {
    // San Francisco coordinates
    val region = GeofenceRegion(
      id = "sf",
      latitude = 37.7749,
      longitude = -122.4194,
      radiusMeters = 1000f, // 1km radius
      policyProfile = "default",
    )

    // Point ~500m away (within radius)
    assertThat(region.contains(37.7790, -122.4194)).isTrue()
  }

  @Test
  fun `contains returns false for point outside radius`() {
    val region = GeofenceRegion(
      id = "sf",
      latitude = 37.7749,
      longitude = -122.4194,
      radiusMeters = 100f, // 100m radius
      policyProfile = "default",
    )

    // Point ~5km away (outside radius)
    assertThat(region.contains(37.8199, -122.4194)).isFalse()
  }

  @Test
  fun `haversineDistance calculates correctly`() {
    // San Francisco to Oakland (~10km)
    val distance = GeofenceRegion.haversineDistance(
      37.7749, -122.4194, // San Francisco
      37.8044, -122.2712, // Oakland
    )

    // Should be approximately 10-15km
    assertThat(distance).isAtLeast(10_000.0)
    assertThat(distance).isAtMost(20_000.0)
  }

  @Test
  fun `haversineDistance returns 0 for same point`() {
    val distance = GeofenceRegion.haversineDistance(
      37.7749, -122.4194,
      37.7749, -122.4194,
    )

    assertThat(distance).isWithin(0.001).of(0.0)
  }

  @Test
  fun `TransitionType has all expected values`() {
    assertThat(TransitionType.values()).hasLength(3)
    assertThat(TransitionType.values()).asList().containsExactly(
      TransitionType.ENTER,
      TransitionType.EXIT,
      TransitionType.DWELL,
    )
  }

  @Test
  fun `displayName is optional`() {
    val withoutName = GeofenceRegion(
      id = "test",
      latitude = 0.0,
      longitude = 0.0,
      radiusMeters = 50f,
      policyProfile = "default",
    )
    assertThat(withoutName.displayName).isNull()

    val withName = GeofenceRegion(
      id = "test",
      latitude = 0.0,
      longitude = 0.0,
      radiusMeters = 50f,
      policyProfile = "default",
      displayName = "Test Region",
    )
    assertThat(withName.displayName).isEqualTo("Test Region")
  }
}
