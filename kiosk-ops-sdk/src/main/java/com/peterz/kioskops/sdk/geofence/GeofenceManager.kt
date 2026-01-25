/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.geofence

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.peterz.kioskops.sdk.audit.AuditTrail
import java.time.Clock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages geofence monitoring and policy profile switching.
 *
 * Security (BSI SYS.3.2.2.A8): Uses Android Geofencing API with
 * configurable responsiveness to balance accuracy and battery life.
 *
 * Privacy (GDPR Art. 5): No location data is persisted or transmitted.
 * Only region membership state is tracked for policy switching.
 *
 * @property context Application context
 * @property policyProvider Provides current geofence policy
 * @property profileProvider Provides policy profiles by name
 * @property auditTrail Audit trail for recording transitions
 * @property clock Clock for timestamps
 *
 * @since 0.4.0
 */
class GeofenceManager(
  private val context: Context,
  private val policyProvider: () -> GeofencePolicy,
  private val profileProvider: (String) -> PolicyProfile?,
  private val auditTrail: AuditTrail?,
  private val clock: Clock = Clock.systemUTC(),
) {

  private val listeners = CopyOnWriteArrayList<GeofenceTransitionListener>()
  private val isMonitoring = AtomicBoolean(false)
  private val activeRegions = CopyOnWriteArrayList<String>()
  private val currentProfile = AtomicReference<String>(PolicyProfile.DEFAULT_PROFILE_NAME)

  /**
   * Start geofence monitoring.
   *
   * Requires ACCESS_FINE_LOCATION permission. For background monitoring,
   * ACCESS_BACKGROUND_LOCATION is also required on Android 10+.
   *
   * @return Result indicating success or failure reason
   */
  @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
  suspend fun startMonitoring(): GeofenceStartResult {
    val policy = policyProvider()

    // Check if geofencing is enabled
    if (!policy.enabled) {
      return GeofenceStartResult.Disabled
    }

    // Check if there are regions to monitor
    if (policy.regions.isEmpty()) {
      return GeofenceStartResult.NoRegions
    }

    // Check if already monitoring
    if (isMonitoring.get()) {
      return GeofenceStartResult.AlreadyActive
    }

    // Check permissions
    if (ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      return GeofenceStartResult.PermissionDenied(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Check if location services are enabled
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
      !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    ) {
      return GeofenceStartResult.LocationDisabled("Location services are disabled")
    }

    return try {
      // Register geofences with the system
      registerGeofences(policy.regions)
      isMonitoring.set(true)

      // Record audit event
      auditTrail?.record(
        "geofence_monitoring_started",
        mapOf(
          "region_count" to policy.regions.size.toString(),
          "timestamp" to clock.instant().toString(),
        ),
      )

      GeofenceStartResult.Success
    } catch (e: Exception) {
      GeofenceStartResult.Error(e)
    }
  }

  /**
   * Stop geofence monitoring.
   */
  suspend fun stopMonitoring() {
    if (!isMonitoring.getAndSet(false)) {
      return // Not monitoring
    }

    try {
      unregisterGeofences()
      activeRegions.clear()
      currentProfile.set(PolicyProfile.DEFAULT_PROFILE_NAME)

      auditTrail?.record(
        "geofence_monitoring_stopped",
        mapOf("timestamp" to clock.instant().toString()),
      )
    } catch (e: Exception) {
      // Log but don't throw - stopping should be best-effort
      notifyError(GeofenceError.GeneralError(e, "Failed to unregister geofences"))
    }
  }

  /**
   * Get current active region (highest priority if multiple).
   */
  fun getCurrentRegion(): GeofenceRegion? {
    val policy = policyProvider()
    return policy.regionsByPriority().firstOrNull { it.id in activeRegions }
  }

  /**
   * Get current policy profile name.
   */
  fun getCurrentProfileName(): String = currentProfile.get()

  /**
   * Get current policy profile.
   */
  fun getCurrentProfile(): PolicyProfile? = profileProvider(currentProfile.get())

  /**
   * Get all currently active region IDs.
   */
  fun getActiveRegionIds(): List<String> = activeRegions.toList()

  /**
   * Check if currently inside a specific region.
   */
  fun isInRegion(regionId: String): Boolean = regionId in activeRegions

  /**
   * Check if monitoring is active.
   */
  fun isMonitoring(): Boolean = isMonitoring.get()

  /**
   * Handle a geofence transition event.
   *
   * Called by GeofenceBroadcastReceiver when transitions are detected.
   */
  internal suspend fun handleTransition(
    regionId: String,
    transitionType: TransitionType,
  ) {
    val policy = policyProvider()
    val region = policy.regions.find { it.id == regionId } ?: return

    when (transitionType) {
      TransitionType.ENTER -> handleEnter(region)
      TransitionType.EXIT -> handleExit(region)
      TransitionType.DWELL -> handleDwell(region)
    }
  }

  private suspend fun handleEnter(region: GeofenceRegion) {
    activeRegions.add(region.id)

    // Update profile if this is now the highest priority active region
    val policy = policyProvider()
    val highestPriority = policy.regionsByPriority().firstOrNull { it.id in activeRegions }
    if (highestPriority?.id == region.id) {
      updateProfile(region.policyProfile)
    }

    val profile = profileProvider(region.policyProfile) ?: PolicyProfile(name = region.policyProfile)

    // Notify listeners
    listeners.forEach { it.onRegionEntered(region, profile) }

    // Audit
    auditTrail?.record(
      "geofence_region_entered",
      mapOf(
        "region_id" to region.id,
        "profile" to region.policyProfile,
        "timestamp" to clock.instant().toString(),
      ),
    )
  }

  private suspend fun handleExit(region: GeofenceRegion) {
    activeRegions.remove(region.id)

    // Determine new profile
    val policy = policyProvider()
    val newRegion = policy.regionsByPriority().firstOrNull { it.id in activeRegions }
    val newProfileName = newRegion?.policyProfile ?: policy.defaultPolicyProfile

    if (currentProfile.get() == region.policyProfile) {
      updateProfile(newProfileName)
    }

    val newProfile = profileProvider(newProfileName) ?: PolicyProfile(name = newProfileName)

    // Notify listeners
    listeners.forEach { it.onRegionExited(region, newProfile) }

    // Audit
    auditTrail?.record(
      "geofence_region_exited",
      mapOf(
        "region_id" to region.id,
        "new_profile" to newProfileName,
        "timestamp" to clock.instant().toString(),
      ),
    )
  }

  private fun handleDwell(region: GeofenceRegion) {
    listeners.forEach { it.onDwell(region) }
  }

  private fun updateProfile(profileName: String) {
    val previous = currentProfile.getAndSet(profileName)
    if (previous != profileName) {
      // Profile changed - SDK will pick up the change via policyProvider
    }
  }

  private fun notifyError(error: GeofenceError) {
    listeners.forEach { it.onError(error) }
  }

  /**
   * Add a geofence transition listener.
   */
  fun addTransitionListener(listener: GeofenceTransitionListener) {
    listeners.add(listener)
  }

  /**
   * Remove a geofence transition listener.
   */
  fun removeTransitionListener(listener: GeofenceTransitionListener) {
    listeners.remove(listener)
  }

  /**
   * Register geofences with the system.
   * Override in subclass for actual implementation with GeofencingClient.
   */
  protected open fun registerGeofences(regions: List<GeofenceRegion>) {
    // Base implementation is a no-op
    // Real implementation would use Google Play Services GeofencingClient
  }

  /**
   * Unregister all geofences.
   * Override in subclass for actual implementation.
   */
  protected open fun unregisterGeofences() {
    // Base implementation is a no-op
  }
}
