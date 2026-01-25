package com.peterz.kioskops.sdk.diagnostics

import kotlinx.serialization.Serializable

@Serializable
data class HealthSnapshot(
  val ts: Long,
  val sdkVersion: String,
  val appPackage: String,
  val androidSdkInt: Int,
  val deviceModel: String,
  val manufacturer: String? = null,
  val securityPatch: String? = null,
  val isDeviceOwner: Boolean? = null,
  val isInLockTaskMode: Boolean? = null,
  val policyHash: String? = null,
  val queueDepth: Long,
  val quarantinedCount: Long = 0,
  val locationId: String,
  val regionTag: String? = null,
  val includeDeviceId: Boolean,
  val sdkDeviceId: String? = null,
  /** Whether the device supports hardware-backed key attestation. */
  val supportsHardwareAttestation: Boolean = false,
  /** Security level of SDK encryption keys (SOFTWARE, TEE, STRONGBOX). */
  val keySecurityLevel: String? = null,
  /** Whether encryption keys are stored in secure hardware. */
  val keysAreHardwareBacked: Boolean = false,
)
