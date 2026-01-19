package com.peterz.kioskops.sdk.queue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface QueueDao {
  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(e: QueueEventEntity)

  /**
   * Returns oldest eligible PENDING and FAILED events for processing.
   * Eligibility = not permanent + backoff gate passed.
   */
  @Query(
    "SELECT * FROM queue_events " +
      "WHERE state IN ('PENDING','FAILED') " +
      "AND permanentFailure = 0 " +
      "AND nextAttemptAtEpochMs <= :nowMs " +
      "ORDER BY createdAtEpochMs ASC " +
      "LIMIT :limit"
  )
  suspend fun loadNextBatch(nowMs: Long, limit: Int): List<QueueEventEntity>

  @Query("UPDATE queue_events SET state = 'SENDING', lastError = NULL, updatedAtEpochMs = :nowMs WHERE id = :id")
  suspend fun markSending(id: String, nowMs: Long)

  @Query("UPDATE queue_events SET state = 'SENT', lastError = NULL, updatedAtEpochMs = :nowMs WHERE id = :id")
  suspend fun markSent(id: String, nowMs: Long)

  @Query(
    "UPDATE queue_events SET " +
      "state = CASE WHEN :permanentFailure = 1 THEN 'QUARANTINED' ELSE 'FAILED' END, " +
      "attempts = attempts + 1, " +
      "lastError = :error, " +
      "quarantineReason = CASE WHEN :permanentFailure = 1 THEN :quarantineReason ELSE NULL END, " +
      "nextAttemptAtEpochMs = :nextAttemptAtMs, " +
      "permanentFailure = :permanentFailure, " +
      "updatedAtEpochMs = :nowMs " +
      "WHERE id = :id"
  )
  suspend fun markFailed(
    id: String,
    error: String,
    quarantineReason: String?,
    nextAttemptAtMs: Long,
    permanentFailure: Int,
    nowMs: Long
  )

  @Query("DELETE FROM queue_events WHERE id = :id")
  suspend fun deleteById(id: String): Int

  @Query("SELECT COUNT(*) FROM queue_events WHERE state != 'SENT'")
  suspend fun countNotSent(): Long

  @Query("SELECT IFNULL(SUM(payloadBytes), 0) FROM queue_events WHERE state != 'SENT'")
  suspend fun sumNotSentBytes(): Long

  /** Deletes oldest non-sent, non-quarantined events. Used for backpressure. */
  @Query(
    "DELETE FROM queue_events WHERE id IN (" +
      "SELECT id FROM queue_events " +
      "WHERE state IN ('PENDING','FAILED') AND permanentFailure = 0 " +
      "ORDER BY createdAtEpochMs ASC " +
      "LIMIT :limit" +
    ")"
  )
  suspend fun deleteOldestEligible(limit: Int): Int

  @Query(
    "SELECT id, type, createdAtEpochMs, attempts, lastError, quarantineReason, updatedAtEpochMs " +
      "FROM queue_events " +
      "WHERE state = 'QUARANTINED' " +
      "ORDER BY createdAtEpochMs ASC " +
      "LIMIT :limit"
  )
  suspend fun loadQuarantinedSummaries(limit: Int): List<QuarantinedEventRow>

  @Query("DELETE FROM queue_events WHERE state = 'SENT' AND createdAtEpochMs < :cutoffMs")
  suspend fun deleteSentOlderThan(cutoffMs: Long): Int

  @Query("DELETE FROM queue_events WHERE state = 'FAILED' AND updatedAtEpochMs < :cutoffMs")
  suspend fun deleteFailedOlderThan(cutoffMs: Long): Int

  @Query("DELETE FROM queue_events WHERE state = 'QUARANTINED' AND updatedAtEpochMs < :cutoffMs")
  suspend fun deleteQuarantinedOlderThan(cutoffMs: Long): Int
}

/** Lightweight projection to avoid pulling encrypted payload blobs. */
data class QuarantinedEventRow(
  val id: String,
  val type: String,
  val createdAtEpochMs: Long,
  val attempts: Int,
  val lastError: String?,
  val quarantineReason: String?,
  val updatedAtEpochMs: Long,
)
