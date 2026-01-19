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
    "UPDATE queue_events SET state = 'FAILED', attempts = attempts + 1, " +
      "lastError = :error, nextAttemptAtEpochMs = :nextAttemptAtMs, " +
      "permanentFailure = :permanentFailure, updatedAtEpochMs = :nowMs " +
      "WHERE id = :id"
  )
  suspend fun markFailed(
    id: String,
    error: String,
    nextAttemptAtMs: Long,
    permanentFailure: Int,
    nowMs: Long
  )

  @Query("DELETE FROM queue_events WHERE id = :id")
  suspend fun deleteById(id: String): Int

  @Query("SELECT COUNT(*) FROM queue_events WHERE state != 'SENT'")
  suspend fun countNotSent(): Long

  @Query("DELETE FROM queue_events WHERE state = 'SENT' AND createdAtEpochMs < :cutoffMs")
  suspend fun deleteSentOlderThan(cutoffMs: Long): Int

  @Query("DELETE FROM queue_events WHERE state = 'FAILED' AND updatedAtEpochMs < :cutoffMs")
  suspend fun deleteFailedOlderThan(cutoffMs: Long): Int
}
