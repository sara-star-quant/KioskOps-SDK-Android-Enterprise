/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.transport

import com.sarastarquant.kioskops.sdk.audit.core.AuditCheckpoint
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
  val events: List<TransportEvent>,
  /**
   * Device audit chain checkpoint; set only when [com.sarastarquant.kioskops.sdk.compliance.SecurityPolicy.signedAuditCheckpoints]
   * is on. Omitted from the JSON body when null.
   * @since 1.6.0
   */
  val auditCheckpoint: AuditCheckpoint? = null,
) {
  /** Binary compatibility with callers compiled before [auditCheckpoint] existed. */
  constructor(
    batchId: String,
    deviceId: String,
    appVersion: String,
    locationId: String,
    sentAtEpochMs: Long,
    events: List<TransportEvent>,
  ) : this(batchId, deviceId, appVersion, locationId, sentAtEpochMs, events, null)

  /** Binary compatibility with callers compiled before [auditCheckpoint] existed. */
  @Deprecated("kept for binary compatibility", level = DeprecationLevel.HIDDEN)
  fun copy(
    batchId: String = this.batchId,
    deviceId: String = this.deviceId,
    appVersion: String = this.appVersion,
    locationId: String = this.locationId,
    sentAtEpochMs: Long = this.sentAtEpochMs,
    events: List<TransportEvent> = this.events,
  ): BatchSendRequest = copy(batchId, deviceId, appVersion, locationId, sentAtEpochMs, events, auditCheckpoint)
}

@Serializable
data class EventAck(
  val id: String,
  val idempotencyKey: String,
  val accepted: Boolean,
  val retryable: Boolean = true,
  val error: String? = null,
  val serverEventId: String? = null
)

@Serializable
data class BatchSendResponse(
  val acceptedCount: Int,
  val acks: List<EventAck>
)
