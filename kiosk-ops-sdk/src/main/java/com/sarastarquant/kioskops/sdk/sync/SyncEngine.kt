package com.sarastarquant.kioskops.sdk.sync

import android.content.Context
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.audit.PersistentAuditTrail
import com.sarastarquant.kioskops.sdk.audit.core.AuditCheckpoint
import com.sarastarquant.kioskops.sdk.logging.RingLog
import com.sarastarquant.kioskops.sdk.queue.QueueEventEntity
import com.sarastarquant.kioskops.sdk.queue.QueueRepository
import com.sarastarquant.kioskops.sdk.telemetry.TelemetrySink
import com.sarastarquant.kioskops.sdk.transport.BatchSendRequest
import com.sarastarquant.kioskops.sdk.transport.BatchSendResponse
import com.sarastarquant.kioskops.sdk.transport.EventAck
import com.sarastarquant.kioskops.sdk.transport.Transport
import com.sarastarquant.kioskops.sdk.transport.TransportEvent
import com.sarastarquant.kioskops.sdk.transport.TransportResult
import com.sarastarquant.kioskops.sdk.util.Clock
import com.sarastarquant.kioskops.sdk.util.DeviceId
import com.sarastarquant.kioskops.sdk.util.Ids
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Fleet-ops sync engine.
 *
 * Design goals:
 * - host-controlled transport (auth headers, data residency)
 * - idempotent server contract (id + idempotencyKey)
 * - explicit transient vs permanent error classification
 * - local-only observability (telemetry/audit) without sending PII
 *
 * **Ordering guarantee**: Events within a single batch are delivered in enqueue order.
 * However, if a batch fails and is retried, a newer batch may be delivered before the
 * retried batch. Consumers that require strict global ordering should implement
 * server-side sequence checks using [com.sarastarquant.kioskops.sdk.queue.QueueEventEntity.createdAtEpochMs].
 */
