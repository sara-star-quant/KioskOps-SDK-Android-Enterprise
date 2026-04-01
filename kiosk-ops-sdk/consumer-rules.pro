# =============================================================================
# KioskOps SDK Consumer ProGuard Rules
# =============================================================================
# Applied to consumers of the SDK when they enable R8/ProGuard.

# -----------------------------------------------------------------------------
# 1. Public API entry points
# -----------------------------------------------------------------------------
-keep class com.peterz.kioskops.sdk.KioskOpsSdk { *; }
-keep class com.peterz.kioskops.sdk.KioskOpsConfig { *; }
-keep class com.peterz.kioskops.sdk.KioskOpsInitializer { *; }

# -----------------------------------------------------------------------------
# 2. Policy and configuration types
# -----------------------------------------------------------------------------
-keep class com.peterz.kioskops.sdk.compliance.** { *; }
-keep class com.peterz.kioskops.sdk.policy.** { *; }
-keep class com.peterz.kioskops.sdk.sync.SyncPolicy { *; }
-keep class com.peterz.kioskops.sdk.sync.SyncOnceResult { *; }
-keep class com.peterz.kioskops.sdk.sync.Backoff { *; }
-keep class com.peterz.kioskops.sdk.sync.Backoff$* { *; }

# -----------------------------------------------------------------------------
# 3. Queue result types (sealed classes + subtypes)
# -----------------------------------------------------------------------------
-keep class com.peterz.kioskops.sdk.queue.EnqueueResult { *; }
-keep class com.peterz.kioskops.sdk.queue.EnqueueResult$* { *; }
-keep class com.peterz.kioskops.sdk.queue.QuarantinedEventSummary { *; }

# -----------------------------------------------------------------------------
# 4. Crypto interfaces and policies
# -----------------------------------------------------------------------------
-keep class com.peterz.kioskops.sdk.crypto.CryptoProvider { *; }
-keep class com.peterz.kioskops.sdk.crypto.AesGcmKeystoreCryptoProvider { *; }
-keep class com.peterz.kioskops.sdk.crypto.ConditionalCryptoProvider { *; }
-keep class com.peterz.kioskops.sdk.crypto.NoopCryptoProvider { *; }
-keep class com.peterz.kioskops.sdk.crypto.VersionedCryptoProvider { *; }
-keep class com.peterz.kioskops.sdk.crypto.FieldLevelEncryptor { *; }
-keep class com.peterz.kioskops.sdk.crypto.KeyRotationPolicy { *; }
-keep class com.peterz.kioskops.sdk.crypto.KeyDerivationConfig { *; }
-keep class com.peterz.kioskops.sdk.crypto.KeyAttestationStatus { *; }
-keep class com.peterz.kioskops.sdk.crypto.KeyAttestationReporter { *; }
-keep class com.peterz.kioskops.sdk.crypto.KeyMetadataStore { *; }
-keep class com.peterz.kioskops.sdk.crypto.RotationResult { *; }
-keep class com.peterz.kioskops.sdk.crypto.RotationResult$* { *; }

# -----------------------------------------------------------------------------
# 5. Transport interfaces and models
# -----------------------------------------------------------------------------
-keep class com.peterz.kioskops.sdk.transport.Transport { *; }
-keep class com.peterz.kioskops.sdk.transport.TransportResult { *; }
-keep class com.peterz.kioskops.sdk.transport.TransportResult$* { *; }
-keep class com.peterz.kioskops.sdk.transport.TransportEvent { *; }
-keep class com.peterz.kioskops.sdk.transport.BatchSendRequest { *; }
-keep class com.peterz.kioskops.sdk.transport.BatchSendResponse { *; }
-keep class com.peterz.kioskops.sdk.transport.EventAck { *; }
-keep class com.peterz.kioskops.sdk.transport.AuthProvider { *; }
-keep class com.peterz.kioskops.sdk.transport.RequestSigner { *; }
-keep class com.peterz.kioskops.sdk.transport.OkHttpTransport { *; }
-keep class com.peterz.kioskops.sdk.transport.HmacRequestSigner { *; }
-keep class com.peterz.kioskops.sdk.transport.NonceProvider { *; }
-keep class com.peterz.kioskops.sdk.transport.security.** { *; }

