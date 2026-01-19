package com.peterz.kioskops.sdk.sync

/**
 * Outcome of one sync flush attempt.
 *
 * These are counters only (no payloads, no identifiers) to keep telemetry safe.
 */
data class SyncOnceResult(
  val attempted: Int,
  val sent: Int,
  val permanentFailed: Int,
  val transientFailed: Int,
  val rejected: Int,
)
