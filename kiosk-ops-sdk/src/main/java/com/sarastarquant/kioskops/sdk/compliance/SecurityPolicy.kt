package com.sarastarquant.kioskops.sdk.compliance

import com.sarastarquant.kioskops.sdk.crypto.KeyDerivationConfig
import com.sarastarquant.kioskops.sdk.crypto.KeyRotationPolicy

data class SecurityPolicy(
  val encryptQueuePayloads: Boolean,
  val encryptTelemetryAtRest: Boolean,
  val encryptDiagnosticsBundle: Boolean,
  val encryptExportedLogs: Boolean,
  val maxEventPayloadBytes: Int,
  /** Key rotation policy for encryption keys. */
  val keyRotationPolicy: KeyRotationPolicy = KeyRotationPolicy.default(),
  /** Key derivation parameters for password-based operations. */
  val keyDerivationConfig: KeyDerivationConfig = KeyDerivationConfig.default(),
  /** Use Room-backed persistent audit trail. Default enabled for new installs. */
  val useRoomBackedAudit: Boolean = true,
  /** Sign audit entries with device attestation. Opt-in for high-security deployments. */
  val signAuditEntries: Boolean = false,
  /**
   * Attach a device-signed audit chain checkpoint to every sync batch. A server that pins
   * checkpoints can check each one against the last (see docs/SECURE_DESIGN.md for the rules and
   * their limits).
   * @since 1.6.0
   */
  val signedAuditCheckpoints: Boolean = false,
) {
  /** Binary compatibility with callers compiled before [signedAuditCheckpoints] existed. */
  @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
  constructor(
    encryptQueuePayloads: Boolean,
    encryptTelemetryAtRest: Boolean,
    encryptDiagnosticsBundle: Boolean,
    encryptExportedLogs: Boolean,
    maxEventPayloadBytes: Int,
    keyRotationPolicy: KeyRotationPolicy = KeyRotationPolicy.default(),
    keyDerivationConfig: KeyDerivationConfig = KeyDerivationConfig.default(),
    useRoomBackedAudit: Boolean = true,
    signAuditEntries: Boolean = false,
  ) : this(
    encryptQueuePayloads,
    encryptTelemetryAtRest,
    encryptDiagnosticsBundle,
    encryptExportedLogs,
    maxEventPayloadBytes,
    keyRotationPolicy,
    keyDerivationConfig,
    useRoomBackedAudit,
    signAuditEntries,
    signedAuditCheckpoints = false,
  )

  /** Binary compatibility with callers compiled before [signedAuditCheckpoints] existed. */
  @Deprecated("kept for binary compatibility", level = DeprecationLevel.HIDDEN)
  fun copy(
    encryptQueuePayloads: Boolean = this.encryptQueuePayloads,
    encryptTelemetryAtRest: Boolean = this.encryptTelemetryAtRest,
    encryptDiagnosticsBundle: Boolean = this.encryptDiagnosticsBundle,
    encryptExportedLogs: Boolean = this.encryptExportedLogs,
    maxEventPayloadBytes: Int = this.maxEventPayloadBytes,
    keyRotationPolicy: KeyRotationPolicy = this.keyRotationPolicy,
    keyDerivationConfig: KeyDerivationConfig = this.keyDerivationConfig,
    useRoomBackedAudit: Boolean = this.useRoomBackedAudit,
    signAuditEntries: Boolean = this.signAuditEntries,
  ): SecurityPolicy = copy(
    encryptQueuePayloads,
    encryptTelemetryAtRest,
    encryptDiagnosticsBundle,
    encryptExportedLogs,
    maxEventPayloadBytes,
    keyRotationPolicy,
    keyDerivationConfig,
    useRoomBackedAudit,
    signAuditEntries,
    signedAuditCheckpoints,
  )

  companion object {
    fun maximalistDefaults() = SecurityPolicy(
      encryptQueuePayloads = true,
      encryptTelemetryAtRest = true,
      encryptDiagnosticsBundle = true,
      encryptExportedLogs = true,
      maxEventPayloadBytes = 64 * 1024,
      keyRotationPolicy = KeyRotationPolicy.default(),
      keyDerivationConfig = KeyDerivationConfig.default(),
      useRoomBackedAudit = true,
      signAuditEntries = false,
    )

    /**
     * High-security policy for environments requiring maximum protection.
     * Includes more aggressive key rotation and signed audit entries.
     */
    fun highSecurityDefaults() = SecurityPolicy(
      encryptQueuePayloads = true,
      encryptTelemetryAtRest = true,
      encryptDiagnosticsBundle = true,
      encryptExportedLogs = true,
      maxEventPayloadBytes = 64 * 1024,
      keyRotationPolicy = KeyRotationPolicy.strict(),
      keyDerivationConfig = KeyDerivationConfig.highSecurity(),
      useRoomBackedAudit = true,
      signAuditEntries = true,
    )
  }
}
