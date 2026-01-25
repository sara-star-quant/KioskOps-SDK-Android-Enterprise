package com.peterz.kioskops.sdk.fleet

import com.peterz.kioskops.sdk.crypto.SecurityLevel
import com.peterz.kioskops.sdk.fleet.posture.BatteryStatus
import com.peterz.kioskops.sdk.fleet.posture.ConnectivityStatus
import com.peterz.kioskops.sdk.fleet.posture.StorageStatus
import kotlinx.serialization.Serializable

/**
 * Device posture snapshot for fleet operations.
 *
 * Privacy (GDPR Art. 5): Contains no PII. Only aggregate device state.
 * All identifiers (IMEI, serial, MAC) are intentionally excluded.
 *
 * Compliance (ISO 27001 A.8): Device state information for asset management.
 */
@Serializable
data class DevicePosture(
  val isDeviceOwner: Boolean,
  val isLockTaskPermitted: Boolean,
  val androidSdkInt: Int,
  val deviceModel: String,
  val manufacturer: String,
  val securityPatch: String?,

  // v0.2.0 Key Attestation
  /** Whether the device supports hardware-backed key attestation. */
  val supportsHardwareAttestation: Boolean = false,
  /** Security level of SDK encryption keys (SOFTWARE, TEE, or STRONGBOX). */
  val keySecurityLevel: SecurityLevel = SecurityLevel.UNKNOWN,
  /** Whether encryption keys are stored in secure hardware. */
  val keysAreHardwareBacked: Boolean = false,

  // v0.3.0 Extended Posture
  /** Battery status snapshot. */
  val battery: BatteryStatus? = null,
  /** Storage status snapshot. */
  val storage: StorageStatus? = null,
  /** Connectivity status snapshot. */
  val connectivity: ConnectivityStatus? = null,
  /** Device group assignments for fleet segmentation. */
  val deviceGroups: List<String> = emptyList(),
)
