package com.peterz.kioskops.sdk

import com.peterz.kioskops.sdk.anomaly.AnomalyPolicy
import com.peterz.kioskops.sdk.compliance.RetentionPolicy
import com.peterz.kioskops.sdk.compliance.SecurityPolicy
import com.peterz.kioskops.sdk.compliance.TelemetryPolicy
import com.peterz.kioskops.sdk.compliance.QueueLimits
import com.peterz.kioskops.sdk.compliance.IdempotencyConfig
import com.peterz.kioskops.sdk.crypto.FieldEncryptionPolicy
import com.peterz.kioskops.sdk.diagnostics.DiagnosticsSchedulePolicy
import com.peterz.kioskops.sdk.fleet.config.RemoteConfigPolicy
import com.peterz.kioskops.sdk.geofence.GeofencePolicy
import com.peterz.kioskops.sdk.geofence.PolicyProfile
import com.peterz.kioskops.sdk.observability.ObservabilityPolicy
import com.peterz.kioskops.sdk.pii.DataClassificationPolicy
import com.peterz.kioskops.sdk.pii.PiiPolicy
import com.peterz.kioskops.sdk.sync.SyncPolicy
import com.peterz.kioskops.sdk.transport.security.TransportSecurityPolicy
import com.peterz.kioskops.sdk.validation.ValidationPolicy

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

  // v0.5.0 Data & Validation
  /** Event validation policy. @since 0.5.0 */
  val validationPolicy: ValidationPolicy = ValidationPolicy.disabledDefaults(),
  /** PII detection and redaction policy. @since 0.5.0 */
  val piiPolicy: PiiPolicy = PiiPolicy.disabledDefaults(),
  /** Field-level encryption policy. @since 0.5.0 */
  val fieldEncryptionPolicy: FieldEncryptionPolicy = FieldEncryptionPolicy.disabledDefaults(),
  /** Data classification policy. @since 0.5.0 */
  val dataClassificationPolicy: DataClassificationPolicy = DataClassificationPolicy.disabledDefaults(),
  /** Anomaly detection policy. @since 0.5.0 */
  val anomalyPolicy: AnomalyPolicy = AnomalyPolicy.disabledDefaults(),
) {
  companion object {
    /**
     * FedRAMP-compliant defaults (NIST 800-53).
     *
     * Enables all security features: validation (strict), PII detection (reject),
     * field encryption, anomaly detection, signed audit, and 365-day audit retention.
     *
     * @since 0.5.0
     */
    fun fedRampDefaults(baseUrl: String, locationId: String) = KioskOpsConfig(
      baseUrl = baseUrl,
      locationId = locationId,
      kioskEnabled = true,
      securityPolicy = SecurityPolicy.highSecurityDefaults(),
      retentionPolicy = RetentionPolicy.maximalistDefaults().copy(
        retainAuditDays = 365,
        minimumAuditRetentionDays = 365,
      ),
      validationPolicy = ValidationPolicy.strictDefaults(),
      piiPolicy = PiiPolicy.rejectDefaults(),
      fieldEncryptionPolicy = FieldEncryptionPolicy.enabledDefaults(),
      dataClassificationPolicy = DataClassificationPolicy.enabledDefaults(),
      anomalyPolicy = AnomalyPolicy.highSecurityDefaults(),
    )

    /**
     * GDPR-compliant defaults.
     *
     * Enables PII redaction (not rejection), data classification,
     * and moderate anomaly detection.
     *
     * @since 0.5.0
     */
    fun gdprDefaults(baseUrl: String, locationId: String) = KioskOpsConfig(
      baseUrl = baseUrl,
      locationId = locationId,
      kioskEnabled = true,
      securityPolicy = SecurityPolicy.maximalistDefaults(),
      validationPolicy = ValidationPolicy.permissiveDefaults(),
      piiPolicy = PiiPolicy.redactDefaults(),
      fieldEncryptionPolicy = FieldEncryptionPolicy.enabledDefaults(),
      dataClassificationPolicy = DataClassificationPolicy.enabledDefaults(),
      anomalyPolicy = AnomalyPolicy.enabledDefaults(),
    )
  }
}
