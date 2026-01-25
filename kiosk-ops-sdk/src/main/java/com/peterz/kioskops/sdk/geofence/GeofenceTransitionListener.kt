/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.geofence

/**
 * Callback interface for geofence transition events.
 *
 * Privacy (GDPR Art. 5): Callbacks receive region information only,
 * not the device's actual coordinates.
 *
 * @since 0.4.0
 */
interface GeofenceTransitionListener {

  /**
   * Called when the device enters a geofence region.
   *
   * @param region The region entered
   * @param profile The policy profile being applied
   */
  fun onRegionEntered(region: GeofenceRegion, profile: PolicyProfile)

  /**
   * Called when the device exits a geofence region.
   *
   * @param region The region exited
   * @param newProfile The new policy profile being applied (may be default)
   */
  fun onRegionExited(region: GeofenceRegion, newProfile: PolicyProfile)

  /**
   * Called when the device has dwelled in a region for the configured duration.
   *
   * @param region The region where dwell occurred
   */
  fun onDwell(region: GeofenceRegion)

  /**
   * Called when geofence monitoring encounters an error.
   *
   * @param error The error that occurred
   */
  fun onError(error: GeofenceError)
}

/**
 * Base class for geofence transition listeners with default no-op implementations.
 *
 * @since 0.4.0
 */
open class GeofenceTransitionAdapter : GeofenceTransitionListener {
  override fun onRegionEntered(region: GeofenceRegion, profile: PolicyProfile) {}
  override fun onRegionExited(region: GeofenceRegion, newProfile: PolicyProfile) {}
  override fun onDwell(region: GeofenceRegion) {}
  override fun onError(error: GeofenceError) {}
}

/**
 * Geofence-related errors.
 *
 * @since 0.4.0
 */
sealed class GeofenceError {
  /**
   * Location permission was denied.
   */
  data class PermissionDenied(
    val permission: String,
    val message: String = "Location permission required for geofencing",
  ) : GeofenceError()

  /**
   * Location services are disabled on the device.
   */
  data class LocationDisabled(
    val message: String = "Location services must be enabled for geofencing",
  ) : GeofenceError()

  /**
   * Google Play Services error.
   */
  data class PlayServicesError(
    val errorCode: Int,
    val message: String,
  ) : GeofenceError()

  /**
   * Too many geofences registered.
   */
  data class TooManyGeofences(
    val current: Int,
    val maximum: Int = GeofencePolicy.MAX_REGIONS,
  ) : GeofenceError()

  /**
   * General geofence error.
   */
  data class GeneralError(
    val cause: Throwable?,
    val message: String,
  ) : GeofenceError()
}

/**
 * Result of starting geofence monitoring.
 *
 * @since 0.4.0
 */
sealed class GeofenceStartResult {
  /**
   * Geofence monitoring started successfully.
   */
  object Success : GeofenceStartResult()

  /**
   * Monitoring already active.
   */
  object AlreadyActive : GeofenceStartResult()

  /**
   * Geofencing is disabled in policy.
   */
  object Disabled : GeofenceStartResult()

  /**
   * No regions configured.
   */
  object NoRegions : GeofenceStartResult()

  /**
   * Failed due to permission denial.
   */
  data class PermissionDenied(val permission: String) : GeofenceStartResult()

  /**
   * Failed because location services are disabled.
   */
  data class LocationDisabled(val message: String) : GeofenceStartResult()

  /**
   * Failed due to an error.
   */
  data class Error(val cause: Exception) : GeofenceStartResult()
}
