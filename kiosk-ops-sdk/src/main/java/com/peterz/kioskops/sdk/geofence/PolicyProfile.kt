/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.geofence

import com.peterz.kioskops.sdk.KioskOpsConfig
import com.peterz.kioskops.sdk.compliance.TelemetryPolicy
import com.peterz.kioskops.sdk.diagnostics.DiagnosticsSchedulePolicy
import com.peterz.kioskops.sdk.sync.SyncPolicy

/**
 * Named policy configuration profile.
 *
 * Allows switching between pre-defined configurations based on geofence location.
 * Only non-null policy fields are applied; null fields retain base config values.
 *
 * Example profiles:
 * - "store-floor": High sync frequency, extended diagnostics
 * - "warehouse": Reduced sync, battery optimization
 * - "transit": Minimal sync, offline-first
 * - "secure-zone": Enhanced telemetry, frequent heartbeats
 *
 * @property name Unique profile name (must match GeofenceRegion.policyProfile)
 * @property syncPolicy Sync policy override (null = use base config)
 * @property telemetryPolicy Telemetry policy override (null = use base config)
 * @property diagnosticsSchedulePolicy Diagnostics schedule override (null = use base config)
 * @property description Human-readable description for documentation
 *
 * @since 0.4.0
 */
data class PolicyProfile(
  val name: String,
  val syncPolicy: SyncPolicy? = null,
  val telemetryPolicy: TelemetryPolicy? = null,
  val diagnosticsSchedulePolicy: DiagnosticsSchedulePolicy? = null,
  val description: String? = null,
) {
  init {
    require(name.isNotBlank()) { "Profile name must not be blank" }
  }

  /**
   * Apply this profile to a base configuration.
   *
   * Non-null policy fields in this profile override the corresponding
   * fields in the base config. Null fields are left unchanged.
   *
   * @param base The base configuration to modify
   * @return New configuration with profile overrides applied
   */
  fun applyTo(base: KioskOpsConfig): KioskOpsConfig {
    return base.copy(
      syncPolicy = syncPolicy ?: base.syncPolicy,
      telemetryPolicy = telemetryPolicy ?: base.telemetryPolicy,
      diagnosticsSchedulePolicy = diagnosticsSchedulePolicy ?: base.diagnosticsSchedulePolicy,
    )
  }

  /**
   * Check if this profile modifies any policies.
   */
  fun hasOverrides(): Boolean {
    return syncPolicy != null ||
      telemetryPolicy != null ||
      diagnosticsSchedulePolicy != null
  }

  companion object {
    /**
     * Default profile name used when not inside any geofence region.
     */
    const val DEFAULT_PROFILE_NAME = "default"

    /**
     * Create a sync-focused profile for high connectivity zones.
     */
    fun highConnectivity(name: String) = PolicyProfile(
      name = name,
      syncPolicy = SyncPolicy(
        enabled = true,
        batchSize = 100,
      ),
      description = "High connectivity zone with frequent sync",
    )

    /**
     * Create a battery-saving profile for extended operation.
     */
    fun batterySaver(name: String) = PolicyProfile(
      name = name,
      syncPolicy = SyncPolicy(
        enabled = true,
        batchSize = 25,
        requireUnmeteredNetwork = true,
      ),
      description = "Battery saver mode with reduced sync frequency",
    )

    /**
     * Create an offline-first profile for poor connectivity areas.
     */
    fun offlineFirst(name: String) = PolicyProfile(
      name = name,
      syncPolicy = SyncPolicy(
        enabled = true,
        batchSize = 10,
        requireUnmeteredNetwork = true,
      ),
      description = "Offline-first mode for poor connectivity",
    )
  }
}
