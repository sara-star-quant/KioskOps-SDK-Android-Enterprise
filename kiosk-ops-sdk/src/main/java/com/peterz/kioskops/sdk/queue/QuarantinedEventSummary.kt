package com.peterz.kioskops.sdk.queue

/**
 * Lightweight quarantined event metadata for support tooling.
 * Payload is intentionally excluded.
 */
data class QuarantinedEventSummary(
  val id: String,
  val type: String,
  val createdAtEpochMs: Long,
  val attempts: Int,
  val reason: String,
  val updatedAtEpochMs: Long,
)

internal fun QuarantinedEventRow.toSummary(): QuarantinedEventSummary {
  val r = quarantineReason ?: lastError ?: "quarantined"
  return QuarantinedEventSummary(
    id = id,
    type = type,
    createdAtEpochMs = createdAtEpochMs,
    attempts = attempts,
    reason = r,
    updatedAtEpochMs = updatedAtEpochMs
  )
}
