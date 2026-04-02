package com.sarastarquant.kioskops.sdk.queue

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.crypto.CryptoProvider
import com.sarastarquant.kioskops.sdk.logging.RingLog
import com.sarastarquant.kioskops.sdk.compliance.OverflowStrategy
import com.sarastarquant.kioskops.sdk.util.Idempotency
import com.sarastarquant.kioskops.sdk.util.Ids
import com.sarastarquant.kioskops.sdk.util.InstallSecret

@RestrictTo(RestrictTo.Scope.LIBRARY)
class QueueRepository(
  context: Context,
  private val logs: RingLog,
  private val crypto: CryptoProvider,
  openHelperFactory: SupportSQLiteOpenHelper.Factory? = null,
) {
  private val appContext = context.applicationContext
  private val db = Room.databaseBuilder(appContext, QueueDatabase::class.java, "kiosk_ops_queue.db")
    .addMigrations(QueueDatabase.MIGRATION_2_3, QueueDatabase.MIGRATION_3_4)
    // v1 was an internal pre-release snapshot; allow destructive migration from it.
    .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
    .apply { if (openHelperFactory != null) openHelperFactory(openHelperFactory) }
    .build()

  private val dao = db.queueDao()

  suspend fun enqueue(
    type: String,
    payloadJson: String,
    cfg: KioskOpsConfig,
    stableEventId: String? = null,
    idempotencyKeyOverride: String? = null,
    userId: String? = null,
    dataClassification: String? = null,
    anomalyScore: Float? = null,
  ): EnqueueResult {
    // Step 2 guardrails
    val bytes = payloadJson.toByteArray(Charsets.UTF_8)
    if (bytes.size > cfg.securityPolicy.maxEventPayloadBytes) {
      logs.w("Queue", "Rejected payload too large (${bytes.size} bytes) type=$type")
      return EnqueueResult.Rejected.PayloadTooLarge(bytes = bytes.size, max = cfg.securityPolicy.maxEventPayloadBytes)
    }

    // Step 7: enforce local storage quotas (count + bytes) before writing payloads.
    val limits = cfg.queueLimits
    val newBytes = bytes.size.toLong()
    val beforeCount = dao.countNotSent()
    val beforeBytes = dao.sumNotSentBytes()
    if (beforeCount >= limits.maxActiveEvents || (beforeBytes + newBytes) > limits.maxActiveBytes) {
      when (limits.overflowStrategy) {
        OverflowStrategy.DROP_NEWEST, OverflowStrategy.BLOCK -> {
          val reason = "Queue limits exceeded count=$beforeCount/${limits.maxActiveEvents} bytes=${beforeBytes}+${newBytes}/${limits.maxActiveBytes}"
          logs.w("Queue", "Rejected enqueue due to quota: $reason")
          return EnqueueResult.Rejected.QueueFull(reason)
        }
        OverflowStrategy.DROP_OLDEST -> {
          var dropped = 0
          var attempts = 0
          var count = beforeCount
          var bytesSum = beforeBytes
          // Delete only enough to make room for the new event.
          // Use limit=1 to avoid over-deleting when we just need space for one event.
          while ((count >= limits.maxActiveEvents || (bytesSum + newBytes) > limits.maxActiveBytes) && attempts < 100) {
            val deleted = dao.deleteOldestEligible(limit = 1)
            if (deleted <= 0) break
            dropped += deleted
            attempts++
            count = dao.countNotSent()
            bytesSum = dao.sumNotSentBytes()
          }
          if (count >= limits.maxActiveEvents || (bytesSum + newBytes) > limits.maxActiveBytes) {
            val reason = "Queue limits still exceeded after dropping oldest count=$count bytes=$bytesSum"
            logs.w("Queue", reason)
            return EnqueueResult.Rejected.QueueFull(reason)
          }

          // Record in logs; higher-level layers can convert this into audit/telemetry.
          if (dropped > 0) {
            logs.i("Queue", "Dropped oldest $dropped events due to queue pressure")
          }
          // Stash dropped count for the eventual Accepted() result.
          // We'll pass it via a local variable below.
          // (Kotlin requires a val; see acceptedDroppedOldest).
          val acceptedDroppedOldest = dropped
          // Proceed to insert; the Accepted result will carry droppedOldest.
          // Note: this is scoped; see below.
          return enqueueAfterPressureDrop(
            type = type,
            payloadJson = payloadJson,
            bytes = bytes,
            nowOverrideMs = null,
            cfg = cfg,
            stableEventId = stableEventId,
            idempotencyKeyOverride = idempotencyKeyOverride,
            droppedOldest = acceptedDroppedOldest,
            userId = userId,
            dataClassification = dataClassification,
            anomalyScore = anomalyScore,
          )
        }
      }
    }

    return enqueueAfterPressureDrop(
      type = type,
      payloadJson = payloadJson,
      bytes = bytes,
      nowOverrideMs = null,
      cfg = cfg,
      stableEventId = stableEventId,
      idempotencyKeyOverride = idempotencyKeyOverride,
      droppedOldest = 0,
      userId = userId,
      dataClassification = dataClassification,
      anomalyScore = anomalyScore,
    )
  }

  private suspend fun enqueueAfterPressureDrop(
    type: String,
    payloadJson: String,
    bytes: ByteArray,
    nowOverrideMs: Long?,
    cfg: KioskOpsConfig,
    stableEventId: String?,
    idempotencyKeyOverride: String?,
    droppedOldest: Int,
    userId: String? = null,
    dataClassification: String? = null,
    anomalyScore: Float? = null,
  ): EnqueueResult {
    val id = Ids.uuid()
    val now = nowOverrideMs ?: System.currentTimeMillis()
    val idempotencyKey = idempotencyKeyOverride ?: run {
      val icfg = cfg.idempotencyConfig
      if (icfg.deterministicEnabled && !stableEventId.isNullOrBlank()) {
        val secret = InstallSecret.getOrCreate(appContext)
        Idempotency.compute(secret = secret, type = type, stableEventId = stableEventId, nowMs = now, cfg = icfg)
      } else {
        Ids.uuid()
      }
    }
    val enc = PayloadCodec.encodeJson(payloadJson, cfg.securityPolicy.encryptQueuePayloads, crypto)
    return try {
      dao.insert(
        QueueEventEntity(
          id = id,
          idempotencyKey = idempotencyKey,
          type = type,
          payloadBlob = enc.blob,
          payloadEncoding = enc.encoding,
          payloadBytes = bytes.size,
          createdAtEpochMs = now,
          state = QueueStates.PENDING,
          attempts = 0,
          nextAttemptAtEpochMs = 0L,
          permanentFailure = 0,
          lastError = null,
          quarantineReason = null,
          updatedAtEpochMs = now,
          userId = userId,
          dataClassification = dataClassification,
          anomalyScore = anomalyScore,
        )
      )
      EnqueueResult.Accepted(id = id, idempotencyKey = idempotencyKey, droppedOldest = droppedOldest)
    } catch (t: android.database.sqlite.SQLiteConstraintException) {
      logs.w("Queue", "Insert failed: duplicate idempotency key", t)
      EnqueueResult.Rejected.DuplicateIdempotency("Duplicate idempotency key: ${t.message ?: ""}")
    } catch (t: android.database.sqlite.SQLiteFullException) {
      logs.e("Queue", "Insert failed: device storage full", t)
      EnqueueResult.Rejected.QueueFull("Device storage full: ${t.message ?: ""}")
    } catch (t: Throwable) {
      logs.e("Queue", "Insert failed: unexpected error", t)
      EnqueueResult.Rejected.Unknown("Insert failed: ${t.message ?: t::class.java.simpleName}")
    }
  }

  suspend fun nextBatch(nowMs: Long, limit: Int = 25): List<QueueEventEntity> = dao.loadNextBatch(nowMs, limit)

  fun decodePayloadJson(e: QueueEventEntity): String =
    PayloadCodec.decodeToJson(e.payloadBlob, e.payloadEncoding, crypto)

  suspend fun markSending(id: String) = dao.markSending(id, System.currentTimeMillis())

  suspend fun markFailed(id: String, err: String, nextAttemptAtMs: Long, permanentFailure: Int, quarantineReason: String? = null) =
    dao.markFailed(id, err, quarantineReason, nextAttemptAtMs, permanentFailure, System.currentTimeMillis())

  suspend fun markSent(id: String) = dao.markSent(id, System.currentTimeMillis())

  suspend fun delete(id: String) { dao.deleteById(id) }

  suspend fun countActive(): Long = dao.countNotSent()

  suspend fun quarantinedSummaries(limit: Int = 50): List<QuarantinedEventSummary> =
    dao.loadQuarantinedSummaries(limit).map { it.toSummary() }

  /** @since 0.5.0 */
  suspend fun deleteByUserId(userId: String): Int = dao.deleteByUserId(userId)

  /** @since 0.5.0 */
  suspend fun getByUserId(userId: String): List<QueueEventEntity> = dao.getByUserId(userId)

  /** @since 0.5.0 */
  suspend fun getAnomalous(threshold: Float): List<QueueEventEntity> = dao.getAnomalous(threshold)

  /**
   * Retention: delete SENT and FAILED events older than the configured window.
   * This supports minimization requirements and prevents unbounded local storage growth.
   */
  suspend fun applyRetention(cfg: KioskOpsConfig) {
    val now = System.currentTimeMillis()
    val sentCutoff = now - cfg.retentionPolicy.retainSentEventsDays.toLong() * 24 * 60 * 60 * 1000
    val failedCutoff = now - cfg.retentionPolicy.retainFailedEventsDays.toLong() * 24 * 60 * 60 * 1000

    val ds = dao.deleteSentOlderThan(sentCutoff)
    val df = dao.deleteFailedOlderThan(failedCutoff)
    val dq = dao.deleteQuarantinedOlderThan(failedCutoff)
    if (ds > 0 || df > 0) {
      logs.i("Queue", "Retention deleted sent=$ds failed=$df quarantined=$dq")
    }
  }
}
