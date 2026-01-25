package com.peterz.kioskops.sdk.fleet

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import com.peterz.kioskops.sdk.crypto.KeyAttestationReporter
import com.peterz.kioskops.sdk.crypto.SecurityLevel

/**
 * Collects a minimal device posture snapshot suitable for fleet ops.
 *
 * Notes:
 * - Intentionally avoids stable identifiers (IMEI/serial/SSAID).
 * - Device Owner status is a key signal for kiosk reliability.
 * - Includes key attestation status for security compliance reporting.
 */
class DevicePostureCollector(private val context: Context) {

  private val attestationReporter: KeyAttestationReporter by lazy {
    KeyAttestationReporter(context)
  }

  fun collect(): DevicePosture {
    val dpm = context.getSystemService(DevicePolicyManager::class.java)
    val am = context.getSystemService(ActivityManager::class.java)

    val isDeviceOwner = runCatching { dpm.isDeviceOwnerApp(context.packageName) }.getOrDefault(false)

    // ActivityManager#isInLockTaskMode is API 23; on newer devices it can be restricted.
    val lockTaskPermitted = runCatching { am.isInLockTaskMode }.getOrDefault(false)

    val sdkInt = android.os.Build.VERSION.SDK_INT
    val model = android.os.Build.MODEL ?: "unknown"
    val manufacturer = android.os.Build.MANUFACTURER ?: "unknown"
    val patch = runCatching { android.os.Build.VERSION.SECURITY_PATCH }.getOrNull()

    // Collect key attestation status
    val supportsHwAttestation = runCatching {
      attestationReporter.isHardwareAttestationSupported()
    }.getOrDefault(false)

    // Check primary SDK key attestation status
    val queueKeyStatus = runCatching {
      attestationReporter.getAttestationStatus("kioskops_queue_aesgcm_v1")
    }.getOrNull()

    val keySecurityLevel = queueKeyStatus?.securityLevel ?: SecurityLevel.UNKNOWN
    val keysHardwareBacked = queueKeyStatus?.isHardwareBacked ?: false

    return DevicePosture(
      isDeviceOwner = isDeviceOwner,
      isLockTaskPermitted = lockTaskPermitted,
      androidSdkInt = sdkInt,
      deviceModel = model,
      manufacturer = manufacturer,
      securityPatch = patch,
      supportsHardwareAttestation = supportsHwAttestation,
      keySecurityLevel = keySecurityLevel,
      keysAreHardwareBacked = keysHardwareBacked,
    )
  }
}
