package com.peterz.kioskops.sdk

import com.peterz.kioskops.sdk.compliance.RetentionPolicy
import com.peterz.kioskops.sdk.compliance.SecurityPolicy
import com.peterz.kioskops.sdk.compliance.TelemetryPolicy
import com.peterz.kioskops.sdk.sync.SyncPolicy

data class KioskOpsConfig(
  val baseUrl: String,
  val locationId: String,
  val kioskEnabled: Boolean,
  val syncIntervalMinutes: Long = 15L,
  val adminExitPin: String? = null,
  val securityPolicy: SecurityPolicy = SecurityPolicy.maximalistDefaults(),
  val retentionPolicy: RetentionPolicy = RetentionPolicy.maximalistDefaults(),
  val telemetryPolicy: TelemetryPolicy = TelemetryPolicy.maximalistDefaults(),
  /** Network sync is opt-in. Default is disabled to avoid silent off-device transfer. */
  val syncPolicy: SyncPolicy = SyncPolicy.disabledDefaults(),
)
