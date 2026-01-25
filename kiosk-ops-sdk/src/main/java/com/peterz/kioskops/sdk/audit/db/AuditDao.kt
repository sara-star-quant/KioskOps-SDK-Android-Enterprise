/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.audit.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/**
 * Data Access Object for audit trail operations.
 *
 * Provides CRUD operations for audit events and chain state management.
 */
@Dao
interface AuditDao {

  /**
   * Insert a new audit event.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertEvent(event: AuditEventEntity)

  /**
   * Get an event by ID.
   */
  @Query("SELECT * FROM audit_events WHERE id = :id")
  suspend fun getEventById(id: String): AuditEventEntity?

  /**
   * Get events in a time range, ordered by timestamp.
   */
  @Query("SELECT * FROM audit_events WHERE ts >= :fromTs AND ts <= :toTs ORDER BY ts ASC")
  suspend fun getEventsInRange(fromTs: Long, toTs: Long): List<AuditEventEntity>

  /**
   * Get all events ordered by timestamp.
   */
  @Query("SELECT * FROM audit_events ORDER BY ts ASC")
  suspend fun getAllEvents(): List<AuditEventEntity>

  /**
   * Get events by name.
   */
  @Query("SELECT * FROM audit_events WHERE name = :name ORDER BY ts ASC")
  suspend fun getEventsByName(name: String): List<AuditEventEntity>

  /**
   * Count total events.
   */
  @Query("SELECT COUNT(*) FROM audit_events")
  suspend fun countEvents(): Long

  /**
   * Count events in a time range.
   */
  @Query("SELECT COUNT(*) FROM audit_events WHERE ts >= :fromTs AND ts <= :toTs")
  suspend fun countEventsInRange(fromTs: Long, toTs: Long): Long

  /**
   * Get the most recent event.
   */
  @Query("SELECT * FROM audit_events ORDER BY ts DESC LIMIT 1")
  suspend fun getLatestEvent(): AuditEventEntity?

  /**
   * Get the oldest event.
   */
  @Query("SELECT * FROM audit_events ORDER BY ts ASC LIMIT 1")
  suspend fun getOldestEvent(): AuditEventEntity?

  /**
   * Delete events older than a timestamp.
   */
  @Query("DELETE FROM audit_events WHERE ts < :beforeTs")
  suspend fun deleteEventsBefore(beforeTs: Long): Int

  /**
   * Delete all events.
   */
  @Query("DELETE FROM audit_events")
  suspend fun deleteAllEvents()

  /**
   * Get distinct event names with counts.
   */
  @Query("SELECT name, COUNT(*) as count FROM audit_events GROUP BY name ORDER BY count DESC")
  suspend fun getEventNameCounts(): List<EventNameCount>

  /**
   * Get chain state.
   */
  @Query("SELECT * FROM audit_chain_state WHERE id = 1")
  suspend fun getChainState(): AuditChainState?

  /**
   * Insert or update chain state.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertChainState(state: AuditChainState)

  /**
   * Atomic insert of event and chain state update.
   */
  @Transaction
  suspend fun insertEventWithChainUpdate(event: AuditEventEntity, state: AuditChainState) {
    insertEvent(event)
    upsertChainState(state)
  }

  /**
   * Get events for chain verification in order.
   */
  @Query("SELECT * FROM audit_events WHERE ts >= :fromTs AND ts <= :toTs ORDER BY ts ASC, id ASC")
  suspend fun getEventsForVerification(fromTs: Long, toTs: Long): List<AuditEventEntity>
}

/**
 * Result of event name count query.
 */
data class EventNameCount(
  val name: String,
  val count: Int,
)
