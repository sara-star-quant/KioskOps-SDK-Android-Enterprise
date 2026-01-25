/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.audit

/**
 * Result of audit chain integrity verification.
 *
 * The hash chain links each audit event to its predecessor,
 * making tampering detectable (though not impossible).
 */
sealed class ChainVerificationResult {

  /**
   * The audit chain is valid.
   *
   * All events in the specified range have valid hash links.
   *
   * @property eventCount Number of events verified.
   * @property fromTs Start timestamp of verified range.
   * @property toTs End timestamp of verified range.
   */
  data class Valid(
    val eventCount: Int,
    val fromTs: Long,
    val toTs: Long,
  ) : ChainVerificationResult()

  /**
   * The audit chain has a break.
   *
   * An event was found whose prevHash doesn't match the
   * hash of the preceding event. This indicates tampering
   * or data corruption.
   *
   * @property brokenAtId ID of the event where the chain breaks.
   * @property brokenAtTs Timestamp of the broken event.
   * @property expectedPrevHash The hash that was expected.
   * @property actualPrevHash The hash that was found.
   */
  data class Broken(
    val brokenAtId: String,
    val brokenAtTs: Long,
    val expectedPrevHash: String,
    val actualPrevHash: String,
  ) : ChainVerificationResult()

  /**
   * The specified range contains no events.
   *
   * This is not an error - just means there's nothing to verify.
   *
   * @property fromTs Start of the requested range.
   * @property toTs End of the requested range.
   */
  data class EmptyRange(
    val fromTs: Long,
    val toTs: Long,
  ) : ChainVerificationResult()

  /**
   * An event's computed hash doesn't match its stored hash.
   *
   * This is a more severe issue than a chain break - the event
   * itself has been modified.
   *
   * @property eventId ID of the corrupted event.
   * @property eventTs Timestamp of the corrupted event.
   */
  data class HashMismatch(
    val eventId: String,
    val eventTs: Long,
  ) : ChainVerificationResult()

  /**
   * Signature verification failed for a signed event.
   *
   * @property eventId ID of the event with invalid signature.
   * @property eventTs Timestamp of the event.
   * @property reason Description of the verification failure.
   */
  data class SignatureInvalid(
    val eventId: String,
    val eventTs: Long,
    val reason: String,
  ) : ChainVerificationResult()
}

/**
 * Statistics about the audit trail.
 */
data class AuditStatistics(
  /** Total number of audit events. */
  val totalEvents: Long,
  /** Timestamp of the oldest event, or null if empty. */
  val oldestEventTs: Long?,
  /** Timestamp of the newest event, or null if empty. */
  val newestEventTs: Long?,
  /** Current chain generation (increments after chain breaks). */
  val chainGeneration: Int,
  /** Number of signed events. */
  val signedEventCount: Long,
  /** Breakdown of event counts by name. */
  val eventsByName: Map<String, Int>,
)
