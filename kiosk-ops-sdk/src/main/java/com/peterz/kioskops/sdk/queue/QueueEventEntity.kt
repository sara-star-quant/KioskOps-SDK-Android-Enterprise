package com.peterz.kioskops.sdk.queue

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "queue_events",
  indices = [
    Index(value = ["state"]),
    Index(value = ["createdAtEpochMs"]),
    Index(value = ["idempotencyKey"], unique = true)
  ]
)
data class QueueEventEntity(
  @PrimaryKey val id: String,
  val idempotencyKey: String,
  val type: String,
  val payloadBlob: ByteArray,
  val payloadEncoding: String,
  /** Payload size in bytes, stored for fast quota checks. */
  val payloadBytes: Int,
  val createdAtEpochMs: Long,
  val state: String,
  val attempts: Int,
  /**
   * Backoff gate. Event is eligible for send when nextAttemptAtEpochMs <= now.
   *
   * Default is 0 for immediate eligibility.
   */
  val nextAttemptAtEpochMs: Long,

  /**
   * 1 = permanently failed (do not retry). 0 = retryable.
   *
   * This is a pragmatic enterprise knob: if the server rejects an event due to
   * schema/validation (4xx), you don't want infinite retries.
   */
  val permanentFailure: Int,
  val lastError: String? = null,
  /** Set when [permanentFailure] is 1 to explain why the event was quarantined. */
  val quarantineReason: String? = null,
  val updatedAtEpochMs: Long,
)

object QueueStates {
  const val PENDING = "PENDING"
  const val SENDING = "SENDING"
  const val SENT = "SENT"
  const val FAILED = "FAILED"
  /**
   * Quarantined events are not retried automatically.
   * They typically indicate validation/schema issues or max-attempt exhaustion.
   */
  const val QUARANTINED = "QUARANTINED"
}
