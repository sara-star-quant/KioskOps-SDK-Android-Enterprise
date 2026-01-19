package com.peterz.kioskops.sdk.fleet

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context

/**
 * Collects a minimal device posture snapshot suitable for fleet ops.
 *
 * Notes:
 * - Intentionally avoids stable identifiers (IMEI/serial/SSAID).
 * - Device Owner status is a key signal for kiosk reliability.
 */
class DevicePostureCollector(private val context: Context) {

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

    return DevicePosture(
      isDeviceOwner = isDeviceOwner,
      isLockTaskPermitted = lockTaskPermitted,
      androidSdkInt = sdkInt,
      deviceModel = model,
      manufacturer = manufacturer,
      securityPatch = patch
    )
  }
}
