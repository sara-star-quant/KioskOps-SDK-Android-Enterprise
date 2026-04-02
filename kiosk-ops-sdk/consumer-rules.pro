# =============================================================================
# KioskOps SDK Consumer ProGuard Rules
# =============================================================================
# Applied to consumers of the SDK when they enable R8/ProGuard.

# -----------------------------------------------------------------------------
# 1. Public API entry points
# -----------------------------------------------------------------------------
-keep class com.sarastarquant.kioskops.sdk.KioskOpsSdk { *; }
-keep class com.sarastarquant.kioskops.sdk.KioskOpsConfig { *; }
-keep class com.sarastarquant.kioskops.sdk.KioskOpsInitializer { *; }
-keep class com.sarastarquant.kioskops.sdk.HealthCheckResult { *; }
-keep class com.sarastarquant.kioskops.sdk.KioskOpsErrorListener { *; }
-keep class com.sarastarquant.kioskops.sdk.KioskOpsError { *; }
-keep class com.sarastarquant.kioskops.sdk.KioskOpsError$* { *; }
-keep class com.sarastarquant.kioskops.sdk.ExperimentalKioskOpsApi { *; }

# -----------------------------------------------------------------------------
# 2. Policy and configuration types
# -----------------------------------------------------------------------------
-keep class com.sarastarquant.kioskops.sdk.compliance.** { *; }
-keep class com.sarastarquant.kioskops.sdk.policy.** { *; }
-keep class com.sarastarquant.kioskops.sdk.sync.SyncPolicy { *; }
-keep class com.sarastarquant.kioskops.sdk.sync.SyncOnceResult { *; }
-keep class com.sarastarquant.kioskops.sdk.sync.Backoff { *; }
-keep class com.sarastarquant.kioskops.sdk.sync.Backoff$* { *; }

# -----------------------------------------------------------------------------
# 3. Queue result types (sealed classes + subtypes)
# -----------------------------------------------------------------------------
-keep class com.sarastarquant.kioskops.sdk.queue.EnqueueResult { *; }
-keep class com.sarastarquant.kioskops.sdk.queue.EnqueueResult$* { *; }
-keep class com.sarastarquant.kioskops.sdk.queue.QuarantinedEventSummary { *; }

# -----------------------------------------------------------------------------
# 4. Crypto interfaces and policies
# -----------------------------------------------------------------------------
-keep class com.sarastarquant.kioskops.sdk.crypto.CryptoProvider { *; }
-keep class com.sarastarquant.kioskops.sdk.crypto.AesGcmKeystoreCryptoProvider { *; }
-keep class com.sarastarquant.kioskops.sdk.crypto.ConditionalCryptoProvider { *; }
-keep class com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider { *; }
-keep class com.sarastarquant.kioskops.sdk.crypto.VersionedCryptoProvider { *; }
-keep class com.sarastarquant.kioskops.sdk.crypto.FieldLevelEncryptor { *; }
-keep class com.sarastarquant.kioskops.sdk.crypto.KeyRotationPolicy { *; }
-keep class com.sarastarquant.kioskops.sdk.crypto.KeyDerivationConfig { *; }
-keep class com.sarastarquant.kioskops.sdk.crypto.KeyAttestationStatus { *; }
-keep class com.sarastarquant.kioskops.sdk.crypto.KeyAttestationReporter { *; }
-keep class com.sarastarquant.kioskops.sdk.crypto.KeyMetadataStore { *; }
-keep class com.sarastarquant.kioskops.sdk.crypto.RotationResult { *; }
-keep class com.sarastarquant.kioskops.sdk.crypto.RotationResult$* { *; }

# -----------------------------------------------------------------------------
# 5. Transport interfaces and models
# -----------------------------------------------------------------------------
-keep class com.sarastarquant.kioskops.sdk.transport.Transport { *; }
-keep class com.sarastarquant.kioskops.sdk.transport.TransportResult { *; }
-keep class com.sarastarquant.kioskops.sdk.transport.TransportResult$* { *; }
-keep class com.sarastarquant.kioskops.sdk.transport.TransportEvent { *; }
-keep class com.sarastarquant.kioskops.sdk.transport.BatchSendRequest { *; }
-keep class com.sarastarquant.kioskops.sdk.transport.BatchSendResponse { *; }
-keep class com.sarastarquant.kioskops.sdk.transport.EventAck { *; }
-keep class com.sarastarquant.kioskops.sdk.transport.AuthProvider { *; }
-keep class com.sarastarquant.kioskops.sdk.transport.RequestSigner { *; }
-keep class com.sarastarquant.kioskops.sdk.transport.OkHttpTransport { *; }
-keep class com.sarastarquant.kioskops.sdk.transport.HmacRequestSigner { *; }
-keep class com.sarastarquant.kioskops.sdk.transport.NonceProvider { *; }
-keep class com.sarastarquant.kioskops.sdk.transport.security.** { *; }

