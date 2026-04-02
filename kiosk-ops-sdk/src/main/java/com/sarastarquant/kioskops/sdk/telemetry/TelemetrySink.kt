package com.sarastarquant.kioskops.sdk.telemetry

interface TelemetrySink {
  fun emit(event: String, fields: Map<String, String> = emptyMap())
}
