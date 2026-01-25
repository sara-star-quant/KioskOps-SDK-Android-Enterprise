package com.peterz.kioskops.sdk.fleet

import com.peterz.kioskops.sdk.crypto.SecurityLevel

data class DevicePosture(
  val isDeviceOwner: Boolean,
  val isLockTaskPermitted: Boolean,
  val androidSdkInt: Int,
  val deviceModel: String,
  val manufacturer: String,
  val securityPatch: String?,
  /** Whether the device supports hardware-backed key attestation. */
  val supportsHardwareAttestation: Boolean = false,
  /** Security level of SDK encryption keys (SOFTWARE, TEE, or STRONGBOX). */
  val keySecurityLevel: SecurityLevel = SecurityLevel.UNKNOWN,
  /** Whether encryption keys are stored in secure hardware. */
  val keysAreHardwareBacked: Boolean = false,
)
