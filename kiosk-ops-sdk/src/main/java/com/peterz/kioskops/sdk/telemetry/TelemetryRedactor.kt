package com.peterz.kioskops.sdk.telemetry

import com.peterz.kioskops.sdk.compliance.TelemetryPolicy

object TelemetryRedactor {
  fun apply(policy: TelemetryPolicy, fields: Map<String, String>): Map<String, String> {
    if (!policy.enabled) return emptyMap()
    // Allow-list beats deny-list for telemetry. Anything not explicitly allowed is dropped.
    return fields.filterKeys { it in policy.allowedKeys }
  }
}
