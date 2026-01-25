/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.geofence

import kotlinx.serialization.Serializable

/**
 * Defines a geofence region with associated policy profile.
 *
 * Privacy (GDPR Art. 5): Region definitions do not contain PII.
 * Use opaque identifiers (e.g., "store-123", "warehouse-east").
 *
 * @property id Unique region identifier (must not be blank)
 * @property latitude Center latitude (-90 to 90)
 * @property longitude Center longitude (-180 to 180)
 * @property radiusMeters Circular geofence radius (must be positive)
 * @property policyProfile Policy profile to apply when inside this region
 * @property priority Higher priority wins when regions overlap (default 0)
 * @property transitionTypes Which transitions to monitor (default: ENTER, EXIT)
 * @property displayName Human-readable name for debugging (optional)
 *
 * @since 0.4.0
 */
@Serializable
data class GeofenceRegion(
  val id: String,
  val latitude: Double,
  val longitude: Double,
  val radiusMeters: Float,
  val policyProfile: String,
  val priority: Int = 0,
  val transitionTypes: Set<TransitionType> = setOf(
    TransitionType.ENTER,
    TransitionType.EXIT,
  ),
  val displayName: String? = null,
) {
  init {
    require(id.isNotBlank()) { "Region ID must not be blank" }
    require(latitude in -90.0..90.0) { "Latitude must be -90 to 90, was $latitude" }
    require(longitude in -180.0..180.0) { "Longitude must be -180 to 180, was $longitude" }
    require(radiusMeters > 0) { "Radius must be positive, was $radiusMeters" }
    require(policyProfile.isNotBlank()) { "Policy profile must not be blank" }
  }

  /**
   * Check if a point is within this region.
   *
   * Uses Haversine formula for accurate distance calculation.
   *
   * @param lat Latitude of the point
   * @param lon Longitude of the point
   * @return True if point is within radiusMeters of center
   */
  fun contains(lat: Double, lon: Double): Boolean {
    val distance = haversineDistance(latitude, longitude, lat, lon)
    return distance <= radiusMeters
  }

  companion object {
    /**
     * Maximum allowed geofence radius (100km).
     */
    const val MAX_RADIUS_METERS = 100_000f

    /**
     * Minimum allowed geofence radius (50m, Android limitation).
     */
    const val MIN_RADIUS_METERS = 50f

    /**
     * Calculate distance between two coordinates using Haversine formula.
     *
     * @return Distance in meters
     */
    fun haversineDistance(
      lat1: Double,
      lon1: Double,
      lat2: Double,
      lon2: Double,
    ): Double {
      val r = 6371000.0 // Earth radius in meters
      val dLat = Math.toRadians(lat2 - lat1)
      val dLon = Math.toRadians(lon2 - lon1)
      val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2)
      val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
      return r * c
    }
  }
}

/**
 * Types of geofence transitions.
 *
 * @since 0.4.0
 */
@Serializable
enum class TransitionType {
  /** Device entered the geofence region. */
  ENTER,
  /** Device exited the geofence region. */
  EXIT,
  /** Device dwelled in the region for the configured duration. */
  DWELL,
}
