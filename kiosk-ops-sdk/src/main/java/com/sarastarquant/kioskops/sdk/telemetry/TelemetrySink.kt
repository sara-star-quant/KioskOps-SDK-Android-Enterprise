/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.telemetry

interface TelemetrySink {
  fun emit(event: String, fields: Map<String, String> = emptyMap())
}
