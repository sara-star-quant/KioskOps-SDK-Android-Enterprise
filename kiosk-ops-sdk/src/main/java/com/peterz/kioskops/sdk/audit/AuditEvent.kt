package com.peterz.kioskops.sdk.audit

import kotlinx.serialization.Serializable

@Serializable
data class AuditEvent(
  val ts: Long,
  val name: String,
  val fields: Map<String, String> = emptyMap(),
  val prevHash: String,
  val hash: String,
)
