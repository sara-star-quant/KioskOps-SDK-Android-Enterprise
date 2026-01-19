package com.peterz.kioskops.sdk.compliance

data class TelemetryPolicy(
  val enabled: Boolean,
  val includeDeviceId: Boolean,
  val regionTag: String?,
  val allowedKeys: Set<String>,
) {
  companion object {
    fun maximalistDefaults() = TelemetryPolicy(
      enabled = true,
      includeDeviceId = false,
      regionTag = null,
      /**
       * Allow-list beats deny-list for telemetry: safer by default.
       * Keep keys non-sensitive and avoid user/customer identifiers.
       */
      allowedKeys = setOf(
        // event basics
        "type",
        "attempt",
        "reason",

        // versions
        "sdkVersion",
        "appVersion",
        "os",
        "model",
        "manufacturer",
        "securityPatch",

        // operational health
        "queueDepth",
        "lastSyncState",
        "isDeviceOwner",
        "isInLockTaskMode",

        // policy drift
        "policyDrifted",
        "policyHash",
        "policyPrevHash",

        // sync (non-sensitive counters)
        "batchSize",
        "sent",
        "rejected",
        "httpStatus",
        "syncResult",
        "nextBackoffMs"
      )
    )
  }
}
