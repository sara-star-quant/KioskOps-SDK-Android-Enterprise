package com.peterz.kioskops.sdk.compliance

data class SecurityPolicy(
  val encryptQueuePayloads: Boolean,
  val encryptTelemetryAtRest: Boolean,
  val encryptDiagnosticsBundle: Boolean,
  val encryptExportedLogs: Boolean,
  val maxEventPayloadBytes: Int,
  val denylistJsonKeys: Set<String>,
  val allowRawPayloadStorage: Boolean,
) {
  companion object {
    fun maximalistDefaults() = SecurityPolicy(
      encryptQueuePayloads = true,
      encryptTelemetryAtRest = true,
      encryptDiagnosticsBundle = true,
      encryptExportedLogs = true,
      maxEventPayloadBytes = 64 * 1024,
      denylistJsonKeys = setOf("email", "phone", "address", "name", "ssn", "id_number"),
      allowRawPayloadStorage = false
    )
  }
}
