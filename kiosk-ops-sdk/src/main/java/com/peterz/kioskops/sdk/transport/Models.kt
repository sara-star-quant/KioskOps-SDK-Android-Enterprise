package com.peterz.kioskops.sdk.transport

import kotlinx.serialization.Serializable

@Serializable
data class TransportEvent(
  val id: String,
  val idempotencyKey: String,
  val type: String,
  /** Serialized JSON payload (kept as a string to allow arbitrary schemas). */
  val payloadJson: String,
  val createdAtEpochMs: Long,
)

@Serializable
data class BatchSendRequest(
  val batchId: String,
  val deviceId: String,
  val appVersion: String,
  val locationId: String,
  val sentAtEpochMs: Long,
  val events: List<TransportEvent>
)

@Serializable
data class EventAck(
  val id: String,
  val idempotencyKey: String,
  val accepted: Boolean,
  val error: String? = null,
  val serverEventId: String? = null
)

@Serializable
data class BatchSendResponse(
  val acceptedCount: Int,
  val acks: List<EventAck>
)
