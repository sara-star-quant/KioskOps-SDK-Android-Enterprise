package com.peterz.kioskops.sdk.telemetry

import kotlinx.serialization.Serializable

@Serializable
data class TelemetryEvent(
  val ts: Long,
  val name: String,
  val fields: Map<String, String> = emptyMap()
)
