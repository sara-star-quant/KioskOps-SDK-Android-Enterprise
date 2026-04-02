/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.debug

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertThrows

class EventInspectorTest {

  private lateinit var inspector: EventInspector

  private val testEvents = listOf(
    EventSummary("e1", "SCAN", 1_700_000_000_000L, 0, EventState.PENDING, 100, null, null, "corr-1"),
    EventSummary("e2", "SCAN", 1_700_000_001_000L, 3, EventState.QUARANTINED, 200, "timeout", null, "corr-1"),
    EventSummary("e3", "HEARTBEAT", 1_700_000_002_000L, 1, EventState.PROCESSING, 50, null, 1_700_000_010_000L, "corr-2"),
    EventSummary("e4", "SCAN", 1_700_000_003_000L, 0, EventState.COMPLETED, 150, null, null, null),
    EventSummary("e5", "DIAGNOSTICS", 1_700_000_004_000L, 5, EventState.QUARANTINED, 300, "bad_schema", null, "corr-3"),
  )

  private val fakeAccessor = object : QueueAccessor {
    override suspend fun getAllEvents() = testEvents
    override suspend fun getEventById(id: String) = testEvents.find { it.id == id }
    override suspend fun decryptPayload(eventId: String) = """{"decrypted":"payload_$eventId"}"""
    override suspend fun retryEvent(eventId: String) = testEvents.any { it.id == eventId && it.state == EventState.QUARANTINED }
    override suspend fun clearQuarantined() = testEvents.count { it.state == EventState.QUARANTINED }
  }

  @Before
  fun setUp() {
    DebugUtils.isDebugBuild = true
    inspector = EventInspector(fakeAccessor)
  }

  @After
  fun tearDown() {
    DebugUtils.isDebugBuild = false
  }

  @Test
  fun `getQueuedEvents returns all events when no filter`() = runTest {
    val result = inspector.getQueuedEvents()
    assertThat(result.totalItems).isEqualTo(5)
    assertThat(result.items).hasSize(5)
    assertThat(result.page).isEqualTo(0)
  }

  @Test
  fun `getQueuedEvents paginates correctly`() = runTest {
    val page0 = inspector.getQueuedEvents(page = 0, pageSize = 2)
    assertThat(page0.items).hasSize(2)
    assertThat(page0.totalPages).isEqualTo(3)
    assertThat(page0.hasNext).isTrue()
    assertThat(page0.hasPrevious).isFalse()

    val page1 = inspector.getQueuedEvents(page = 1, pageSize = 2)
    assertThat(page1.items).hasSize(2)
    assertThat(page1.hasPrevious).isTrue()

    val page2 = inspector.getQueuedEvents(page = 2, pageSize = 2)
    assertThat(page2.items).hasSize(1)
    assertThat(page2.hasNext).isFalse()
  }

  @Test
  fun `getQueuedEvents filters by eventType`() = runTest {
    val result = inspector.getQueuedEvents(filter = EventFilter(eventType = "SCAN"))
    assertThat(result.totalItems).isEqualTo(3)
    assertThat(result.items.all { it.type == "SCAN" }).isTrue()
  }

  @Test
  fun `getQueuedEvents filters by state`() = runTest {
    val result = inspector.getQueuedEvents(filter = EventFilter(state = EventState.QUARANTINED))
    assertThat(result.totalItems).isEqualTo(2)
  }

  @Test
  fun `getQueuedEvents filters by minAttempts`() = runTest {
    val result = inspector.getQueuedEvents(filter = EventFilter(minAttempts = 3))
    assertThat(result.totalItems).isEqualTo(2)
    assertThat(result.items.all { it.attempts >= 3 }).isTrue()
  }

  @Test(expected = IllegalArgumentException::class)
  fun `getQueuedEvents rejects negative page`() = runTest {
    inspector.getQueuedEvents(page = -1)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `getQueuedEvents rejects pageSize over 100`() = runTest {
    inspector.getQueuedEvents(pageSize = 101)
  }

  @Test
  fun `getEventDetails returns details with decrypted payload`() = runTest {
    val details = inspector.getEventDetails("e1")
    assertThat(details).isNotNull()
    assertThat(details!!.id).isEqualTo("e1")
    assertThat(details.decryptedPayload).contains("payload_e1")
  }

  @Test
  fun `getEventDetails returns null for unknown ID`() = runTest {
    assertThat(inspector.getEventDetails("nonexistent")).isNull()
  }

  @Test
  fun `getQueueStatistics calculates correct breakdowns`() = runTest {
    val stats = inspector.getQueueStatistics()
    assertThat(stats.activeCount).isEqualTo(1) // PENDING
    assertThat(stats.quarantinedCount).isEqualTo(2)
    assertThat(stats.processingCount).isEqualTo(1)
    assertThat(stats.totalBytes).isEqualTo(800) // 100+200+50+150+300
    assertThat(stats.eventTypeBreakdown["SCAN"]).isEqualTo(3)
    assertThat(stats.eventTypeBreakdown["HEARTBEAT"]).isEqualTo(1)
    assertThat(stats.averageAttempts).isWithin(0.1).of(1.8) // (0+3+1+0+5)/5
  }

  @Test
  fun `retryQuarantinedEvent returns true for quarantined event`() = runTest {
    assertThat(inspector.retryQuarantinedEvent("e2")).isTrue()
  }

  @Test
  fun `retryQuarantinedEvent returns false for non-quarantined event`() = runTest {
    assertThat(inspector.retryQuarantinedEvent("e1")).isFalse()
  }

  @Test
  fun `clearQuarantinedEvents returns count`() = runTest {
    assertThat(inspector.clearQuarantinedEvents()).isEqualTo(2)
  }

  @Test
  fun `getEventsByCorrelationId filters correctly`() = runTest {
    val events = inspector.getEventsByCorrelationId("corr-1")
    assertThat(events).hasSize(2)
    assertThat(events.map { it.id }).containsExactly("e1", "e2")
  }

  @Test
  fun `getEventsByCorrelationId returns empty for unknown ID`() = runTest {
    assertThat(inspector.getEventsByCorrelationId("nonexistent")).isEmpty()
  }
}
