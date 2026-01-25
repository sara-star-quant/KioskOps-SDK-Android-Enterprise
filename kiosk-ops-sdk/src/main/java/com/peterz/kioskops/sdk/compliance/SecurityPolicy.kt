package com.peterz.kioskops.sdk.compliance

import com.peterz.kioskops.sdk.crypto.KeyDerivationConfig
import com.peterz.kioskops.sdk.crypto.KeyRotationPolicy

data class SecurityPolicy(
  val encryptQueuePayloads: Boolean,
  val encryptTelemetryAtRest: Boolean,
  val encryptDiagnosticsBundle: Boolean,
  val encryptExportedLogs: Boolean,
  val maxEventPayloadBytes: Int,
  val denylistJsonKeys: Set<String>,
  val allowRawPayloadStorage: Boolean,
  /** Key rotation policy for encryption keys. */
  val keyRotationPolicy: KeyRotationPolicy = KeyRotationPolicy.default(),
  /** Key derivation parameters for password-based operations. */
  val keyDerivationConfig: KeyDerivationConfig = KeyDerivationConfig.default(),
  /** Use Room-backed persistent audit trail. Default enabled for new installs. */
  val useRoomBackedAudit: Boolean = true,
  /** Sign audit entries with device attestation. Opt-in for high-security deployments. */
  val signAuditEntries: Boolean = false,
) {
  companion object {
    fun maximalistDefaults() = SecurityPolicy(
      encryptQueuePayloads = true,
      encryptTelemetryAtRest = true,
      encryptDiagnosticsBundle = true,
      encryptExportedLogs = true,
      maxEventPayloadBytes = 64 * 1024,
      denylistJsonKeys = setOf("email", "phone", "address", "name", "ssn", "id_number"),
      allowRawPayloadStorage = false,
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
      denylistJsonKeys = setOf("email", "phone", "address", "name", "ssn", "id_number", "dob", "credit_card"),
      allowRawPayloadStorage = false,
      keyRotationPolicy = KeyRotationPolicy.strict(),
      keyDerivationConfig = KeyDerivationConfig.highSecurity(),
      useRoomBackedAudit = true,
      signAuditEntries = true,
    )
  }
}
