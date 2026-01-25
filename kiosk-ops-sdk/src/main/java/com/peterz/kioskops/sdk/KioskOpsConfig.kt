package com.peterz.kioskops.sdk

import com.peterz.kioskops.sdk.compliance.RetentionPolicy
import com.peterz.kioskops.sdk.compliance.SecurityPolicy
import com.peterz.kioskops.sdk.compliance.TelemetryPolicy
import com.peterz.kioskops.sdk.compliance.QueueLimits
import com.peterz.kioskops.sdk.compliance.IdempotencyConfig
import com.peterz.kioskops.sdk.diagnostics.DiagnosticsSchedulePolicy
import com.peterz.kioskops.sdk.fleet.config.RemoteConfigPolicy
import com.peterz.kioskops.sdk.geofence.GeofencePolicy
import com.peterz.kioskops.sdk.geofence.PolicyProfile
import com.peterz.kioskops.sdk.observability.ObservabilityPolicy
import com.peterz.kioskops.sdk.sync.SyncPolicy
import com.peterz.kioskops.sdk.transport.security.TransportSecurityPolicy

/**
 * Main configuration for KioskOps SDK.
 *
 * Security (ISO 27001 A.5): All security-relevant settings are explicitly configured
 * with secure defaults. No silent data transfer occurs without explicit opt-in.
 */
data class KioskOpsConfig(
  val baseUrl: String,
  val locationId: String,
  val kioskEnabled: Boolean,
  val syncIntervalMinutes: Long = 15L,
  val adminExitPin: String? = null,
  val securityPolicy: SecurityPolicy = SecurityPolicy.maximalistDefaults(),
  val retentionPolicy: RetentionPolicy = RetentionPolicy.maximalistDefaults(),
  val telemetryPolicy: TelemetryPolicy = TelemetryPolicy.maximalistDefaults(),
  val queueLimits: QueueLimits = QueueLimits.maximalistDefaults(),
  val idempotencyConfig: IdempotencyConfig = IdempotencyConfig.maximalistDefaults(),
  /** Network sync is opt-in. Default is disabled to avoid silent off-device transfer. */
  val syncPolicy: SyncPolicy = SyncPolicy.disabledDefaults(),
  /** Transport layer security: certificate pinning, mTLS, and CT validation. */
  val transportSecurityPolicy: TransportSecurityPolicy = TransportSecurityPolicy(),

  // v0.3.0 Fleet Operations
  /** Remote configuration policy for managed config and FCM updates. */
  val remoteConfigPolicy: RemoteConfigPolicy = RemoteConfigPolicy.disabledDefaults(),
  /** Diagnostics scheduling policy for automated collection and remote triggers. */
  val diagnosticsSchedulePolicy: DiagnosticsSchedulePolicy = DiagnosticsSchedulePolicy.disabledDefaults(),

  // v0.4.0 Observability & Developer Experience
  /** Observability configuration for logging, tracing, and metrics. */
  val observabilityPolicy: ObservabilityPolicy = ObservabilityPolicy.disabledDefaults(),
  /** Geofence-aware policy switching configuration. */
  val geofencePolicy: GeofencePolicy = GeofencePolicy.disabledDefaults(),
  /** Named policy profiles for geofence-based configuration switching. */
  val policyProfiles: Map<String, PolicyProfile> = emptyMap(),
)