# -----------------------------------------------------------------------------
# 6. Audit types
# -----------------------------------------------------------------------------
-keep class com.peterz.kioskops.sdk.audit.AuditEvent { *; }
-keep class com.peterz.kioskops.sdk.audit.AuditTrail { *; }
-keep class com.peterz.kioskops.sdk.audit.PersistentAuditTrail { *; }
-keep class com.peterz.kioskops.sdk.audit.ChainVerificationResult { *; }
-keep class com.peterz.kioskops.sdk.audit.ChainVerificationResult$* { *; }
-keep class com.peterz.kioskops.sdk.audit.AuditStatistics { *; }
-keep class com.peterz.kioskops.sdk.audit.DeviceAttestationProvider { *; }
-keep class com.peterz.kioskops.sdk.audit.KeystoreAttestationProvider { *; }

# -----------------------------------------------------------------------------
# 7. Fleet, diagnostics, and device management
# -----------------------------------------------------------------------------
-keep class com.peterz.kioskops.sdk.fleet.DevicePosture { *; }
-keep class com.peterz.kioskops.sdk.fleet.DeviceGroupProvider { *; }
-keep class com.peterz.kioskops.sdk.fleet.DefaultDeviceGroupProvider { *; }
-keep class com.peterz.kioskops.sdk.fleet.DiagnosticsUploader { *; }
-keep class com.peterz.kioskops.sdk.fleet.PolicyDriftDetector { *; }
-keep class com.peterz.kioskops.sdk.fleet.ManagedConfigReader { *; }
-keep class com.peterz.kioskops.sdk.fleet.config.RemoteConfigManager { *; }
-keep class com.peterz.kioskops.sdk.fleet.config.RemoteConfigPolicy { *; }
-keep class com.peterz.kioskops.sdk.fleet.config.ConfigVersion { *; }
-keep class com.peterz.kioskops.sdk.fleet.config.ConfigUpdateResult { *; }
-keep class com.peterz.kioskops.sdk.fleet.config.ConfigUpdateResult$* { *; }
-keep class com.peterz.kioskops.sdk.fleet.config.ConfigRollbackResult { *; }
-keep class com.peterz.kioskops.sdk.fleet.config.ConfigRollbackResult$* { *; }
-keep class com.peterz.kioskops.sdk.fleet.config.ConfigSource { *; }
-keep class com.peterz.kioskops.sdk.fleet.posture.** { *; }
-keep class com.peterz.kioskops.sdk.diagnostics.** { *; }

# -----------------------------------------------------------------------------
# 8. Validation, PII, anomaly detection
# -----------------------------------------------------------------------------
-keep class com.peterz.kioskops.sdk.validation.** { *; }
-keep class com.peterz.kioskops.sdk.pii.** { *; }
-keep class com.peterz.kioskops.sdk.anomaly.** { *; }

# -----------------------------------------------------------------------------
# 9. Geofencing, observability, debug, logging, telemetry
# -----------------------------------------------------------------------------
-keep class com.peterz.kioskops.sdk.geofence.** { *; }
-keep class com.peterz.kioskops.sdk.observability.** { *; }
-keep class com.peterz.kioskops.sdk.debug.** { *; }
-keep class com.peterz.kioskops.sdk.logging.** { *; }
-keep class com.peterz.kioskops.sdk.telemetry.TelemetrySink { *; }
-keep class com.peterz.kioskops.sdk.telemetry.TelemetryEvent { *; }

# -----------------------------------------------------------------------------
# 10. Room entities and DAOs (required for Room reflection)
# -----------------------------------------------------------------------------
-keep class com.peterz.kioskops.sdk.audit.db.** { *; }
-keep class com.peterz.kioskops.sdk.queue.QueueEventEntity { *; }
-keep class com.peterz.kioskops.sdk.queue.QueueDao { *; }
-keep class com.peterz.kioskops.sdk.queue.QueueDatabase { *; }
-keep class com.peterz.kioskops.sdk.queue.QuarantinedEventRow { *; }
-keep class com.peterz.kioskops.sdk.queue.QueueStates { *; }
-keep class com.peterz.kioskops.sdk.fleet.config.db.** { *; }

# -----------------------------------------------------------------------------
# 11. kotlinx.serialization (keep generated serializers)
# -----------------------------------------------------------------------------
-keepclassmembers class com.peterz.kioskops.sdk.** {
  *** Companion;
}
-keepclasseswithmembers class com.peterz.kioskops.sdk.** {
  kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.peterz.kioskops.sdk.**$$serializer { *; }

# -----------------------------------------------------------------------------
# 12. WorkManager workers (required for WorkManager reflection)
# -----------------------------------------------------------------------------
-keep class com.peterz.kioskops.sdk.work.** { *; }

# -----------------------------------------------------------------------------
# 13. Kotlin metadata
# -----------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep class kotlin.Metadata { *; }
