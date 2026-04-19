package com.sarastarquant.kioskops.sdk.queue

import androidx.annotation.RestrictTo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@RestrictTo(RestrictTo.Scope.LIBRARY)
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

  /**
   * Rescue SENDING rows stranded across a process restart. An event is marked SENDING just
   * before transport; if the process dies mid-flight, the row stays SENDING forever and
   * loadNextBatch never picks it up (it only selects PENDING/FAILED), so the event silently
   * rots until retention deletes it.
   *
   * [staleBeforeEpochMs] is an age gate so a concurrently-running process's genuinely in-flight
   * rows aren't reset mid-send. Callers pass e.g. now - 2 x callTimeout.
   *
   * Returns the number of rows reset.
   * @since 1.2.0
   */
  @Query(
    "UPDATE queue_events SET state = 'PENDING', updatedAtEpochMs = :nowMs " +
      "WHERE state = 'SENDING' AND updatedAtEpochMs < :staleBeforeEpochMs"
  )
  suspend fun reconcileStaleSending(staleBeforeEpochMs: Long, nowMs: Long): Int

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

  /**
   * Record a batch-level failure without bumping per-event attempts. Used when the whole
   * batch fails for reasons outside the individual event (network transient, auth error,
   * schema-level permanent). Without this, a single 30-minute outage processed as 50-event
   * batches would march every event to `maxAttemptsPerEvent` and quarantine the fleet's
   * backlog despite nothing being wrong with the events.
   *
   * Still sets state=FAILED and records the error + next-attempt gate so the event is
   * eligible again after backoff.
   * @since 1.2.0
   */
  @Query(
    "UPDATE queue_events SET " +
      "state = 'FAILED', " +
      "lastError = :error, " +
      "nextAttemptAtEpochMs = :nextAttemptAtMs, " +
      "updatedAtEpochMs = :nowMs " +
      "WHERE id = :id"
  )
  suspend fun markBatchFailureNoAttemptBump(
    id: String,
    error: String,
    nextAttemptAtMs: Long,
    nowMs: Long,
  )

  @Query("DELETE FROM queue_events WHERE id = :id")
  suspend fun deleteById(id: String): Int

  @Query("SELECT COUNT(*) FROM queue_events WHERE state != 'SENT'")
  suspend fun countNotSent(): Long

  /**
   * Room-reactive variant of [countNotSent]. Emits a new value whenever rows matching the
   * `state != 'SENT'` predicate are inserted, updated, or deleted. Cheaper and more
   * responsive than polling on a timer.
   * @since 1.2.0
   */
  @Query("SELECT COUNT(*) FROM queue_events WHERE state != 'SENT'")
  fun countNotSentFlow(): kotlinx.coroutines.flow.Flow<Long>

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

  /** @since 0.5.0 */
  @Query("DELETE FROM queue_events WHERE userId = :userId")
  suspend fun deleteByUserId(userId: String): Int

  /** @since 0.5.0 */
  @Query("SELECT * FROM queue_events WHERE userId = :userId ORDER BY createdAtEpochMs ASC")
  suspend fun getByUserId(userId: String): List<QueueEventEntity>

  /** @since 0.5.0 */
  @Query("SELECT * FROM queue_events WHERE anomalyScore IS NOT NULL AND anomalyScore >= :threshold ORDER BY anomalyScore DESC")
  suspend fun getAnomalous(threshold: Float): List<QueueEventEntity>
}

/** Lightweight projection to avoid pulling encrypted payload blobs. */
@RestrictTo(RestrictTo.Scope.LIBRARY)
data class QuarantinedEventRow(
  val id: String,
  val type: String,
  val createdAtEpochMs: Long,
  val attempts: Int,
  val lastError: String?,
  val quarantineReason: String?,
  val updatedAtEpochMs: Long,
)
