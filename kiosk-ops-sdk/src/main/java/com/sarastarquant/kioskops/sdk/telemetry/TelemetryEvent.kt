/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.telemetry

import kotlinx.serialization.Serializable

@Serializable
data class TelemetryEvent(
  val ts: Long,
  val name: String,
  val fields: Map<String, String> = emptyMap()
)
