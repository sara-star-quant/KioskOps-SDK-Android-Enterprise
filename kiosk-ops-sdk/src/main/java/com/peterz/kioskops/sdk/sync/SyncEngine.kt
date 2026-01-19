package com.peterz.kioskops.sdk.sync

import android.content.Context
import com.peterz.kioskops.sdk.KioskOpsConfig
import com.peterz.kioskops.sdk.audit.AuditTrail
import com.peterz.kioskops.sdk.logging.RingLog
import com.peterz.kioskops.sdk.queue.QueueRepository
import com.peterz.kioskops.sdk.telemetry.TelemetrySink
import com.peterz.kioskops.sdk.transport.BatchSendRequest
import com.peterz.kioskops.sdk.transport.Transport
import com.peterz.kioskops.sdk.transport.TransportEvent
import com.peterz.kioskops.sdk.transport.TransportResult
import com.peterz.kioskops.sdk.util.Clock
import com.peterz.kioskops.sdk.util.DeviceId
import com.peterz.kioskops.sdk.util.Ids

/**
 * Fleet-ops sync engine.
 *
 * Design goals:
 * - host-controlled transport (auth headers, data residency)
 * - idempotent server contract (id + idempotencyKey)
 * - explicit transient vs permanent error classification
 * - local-only observability (telemetry/audit) without sending PII
 */
class SyncEngine(
  private val context: Context,
  private val cfgProvider: () -> KioskOpsConfig,
  private val queue: QueueRepository,
  private val transport: Transport,
  private val logs: RingLog,
  private val telemetry: TelemetrySink,
  private val audit: AuditTrail,
  private val clock: Clock,
) {
  suspend fun flushOnce(): TransportResult<SyncOnceResult> {
    val cfg = cfgProvider()
    if (!cfg.syncPolicy.enabled) {
      return TransportResult.Success(
        value = SyncOnceResult(attempted = 0, sent = 0, permanentFailed = 0, transientFailed = 0, rejected = 0)
      )
    }

    val baseUrl = cfg.baseUrl.trim()
    if (baseUrl.isBlank() || baseUrl.contains("example.invalid")) {
      // Do NOT touch the queue if config is missing.
      return TransportResult.PermanentFailure("baseUrl_not_configured")
    }

    val now = clock.nowMs()
    val batch = queue.nextBatch(nowMs = now, limit = cfg.syncPolicy.batchSize.coerceAtLeast(1))
    if (batch.isEmpty()) {
      return TransportResult.Success(
        value = SyncOnceResult(attempted = 0, sent = 0, permanentFailed = 0, transientFailed = 0, rejected = 0)
      )
    }

    audit.record("sync_batch_start", mapOf("batchSize" to batch.size.toString()))
    telemetry.emit("sync_batch_start", mapOf("batchSize" to batch.size.toString(), "syncResult" to "start"))

    // Mark as SENDING to reduce accidental double-sends within a single process.
    for (e in batch) queue.markSending(e.id)

    val request = BatchSendRequest(
      batchId = Ids.uuid(),
      deviceId = DeviceId.get(context),
      appVersion = appVersionSafe(context),
      locationId = cfg.locationId,
      sentAtEpochMs = now,
      events = batch.map {
        TransportEvent(
          id = it.id,
          idempotencyKey = it.idempotencyKey,
          type = it.type,
          payloadJson = queue.decodePayloadJson(it),
          createdAtEpochMs = it.createdAtEpochMs
        )
      }
    )

    val res = transport.sendBatch(cfg, request)

    return when (res) {
      is TransportResult.Success -> {
        val resp = res.value
        val ackById = resp.acks.associateBy { it.id }

        var sent = 0
        var rejected = 0
        var permanentFailed = 0
        var transientFailed = 0

        for (e in batch) {
          val ack = ackById[e.id]
          if (ack == null) {
            val next = now + Backoff.nextDelayMs(e.attempts)
            queue.markFailed(e.id, "missing_ack", nextAttemptAtMs = next, permanentFailure = 0)
            transientFailed++
            continue
          }

          if (ack.accepted) {
            queue.markSent(e.id)
            sent++
            continue
          }

          rejected++
          val maxedOut = (e.attempts + 1) >= cfg.syncPolicy.maxAttemptsPerEvent
          val isPermanent = (!ack.retryable) || maxedOut
          val permanentFlag = if (isPermanent) 1 else 0

          val nextAttempt = if (isPermanent) Long.MAX_VALUE else now + Backoff.nextDelayMs(e.attempts)
          val quarantineReason = when {
            !ack.retryable -> "server_non_retryable"
            maxedOut -> "max_attempts_exceeded"
            else -> null
          }
          queue.markFailed(
            id = e.id,
            err = ack.error ?: if (maxedOut) "max_attempts_exceeded" else "rejected",
            quarantineReason = quarantineReason,
            nextAttemptAtMs = nextAttempt,
            permanentFailure = permanentFlag
          )

          if (isPermanent) permanentFailed++ else transientFailed++
        }

        audit.record(
          "sync_batch_success",
          mapOf(
            "attempted" to batch.size.toString(),
            "sent" to sent.toString(),
            "rejected" to rejected.toString(),
            "httpStatus" to res.httpStatus.toString()
          )
        )

        telemetry.emit(
          "sync_batch_success",
          mapOf(
            "batchSize" to batch.size.toString(),
            "sent" to sent.toString(),
            "rejected" to rejected.toString(),
            "httpStatus" to res.httpStatus.toString(),
            "syncResult" to "success"
          )
        )

        TransportResult.Success(
          value = SyncOnceResult(
            attempted = batch.size,
            sent = sent,
            permanentFailed = permanentFailed,
            transientFailed = transientFailed,
            rejected = rejected
          ),
          httpStatus = res.httpStatus
        )
      }

      is TransportResult.TransientFailure -> {
        var nextBackoffMs = 0L
        for (e in batch) {
          val delay = Backoff.nextDelayMs(e.attempts)
          nextBackoffMs = maxOf(nextBackoffMs, delay)
          queue.markFailed(e.id, res.message, nextAttemptAtMs = now + delay, permanentFailure = 0)
        }

        audit.record("sync_batch_transient_failure", mapOf("httpStatus" to (res.httpStatus?.toString() ?: "")))
        telemetry.emit(
          "sync_batch_transient_failure",
          mapOf(
            "batchSize" to batch.size.toString(),
            "httpStatus" to (res.httpStatus?.toString() ?: ""),
            "nextBackoffMs" to nextBackoffMs.toString(),
            "syncResult" to "transient_failure"
          )
        )

        logs.w("Sync", "Transient failure; retry in ${nextBackoffMs}ms")
        TransportResult.TransientFailure(message = res.message, httpStatus = res.httpStatus, cause = res.cause)
      }

      is TransportResult.PermanentFailure -> {
        // Auth/config/schema errors. Do not auto-perma-fail queued events here;
        // the operator may fix config and retry.
        for (e in batch) {
          val delay = Backoff.nextDelayMs(e.attempts)
          queue.markFailed(e.id, res.message, nextAttemptAtMs = now + delay, permanentFailure = 0)
        }

        audit.record("sync_batch_permanent_failure", mapOf("httpStatus" to (res.httpStatus?.toString() ?: "")))
        telemetry.emit(
          "sync_batch_permanent_failure",
          mapOf(
            "batchSize" to batch.size.toString(),
            "httpStatus" to (res.httpStatus?.toString() ?: ""),
            "syncResult" to "permanent_failure"
          )
        )

        TransportResult.PermanentFailure(message = res.message, httpStatus = res.httpStatus, cause = res.cause)
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
