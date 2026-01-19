package com.peterz.kioskops.sdk.queue

/**
 * Result of an enqueue attempt.
 *
 * The SDK historically returned a Boolean. For enterprise pilots, callers typically want
 * a reason when an event is rejected (e.g. policy denylist or local storage quotas).
 */
sealed class EnqueueResult {
  data class Accepted(
    val id: String,
    val idempotencyKey: String,
    /** Number of oldest events dropped to make room under [QueueLimits]. */
    val droppedOldest: Int = 0
  ) : EnqueueResult()

  sealed class Rejected : EnqueueResult() {
    data class PayloadTooLarge(val bytes: Int, val max: Int) : Rejected()
    data class DenylistedKey(val key: String) : Rejected()
    data class QueueFull(val reason: String) : Rejected()
    data class DuplicateIdempotency(val reason: String) : Rejected()
    data class Unknown(val reason: String) : Rejected()
  }

  val isAccepted: Boolean get() = this is Accepted
}
