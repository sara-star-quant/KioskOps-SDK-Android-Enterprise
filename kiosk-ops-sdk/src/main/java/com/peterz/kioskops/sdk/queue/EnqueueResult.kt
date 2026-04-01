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
    data class QueueFull(val reason: String) : Rejected()
    data class DuplicateIdempotency(val reason: String) : Rejected()
    /** @since 0.5.0 */
    data class ValidationFailed(val errors: List<String>) : Rejected()
    /** @since 0.5.0 */
    data class PiiDetected(val findings: List<String>) : Rejected()
    /** @since 0.5.0 */
    data class AnomalyRejected(val score: Float, val reasons: List<String>) : Rejected()
    data class Unknown(val reason: String) : Rejected()
  }

  /**
   * Event was accepted but PII was redacted from the payload.
   * @since 0.5.0
   */
  data class PiiRedacted(
    val id: String,
    val idempotencyKey: String,
    val redactedFields: List<String>,
    val droppedOldest: Int = 0,
  ) : EnqueueResult()

  val isAccepted: Boolean get() = this is Accepted
}
