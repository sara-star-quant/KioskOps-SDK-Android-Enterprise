/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.geofence

/**
 * Policy for geofence-aware configuration switching.
 *
 * Privacy (GDPR Art. 5): Location is processed locally only.
 * No coordinates are stored or transmitted. Only region membership
 * state is tracked for policy switching.
 *
 * Security (BSI SYS.3.2.2.A8): Uses passive location providers
 * to minimize battery impact.
 *
 * @property enabled Enable geofence monitoring
 * @property regions Defined geofence regions
 * @property defaultPolicyProfile Default policy when outside all regions
 * @property dwellTimeMs Time required inside region before triggering dwell (ms)
 * @property locationAccuracyMeters Minimum location accuracy required
 * @property loiteringDelayMs Delay before DWELL transition fires
 * @property notificationResponsiveness How quickly to receive transitions (ms)
 *
 * @since 0.4.0
 */
data class GeofencePolicy(
  val enabled: Boolean = false,
  val regions: List<GeofenceRegion> = emptyList(),
  val defaultPolicyProfile: String = PolicyProfile.DEFAULT_PROFILE_NAME,
  val dwellTimeMs: Long = 30_000L,
  val locationAccuracyMeters: Float = 100f,
  val loiteringDelayMs: Int = 30_000,
  val notificationResponsiveness: Int = 300_000, // 5 minutes
) {
  init {
    require(dwellTimeMs >= 0) { "dwellTimeMs must be non-negative" }
    require(locationAccuracyMeters > 0) { "locationAccuracyMeters must be positive" }
    require(loiteringDelayMs >= 0) { "loiteringDelayMs must be non-negative" }
    require(notificationResponsiveness >= 0) { "notificationResponsiveness must be non-negative" }

    // Validate unique region IDs
    val regionIds = regions.map { it.id }
    require(regionIds.distinct().size == regionIds.size) {
      "Region IDs must be unique, found duplicates: ${regionIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys}"
    }

    // Validate region radii
    regions.forEach { region ->
      require(region.radiusMeters >= GeofenceRegion.MIN_RADIUS_METERS) {
        "Region ${region.id} radius must be >= ${GeofenceRegion.MIN_RADIUS_METERS}m"
      }
      require(region.radiusMeters <= GeofenceRegion.MAX_RADIUS_METERS) {
        "Region ${region.id} radius must be <= ${GeofenceRegion.MAX_RADIUS_METERS}m"
      }
    }
  }

  /**
   * Find regions that overlap (centers within each other's radius).
   * Useful for detecting configuration issues.
   */
  fun findOverlappingRegions(): List<Pair<GeofenceRegion, GeofenceRegion>> {
    val overlaps = mutableListOf<Pair<GeofenceRegion, GeofenceRegion>>()
    for (i in regions.indices) {
      for (j in i + 1 until regions.size) {
        val r1 = regions[i]
        val r2 = regions[j]
        val distance = GeofenceRegion.haversineDistance(
          r1.latitude, r1.longitude,
          r2.latitude, r2.longitude,
        )
        if (distance < r1.radiusMeters + r2.radiusMeters) {
          overlaps.add(r1 to r2)
        }
      }
    }
    return overlaps
  }

  /**
   * Get regions sorted by priority (highest first).
   */
  fun regionsByPriority(): List<GeofenceRegion> {
    return regions.sortedByDescending { it.priority }
  }

  /**
   * Find the highest-priority region containing a point.
   */
  fun findRegionAt(latitude: Double, longitude: Double): GeofenceRegion? {
    return regionsByPriority().firstOrNull { it.contains(latitude, longitude) }
  }

  companion object {
    /**
     * Disabled defaults - geofencing off.
     */
    fun disabledDefaults() = GeofencePolicy()

    /**
     * Maximum number of geofence regions (Android limitation).
     */
    const val MAX_REGIONS = 100
  }
}
