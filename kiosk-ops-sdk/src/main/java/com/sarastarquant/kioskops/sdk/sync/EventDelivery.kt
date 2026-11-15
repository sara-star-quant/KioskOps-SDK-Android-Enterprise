package com.sarastarquant.kioskops.sdk.sync

/**
 * Outcome for one event after a sync batch, reported to [KioskOpsDeliveryListener]. Usually the
 * server's verdict; an event whose stored payload cannot be decoded is never sent and is reported
 * locally as [RETRY_SCHEDULED] or [QUARANTINED] with error `payload_decode_failed`.
 *
 * @since 1.5.0
 */
enum class DeliveryStatus {
  /** Server accepted the event; it is removed from the queue. */
  DELIVERED,

  /** Not delivered; it will be retried with backoff. */
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
 * Callback fired once per event after the local queue state is updated: when the server acknowledges
 * or rejects it, or when its payload cannot be decoded (error `payload_decode_failed`; the server
 * never sees that event, and a batch in which no event decodes makes no transport call at all).
 * Batch-level failures (HTTP errors, timeouts) report nothing: the events stay queued and retry.
 * Implementations must be thread-safe; callbacks may fire from background threads. A listener that
 * throws is logged and ignored.
 *
 * The listener runs while the sync lock is held. A listener that calls `syncOnce()` synchronously
 * or waits on `syncOnceBlocking()` never returns; hand such work to another coroutine or thread.
 *
 * @since 1.5.0
 */
fun interface KioskOpsDeliveryListener {
  fun onDelivery(delivery: EventDelivery)
}
