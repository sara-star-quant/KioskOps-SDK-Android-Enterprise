package com.peterz.kioskops.sdk.fleet

data class DevicePosture(
  val isDeviceOwner: Boolean,
  val isLockTaskPermitted: Boolean,
  val androidSdkInt: Int,
  val deviceModel: String,
  val manufacturer: String,
  val securityPatch: String?,
)