class SyncEngine @JvmOverloads constructor(
  private val context: Context,
  private val cfgProvider: () -> KioskOpsConfig,
  private val queue: QueueRepository,
  private val transport: Transport,
  private val logs: RingLog,
  private val telemetry: TelemetrySink,
  private val audit: PersistentAuditTrail,
  private val clock: Clock,
  private val onDelivery: (EventDelivery) -> Unit = {},
) {
  private val flushLock = Mutex()

  /**
   * Send one batch. Calls are serialized: a second caller waits until the first finishes, so audit
   * checkpoints reach the server in the order the device created them.
   */
  suspend fun flushOnce(): TransportResult<SyncOnceResult> = flushLock.withLock { flushOnceLocked() }

  private suspend fun flushOnceLocked(): TransportResult<SyncOnceResult> {
    val cfg = cfgProvider()
    if (!cfg.syncPolicy.enabled) return idle()

    val baseUrl = cfg.baseUrl.trim()
    if (baseUrl.isBlank() || baseUrl.contains("example.invalid")) {
      // Do NOT touch the queue if config is missing.
      return TransportResult.PermanentFailure("baseUrl_not_configured")
    }

    val now = clock.nowMs()
    val batch = selectBatch(cfg, now)
    if (batch.isEmpty()) return idle()

    audit.record("sync_batch_start", mapOf("batchSize" to batch.size.toString()))
    telemetry.emit("sync_batch_start", mapOf("batchSize" to batch.size.toString(), "syncResult" to "start"))

    // Events whose payload cannot be decoded are never sent; they are settled as rejected acks below.
    val events = decodeEvents(batch)
    val sendable = batch.filter { it.id in events }

    // The checkpoint is taken before any event is marked SENDING, so a signing failure strands nothing.
    @Suppress("TooGenericExceptionCaught")
    val checkpoint = try {
      if (sendable.isEmpty()) null else createCheckpoint(cfg)
    } catch (e: CancellationException) {
      throw e
    } catch (t: Throwable) {
      logs.w("Sync", "Audit checkpoint failed: ${t::class.java.simpleName}")
      return onBatchTransientFailure(batch, TransportResult.TransientFailure("checkpoint_failed", cause = t), now)
    }
    val res = if (sendable.isEmpty()) {
      TransportResult.Success(BatchSendResponse(acceptedCount = 0, acks = emptyList()))
    } else {
      sendInFlight(cfg, buildRequest(cfg, events.values.toList(), now, checkpoint), sendable, now)
    }

    // The server has answered: record that answer to completion even if the caller is cancelled.
    return withContext(NonCancellable) {
      when (res) {
        is TransportResult.Success -> {
          checkpoint?.let { acknowledgeCheckpoint(it) }
          onBatchSuccess(cfg, batch, res, now, undecodable = batch.filter { it.id !in events })
        }
        is TransportResult.TransientFailure -> onBatchTransientFailure(batch, res, now)
        is TransportResult.PermanentFailure -> onBatchPermanentFailure(batch, res, now)
      }
    }
  }

  private fun idle(): TransportResult<SyncOnceResult> = TransportResult.Success(
    value = SyncOnceResult(attempted = 0, sent = 0, permanentFailed = 0, transientFailed = 0, rejected = 0)
  )

  private suspend fun selectBatch(cfg: KioskOpsConfig, now: Long): List<QueueEventEntity> =
    queue.nextBatch(nowMs = now, limit = cfg.syncPolicy.batchSize.coerceAtLeast(1))

  private suspend fun createCheckpoint(cfg: KioskOpsConfig): AuditCheckpoint? =
    if (cfg.securityPolicy.signedAuditCheckpoints) audit.createCheckpoint(sign = true) else null

  /** Decoded events by id, in batch order. An event whose payload fails to decode is left out. */
  private suspend fun decodeEvents(batch: List<QueueEventEntity>): Map<String, TransportEvent> {
    val out = LinkedHashMap<String, TransportEvent>(batch.size)
    for (e in batch) {
      @Suppress("TooGenericExceptionCaught")
      try {
        out[e.id] = TransportEvent(
          id = e.id,
          idempotencyKey = e.idempotencyKey,
          type = e.type,
          payloadJson = queue.decodePayloadJson(e),
          createdAtEpochMs = e.createdAtEpochMs
        )
      } catch (c: CancellationException) {
        throw c
      } catch (t: Throwable) {
        logs.w("Sync", "Payload decode failed: ${t::class.java.simpleName}")
      }
    }
    return out
  }

  private fun buildRequest(
    cfg: KioskOpsConfig,
    events: List<TransportEvent>,
    now: Long,
    checkpoint: AuditCheckpoint?,
  ) = BatchSendRequest(
    batchId = Ids.uuid(),
    deviceId = DeviceId.get(context),
    appVersion = appVersionSafe(context),
    locationId = cfg.locationId,
    sentAtEpochMs = now,
    events = events,
    auditCheckpoint = checkpoint,
  )

  /**
   * Mark the batch SENDING and send it. If the caller is cancelled while the batch is in flight, the
   * whole batch returns to retry eligibility immediately, without an attempt bump, before rethrowing.
   */
  private suspend fun sendInFlight(
    cfg: KioskOpsConfig,
    request: BatchSendRequest,
    batch: List<QueueEventEntity>,
    now: Long,
  ): TransportResult<BatchSendResponse> = try {
    // Mark as SENDING to reduce accidental double-sends within a single process.
    for (e in batch) queue.markSending(e.id)
    sendGuarded(cfg, request)
  } catch (e: CancellationException) {
    withContext(NonCancellable) {
      for (ev in batch) {
        // A failed reset must not mask the cancellation or skip the remaining rows.
        @Suppress("TooGenericExceptionCaught")
        try {
          queue.markBatchFailureNoAttemptBump(ev.id, "cancelled", nextAttemptAtMs = now)
        } catch (t: Throwable) {
          e.addSuppressed(t)
        }
      }
    }
    throw e
  }

  /**
   * A transport that throws instead of returning a result is treated as a transient batch failure. That
   * includes a CancellationException the transport raised itself (for example a timeout inside it) while
   * this coroutine is still active; only a cancellation of the flush itself propagates.
   */
  @Suppress("TooGenericExceptionCaught")
  private suspend fun sendGuarded(cfg: KioskOpsConfig, request: BatchSendRequest): TransportResult<BatchSendResponse> =
    try {
      transport.sendBatch(cfg, request)
    } catch (e: CancellationException) {
      if (!currentCoroutineContext().isActive) throw e
      transportException(e)
    } catch (t: Throwable) {
      transportException(t)
    }

  private fun transportException(t: Throwable) =
    TransportResult.TransientFailure(message = "transport_exception: ${t::class.java.simpleName}", cause = t)

  /**
   * Called only on HTTP success. A failure is logged and never blocks the batch. One lost acknowledgement is
   * covered by the server's previous-pin rule; repeated loss makes the server report Forked.
   */
  @Suppress("TooGenericExceptionCaught")
  private suspend fun acknowledgeCheckpoint(checkpoint: AuditCheckpoint) {
    try {
      audit.acknowledgeCheckpoint(checkpoint)
    } catch (e: CancellationException) {
      throw e
    } catch (t: Throwable) {
      logs.w("Sync", "Audit checkpoint acknowledgement failed: ${t::class.java.simpleName}")
    }
  }

  private suspend fun onBatchSuccess(
    cfg: KioskOpsConfig,
    batch: List<QueueEventEntity>,
    res: TransportResult.Success<BatchSendResponse>,
    now: Long,
    undecodable: List<QueueEventEntity>,
  ): TransportResult<SyncOnceResult> {
    val ackById = res.value.acks.associateBy { it.id } + undecodable.associate { it.id to decodeFailureAck(it) }
    var sent = 0
    var rejected = 0
    var permanentFailed = 0
    var transientFailed = 0
    val deliveries = ArrayList<EventDelivery>(batch.size)

    for (e in batch) {
      val ack = ackById[e.id]
      if (ack != null && !ack.accepted) rejected++
      val decision = SyncDecisions.decideFromAck(e.attempts, ack, cfg.syncPolicy.maxAttemptsPerEvent, now)
      when (decision) {
        is EventDecision.Sent -> sent++
        is EventDecision.RetryAt -> transientFailed++
        is EventDecision.Quarantine -> permanentFailed++
      }
      deliveries += applyOutcome(e, decision)
    }

    val httpStatus = res.httpStatus.toString()
    audit.record(
      "sync_batch_success",
      mapOf(
        "attempted" to batch.size.toString(),
        "sent" to sent.toString(),
        "rejected" to rejected.toString(),
        "httpStatus" to httpStatus
      )
    )
    telemetry.emit(
      "sync_batch_success",
      mapOf(
        "batchSize" to batch.size.toString(),
        "sent" to sent.toString(),
        "rejected" to rejected.toString(),
        "httpStatus" to httpStatus,
        "syncResult" to "success"
      )
    )
    notifyDeliveries(deliveries)

    return TransportResult.Success(
      value = SyncOnceResult(batch.size, sent, permanentFailed, transientFailed, rejected),
      httpStatus = res.httpStatus
    )
  }

  /** An undecodable event is settled like a retryable rejection: backoff with an attempt bump, quarantine at the cap. */
  private fun decodeFailureAck(e: QueueEventEntity) =
    EventAck(id = e.id, idempotencyKey = e.idempotencyKey, accepted = false, retryable = true, error = "payload_decode_failed")

  /** Store one event's decision and return the delivery the host is told about. */
  private suspend fun applyOutcome(e: QueueEventEntity, decision: EventDecision): EventDelivery = when (decision) {
    is EventDecision.Sent -> {
      queue.markSent(e.id)
      EventDelivery(e.id, e.idempotencyKey, e.type, DeliveryStatus.DELIVERED, serverEventId = decision.serverEventId)
    }
    is EventDecision.RetryAt -> {
      if (decision.bumpAttempts) {
        queue.markFailed(e.id, decision.error, nextAttemptAtMs = decision.atMs, permanentFailure = 0)
      } else {
        queue.markBatchFailureNoAttemptBump(e.id, decision.error, nextAttemptAtMs = decision.atMs)
      }
      EventDelivery(e.id, e.idempotencyKey, e.type, DeliveryStatus.RETRY_SCHEDULED, error = decision.error)
    }
    is EventDecision.Quarantine -> {
      queue.markFailed(
        id = e.id,
        err = decision.error,
        quarantineReason = decision.reason,
        nextAttemptAtMs = Long.MAX_VALUE,
        permanentFailure = 1
      )
      EventDelivery(e.id, e.idempotencyKey, e.type, DeliveryStatus.QUARANTINED, error = decision.error)
    }
  }

  /**
   * Batch-level failure is not the events' fault: back each event off without bumping attempts, so a
   * long outage does not march the backlog to maxAttemptsPerEvent. Returns the longest delay applied.
   */
  private suspend fun backOffBatch(batch: List<QueueEventEntity>, message: String, now: Long): Long {
    var longest = 0L
    for (e in batch) {
      val decision = SyncDecisions.decideBatchFailure(e.attempts, message, now)
      longest = maxOf(longest, decision.atMs - now)
      applyOutcome(e, decision)
    }
    return longest
  }

  private suspend fun onBatchTransientFailure(
    batch: List<QueueEventEntity>,
    res: TransportResult.TransientFailure,
    now: Long,
  ): TransportResult<SyncOnceResult> {
    val nextBackoffMs = backOffBatch(batch, res.message, now)
    val httpStatus = res.httpStatus?.toString() ?: ""
    audit.record("sync_batch_transient_failure", mapOf("httpStatus" to httpStatus))
    telemetry.emit(
      "sync_batch_transient_failure",
      mapOf(
        "batchSize" to batch.size.toString(),
        "httpStatus" to httpStatus,
        "nextBackoffMs" to nextBackoffMs.toString(),
        "syncResult" to "transient_failure"
      )
    )
    logs.w("Sync", "Transient failure; retry in ${nextBackoffMs}ms")
    return TransportResult.TransientFailure(message = res.message, httpStatus = res.httpStatus, cause = res.cause)
  }

  /** Auth, config, or schema errors: the operator may fix config and retry, so events are not quarantined. */
  private suspend fun onBatchPermanentFailure(
    batch: List<QueueEventEntity>,
    res: TransportResult.PermanentFailure,
    now: Long,
  ): TransportResult<SyncOnceResult> {
    backOffBatch(batch, res.message, now)
    val httpStatus = res.httpStatus?.toString() ?: ""
    audit.record("sync_batch_permanent_failure", mapOf("httpStatus" to httpStatus))
    telemetry.emit(
      "sync_batch_permanent_failure",
      mapOf(
        "batchSize" to batch.size.toString(),
        "httpStatus" to httpStatus,
        "syncResult" to "permanent_failure"
      )
    )
    return TransportResult.PermanentFailure(message = res.message, httpStatus = res.httpStatus, cause = res.cause)
  }

  private fun notifyDeliveries(deliveries: List<EventDelivery>) {
    for (d in deliveries) {
      @Suppress("TooGenericExceptionCaught")
      try {
        onDelivery(d)
      } catch (t: Throwable) {
        // Host callback must not break sync; surface the bug at warn level (no payload).
        logs.w("Sync", "Delivery listener threw: ${t::class.java.simpleName}: ${t.message ?: "no message"}")
      }
    }
  }

  private fun appVersionSafe(ctx: Context): String {
    return try {
      val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
      p.versionName ?: "0"
    } catch (_: Throwable) {
      "0"
    }
  }
}
