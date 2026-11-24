/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.telemetry

import com.sarastarquant.kioskops.sdk.compliance.TelemetryPolicy

object TelemetryRedactor {
  fun apply(policy: TelemetryPolicy, fields: Map<String, String>): Map<String, String> {
    if (!policy.enabled) return emptyMap()
    // Allow-list beats deny-list for telemetry. Anything not explicitly allowed is dropped.
    return fields.filterKeys { it in policy.allowedKeys }
  }
}
