/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.debug

import java.time.Duration
import java.time.Instant

/**
 * State of an event in the queue.
 *
 * @since 0.4.0
 */
enum class EventState {
  /** Event is waiting to be sent. */
  PENDING,
  /** Event is currently being processed. */
  PROCESSING,
  /** Event has been quarantined due to repeated failures. */
  QUARANTINED,
  /** Event was successfully sent. */
  COMPLETED,
}

/**
 * Debug-only event queue inspector.
 *
 * Allows viewing queued events, statistics, and managing quarantined
 * events during development and debugging.
 *
 * Security (ISO 27001 A.14.2): Only available in debug builds.
 * Privacy: Events are displayed as metadata summaries without decrypted payloads.
 *
 * @since 0.4.0
 */
@RequiresDebugBuild
class EventInspector(
  private val queueAccessor: QueueAccessor,
) {

  init {
    DebugUtils.requireDebugBuild()
  }

  /**
   * Get paginated list of queued events (metadata only).
   *
   * @param page Page number (0-indexed)
   * @param pageSize Events per page
   * @param filter Optional filter criteria
   * @return Paginated event summaries
   */
  suspend fun getQueuedEvents(
    page: Int = 0,
    pageSize: Int = 50,
    filter: EventFilter? = null,
  ): PagedResult<EventSummary> {
    require(page >= 0) { "Page must be non-negative" }
    require(pageSize in 1..100) { "Page size must be 1-100" }

    val events = queueAccessor.getAllEvents()
    val filtered = filter?.let { f ->
      events.filter { event ->
        (f.eventType == null || event.type == f.eventType) &&
        (f.state == null || event.state == f.state) &&
        (f.minAttempts == null || event.attempts >= f.minAttempts)
      }
    } ?: events

    val total = filtered.size
    val start = page * pageSize
    val paged = filtered.drop(start).take(pageSize)

    return PagedResult(
      items = paged,
      page = page,
      pageSize = pageSize,
      totalItems = total,
      totalPages = (total + pageSize - 1) / pageSize,
    )
  }

  /**
   * Get event details by ID.
   *
   * In debug builds, payload is decrypted for inspection.
   *
   * @param eventId Event ID
   * @return Event details or null if not found
   */
  suspend fun getEventDetails(eventId: String): EventDetails? {
    return queueAccessor.getEventById(eventId)?.let { event ->
      EventDetails(
        id = event.id,
        type = event.type,
        enqueuedAt = event.enqueuedAt,
        attempts = event.attempts,
        state = event.state,
        payloadSizeBytes = event.payloadSize,
        lastError = event.lastError,
        nextRetryAt = event.nextRetryAt,
        correlationId = event.correlationId,
        decryptedPayload = queueAccessor.decryptPayload(eventId),
      )
    }
  }

  /**
   * Get queue statistics.
   *
   * @return Current queue statistics
   */
  suspend fun getQueueStatistics(): QueueStatistics {
    val events = queueAccessor.getAllEvents()
    val now = Instant.now()

    val stateGroups = events.groupBy { it.state }
    val typeGroups = events.groupBy { it.type }

    val oldest = events.minByOrNull { it.enqueuedAt }
    val oldestAge = oldest?.let {
      Duration.between(Instant.ofEpochMilli(it.enqueuedAt), now)
    }

    return QueueStatistics(
      activeCount = stateGroups[EventState.PENDING]?.size?.toLong() ?: 0L,
      quarantinedCount = stateGroups[EventState.QUARANTINED]?.size?.toLong() ?: 0L,
      processingCount = stateGroups[EventState.PROCESSING]?.size?.toLong() ?: 0L,
      totalBytes = events.sumOf { it.payloadSize.toLong() },
      oldestEventAge = oldestAge,
      eventTypeBreakdown = typeGroups.mapValues { it.value.size },
      stateBreakdown = stateGroups.mapValues { it.value.size },
      averageAttempts = if (events.isNotEmpty()) {
        events.sumOf { it.attempts } / events.size.toDouble()
      } else 0.0,
    )
  }

  /**
   * Force retry a quarantined event.
   *
   * Moves the event back to PENDING state with reset retry count.
   *
   * @param eventId Event ID to retry
   * @return True if event was retried, false if not found or not quarantined
   */
  suspend fun retryQuarantinedEvent(eventId: String): Boolean {
    return queueAccessor.retryEvent(eventId)
  }

  /**
   * Clear all quarantined events.
   *
   * @return Number of events removed
   */
  suspend fun clearQuarantinedEvents(): Int {
    return queueAccessor.clearQuarantined()
  }

  /**
   * Get events by correlation ID.
   *
   * Useful for tracing related events.
   *
   * @param correlationId Correlation ID to search for
   * @return List of matching event summaries
   */
  suspend fun getEventsByCorrelationId(correlationId: String): List<EventSummary> {
    return queueAccessor.getAllEvents().filter { it.correlationId == correlationId }
  }
}

/**
 * Summary of a queued event (metadata only).
 *
 * @since 0.4.0
 */
data class EventSummary(
  val id: String,
  val type: String,
  val enqueuedAt: Long,
  val attempts: Int,
  val state: EventState,
  val payloadSize: Int,
  val lastError: String?,
  val nextRetryAt: Long?,
  val correlationId: String?,
)

/**
 * Detailed event information including decrypted payload.
 *
 * @since 0.4.0
 */
data class EventDetails(
  val id: String,
  val type: String,
  val enqueuedAt: Long,
  val attempts: Int,
  val state: EventState,
  val payloadSizeBytes: Int,
  val lastError: String?,
  val nextRetryAt: Long?,
  val correlationId: String?,
  val decryptedPayload: String?,
)

/**
 * Filter criteria for event queries.
 *
 * @since 0.4.0
 */
data class EventFilter(
  val eventType: String? = null,
  val state: EventState? = null,
  val minAttempts: Int? = null,
)

/**
 * Paginated result container.
 *
 * @since 0.4.0
 */
data class PagedResult<T>(
  val items: List<T>,
  val page: Int,
  val pageSize: Int,
  val totalItems: Int,
  val totalPages: Int,
) {
  val hasNext: Boolean get() = page < totalPages - 1
  val hasPrevious: Boolean get() = page > 0
}

/**
 * Queue statistics for debugging.
 *
 * @since 0.4.0
 */
data class QueueStatistics(
  val activeCount: Long,
  val quarantinedCount: Long,
  val processingCount: Long,
  val totalBytes: Long,
  val oldestEventAge: Duration?,
  val eventTypeBreakdown: Map<String, Int>,
  val stateBreakdown: Map<EventState, Int>,
  val averageAttempts: Double,
)

/**
 * Interface for queue access (provided by SDK internals).
 *
 * @since 0.4.0
 */
interface QueueAccessor {
  suspend fun getAllEvents(): List<EventSummary>
  suspend fun getEventById(id: String): EventSummary?
  suspend fun decryptPayload(eventId: String): String?
  suspend fun retryEvent(eventId: String): Boolean
  suspend fun clearQuarantined(): Int
}