# -----------------------------------------------------------------------------
# 6. Audit types
# -----------------------------------------------------------------------------
-keep class com.sarastarquant.kioskops.sdk.audit.AuditEvent { *; }
-keep class com.sarastarquant.kioskops.sdk.audit.AuditTrail { *; }
-keep class com.sarastarquant.kioskops.sdk.audit.PersistentAuditTrail { *; }
-keep class com.sarastarquant.kioskops.sdk.audit.ChainVerificationResult { *; }
-keep class com.sarastarquant.kioskops.sdk.audit.ChainVerificationResult$* { *; }
-keep class com.sarastarquant.kioskops.sdk.audit.AuditStatistics { *; }
-keep class com.sarastarquant.kioskops.sdk.audit.DeviceAttestationProvider { *; }
-keep class com.sarastarquant.kioskops.sdk.audit.KeystoreAttestationProvider { *; }

# -----------------------------------------------------------------------------
# 7. Fleet, diagnostics, and device management
# -----------------------------------------------------------------------------
-keep class com.sarastarquant.kioskops.sdk.fleet.DevicePosture { *; }
-keep class com.sarastarquant.kioskops.sdk.fleet.DeviceGroupProvider { *; }
-keep class com.sarastarquant.kioskops.sdk.fleet.DefaultDeviceGroupProvider { *; }
-keep class com.sarastarquant.kioskops.sdk.fleet.DiagnosticsUploader { *; }
-keep class com.sarastarquant.kioskops.sdk.fleet.PolicyDriftDetector { *; }
-keep class com.sarastarquant.kioskops.sdk.fleet.ManagedConfigReader { *; }
-keep class com.sarastarquant.kioskops.sdk.fleet.config.RemoteConfigManager { *; }
-keep class com.sarastarquant.kioskops.sdk.fleet.config.RemoteConfigPolicy { *; }
-keep class com.sarastarquant.kioskops.sdk.fleet.config.ConfigVersion { *; }
-keep class com.sarastarquant.kioskops.sdk.fleet.config.ConfigUpdateResult { *; }
-keep class com.sarastarquant.kioskops.sdk.fleet.config.ConfigUpdateResult$* { *; }
-keep class com.sarastarquant.kioskops.sdk.fleet.config.ConfigRollbackResult { *; }
-keep class com.sarastarquant.kioskops.sdk.fleet.config.ConfigRollbackResult$* { *; }
-keep class com.sarastarquant.kioskops.sdk.fleet.config.ConfigSource { *; }
-keep class com.sarastarquant.kioskops.sdk.fleet.posture.** { *; }
-keep class com.sarastarquant.kioskops.sdk.diagnostics.** { *; }

# -----------------------------------------------------------------------------
# 8. Validation, PII, anomaly detection
# -----------------------------------------------------------------------------
-keep class com.sarastarquant.kioskops.sdk.validation.** { *; }
-keep class com.sarastarquant.kioskops.sdk.pii.** { *; }
-keep class com.sarastarquant.kioskops.sdk.anomaly.** { *; }

# -----------------------------------------------------------------------------
# 9. Geofencing, observability, debug, logging, telemetry
# -----------------------------------------------------------------------------
-keep class com.sarastarquant.kioskops.sdk.geofence.** { *; }
-keep class com.sarastarquant.kioskops.sdk.observability.** { *; }
-keep class com.sarastarquant.kioskops.sdk.debug.** { *; }
-keep class com.sarastarquant.kioskops.sdk.logging.** { *; }
-keep class com.sarastarquant.kioskops.sdk.telemetry.TelemetrySink { *; }
-keep class com.sarastarquant.kioskops.sdk.telemetry.TelemetryEvent { *; }

# -----------------------------------------------------------------------------
# 10. Room entities and DAOs (required for Room reflection)
# -----------------------------------------------------------------------------
-keep class com.sarastarquant.kioskops.sdk.audit.db.** { *; }
-keep class com.sarastarquant.kioskops.sdk.queue.QueueEventEntity { *; }
-keep class com.sarastarquant.kioskops.sdk.queue.QueueDao { *; }
-keep class com.sarastarquant.kioskops.sdk.queue.QueueDatabase { *; }
-keep class com.sarastarquant.kioskops.sdk.queue.QuarantinedEventRow { *; }
-keep class com.sarastarquant.kioskops.sdk.queue.QueueStates { *; }
-keep class com.sarastarquant.kioskops.sdk.fleet.config.db.** { *; }

# -----------------------------------------------------------------------------
# 11. kotlinx.serialization (keep generated serializers)
# -----------------------------------------------------------------------------
-keepclassmembers class com.sarastarquant.kioskops.sdk.** {
  *** Companion;
}
-keepclasseswithmembers class com.sarastarquant.kioskops.sdk.** {
  kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.sarastarquant.kioskops.sdk.**$$serializer { *; }

# -----------------------------------------------------------------------------
# 12. WorkManager workers (required for WorkManager reflection)
# -----------------------------------------------------------------------------
-keep class com.sarastarquant.kioskops.sdk.work.** { *; }

# -----------------------------------------------------------------------------
# 13. Kotlin metadata
# -----------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep class kotlin.Metadata { *; }
