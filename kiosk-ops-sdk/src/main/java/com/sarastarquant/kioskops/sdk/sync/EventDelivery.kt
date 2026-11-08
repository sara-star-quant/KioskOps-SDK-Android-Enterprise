package com.sarastarquant.kioskops.sdk.sync

/**
 * Final server verdict for one event after a sync batch, reported to [KioskOpsDeliveryListener].
 *
 * @since 1.5.0
 */
enum class DeliveryStatus {
  /** Server accepted the event; it is removed from the queue. */
  DELIVERED,

  /** Server did not accept the event but it will be retried with backoff. */
  RETRY_SCHEDULED,

  /** Event will not be retried (non-retryable rejection or max attempts reached). */
  QUARANTINED,
}

/**
 * Per-event delivery outcome. Carries identifiers and the server's error string only;
 * never the payload.
 *
 * @since 1.5.0
 */
data class EventDelivery(
  val eventId: String,
  val idempotencyKey: String,
  val type: String,
  val status: DeliveryStatus,
  val serverEventId: String? = null,
  val error: String? = null,
)

/**
 * Callback fired once per event after the server acknowledges (or rejects) it, after the local
 * queue state is updated. Batch-level failures (HTTP errors, timeouts) report nothing: the events
 * stay queued and retry. Implementations must be thread-safe; callbacks may fire from background
 * threads. A listener that throws is logged and ignored.
 *
 * @since 1.5.0
 */
fun interface KioskOpsDeliveryListener {
  fun onDelivery(delivery: EventDelivery)
}
