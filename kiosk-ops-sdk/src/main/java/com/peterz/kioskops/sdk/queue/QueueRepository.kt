package com.peterz.kioskops.sdk.queue

import android.content.Context
import androidx.room.Room
import com.peterz.kioskops.sdk.KioskOpsConfig
import com.peterz.kioskops.sdk.crypto.CryptoProvider
import com.peterz.kioskops.sdk.logging.RingLog
import com.peterz.kioskops.sdk.util.Ids

class QueueRepository(
  context: Context,
  private val logs: RingLog,
  private val crypto: CryptoProvider,
) {
  private val db = Room.databaseBuilder(context, QueueDatabase::class.java, "kiosk_ops_queue.db")
    .fallbackToDestructiveMigration() // MVP: replace with migration once schema is stable
    .build()

  private val dao = db.queueDao()

  suspend fun enqueue(type: String, payloadJson: String, cfg: KioskOpsConfig): Boolean {
    // Step 2 guardrails
    val bytes = payloadJson.toByteArray(Charsets.UTF_8)
    if (bytes.size > cfg.securityPolicy.maxEventPayloadBytes) {
      logs.w("Queue", "Rejected payload too large (${bytes.size} bytes) type=$type")
      return false
    }
    if (!cfg.securityPolicy.allowRawPayloadStorage) {
      val lower = payloadJson.lowercase()
      for (k in cfg.securityPolicy.denylistJsonKeys) {
        if (lower.contains("\"$k\"")) {
          logs.w("Queue", "Rejected payload due to denylisted key=$k type=$type")
          return false
        }
      }
    }

    val id = Ids.uuid()
    val idempotencyKey = Ids.uuid()
    val enc = PayloadCodec.encodeJson(payloadJson, cfg.securityPolicy.encryptQueuePayloads, crypto)

    val now = System.currentTimeMillis()
    return try {
      dao.insert(
        QueueEventEntity(
          id = id,
          idempotencyKey = idempotencyKey,
          type = type,
          payloadBlob = enc.blob,
          payloadEncoding = enc.encoding,
          createdAtEpochMs = now,
          state = QueueStates.PENDING,
          attempts = 0,
          nextAttemptAtEpochMs = 0L,
          permanentFailure = 0,
          lastError = null,
          updatedAtEpochMs = now
        )
      )
      true
    } catch (t: Throwable) {
      logs.w("Queue", "Insert failed (possible duplicate idempotency?)", t)
      false
    }
  }

  suspend fun nextBatch(nowMs: Long, limit: Int = 25): List<QueueEventEntity> = dao.loadNextBatch(nowMs, limit)

  fun decodePayloadJson(e: QueueEventEntity): String =
    PayloadCodec.decodeToJson(e.payloadBlob, e.payloadEncoding, crypto)

  suspend fun markSending(id: String) = dao.markSending(id, System.currentTimeMillis())

  suspend fun markFailed(id: String, err: String, nextAttemptAtMs: Long, permanentFailure: Int) =
    dao.markFailed(id, err, nextAttemptAtMs, permanentFailure, System.currentTimeMillis())

  suspend fun markSent(id: String) = dao.markSent(id, System.currentTimeMillis())

  suspend fun delete(id: String) { dao.deleteById(id) }

  suspend fun countActive(): Long = dao.countNotSent()

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
    if (ds > 0 || df > 0) {
      logs.i("Queue", "Retention deleted sent=$ds failed=$df")
    }
  }
}
