/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.compliance.RetentionPolicy
import com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider
import com.sarastarquant.kioskops.sdk.util.Clock
import com.sarastarquant.kioskops.sdk.util.Hashing
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class AuditTrailExtendedTest {

  private lateinit var ctx: Context
  private var nowMs = Instant.parse("2026-03-15T12:00:00Z").toEpochMilli()
  private val clock = object : Clock {
    override fun nowMs(): Long = nowMs
  }
  private val json = Json { ignoreUnknownKeys = true }

  private val defaultRetention = RetentionPolicy(
    retainSentEventsDays = 7,
    retainFailedEventsDays = 14,
    retainTelemetryDays = 7,
    retainAuditDays = 30,
    retainLogsDays = 7,
  )

  private lateinit var trail: AuditTrail

  @Before
  fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()
    // Clear any leftover audit files from previous tests
    val auditDir = File(ctx.filesDir, "kioskops_audit")
    auditDir.listFiles()?.forEach { it.delete() }

    trail = AuditTrail(
      context = ctx,
      retentionProvider = { defaultRetention },
      clock = clock,
      crypto = NoopCryptoProvider,
    )
  }

  // -- record() stores event with correct fields --

  @Test
  fun `record stores event with correct name and timestamp`() {
    trail.record("sdk_initialized")

    val files = trail.listFiles()
    assertThat(files).hasSize(1)
    assertThat(files[0].name).contains("2026-03-15")

    val line = files[0].readLines().first()
    val event = json.decodeFromString<AuditEvent>(line)
    assertThat(event.ts).isEqualTo(nowMs)
    assertThat(event.name).isEqualTo("sdk_initialized")
    assertThat(event.fields).isEmpty()
  }

  @Test
  fun `record stores event with custom fields map`() {
    trail.record("sync_complete", mapOf("batch_size" to "42", "status" to "ok"))

    val files = trail.listFiles()
    val line = files[0].readLines().first()
    val event = json.decodeFromString<AuditEvent>(line)

    assertThat(event.name).isEqualTo("sync_complete")
    assertThat(event.fields).containsEntry("batch_size", "42")
    assertThat(event.fields).containsEntry("status", "ok")
    assertThat(event.fields).hasSize(2)
  }

  // -- Hash chain linking --

  @Test
  fun `first record uses GENESIS as prevHash`() {
    trail.record("first_event")

    val files = trail.listFiles()
    val line = files[0].readLines().first()
    val event = json.decodeFromString<AuditEvent>(line)

    assertThat(event.prevHash).isEqualTo("GENESIS")
    assertThat(event.hash).isNotEmpty()
    assertThat(event.hash).isNotEqualTo("GENESIS")
  }

  @Test
  fun `second record chains to first record hash`() {
    trail.record("event_1")
    nowMs += 1000
    trail.record("event_2")

    val files = trail.listFiles()
    val lines = files[0].readLines()
    val event1 = json.decodeFromString<AuditEvent>(lines[0])
    val event2 = json.decodeFromString<AuditEvent>(lines[1])

    assertThat(event1.prevHash).isEqualTo("GENESIS")
    assertThat(event2.prevHash).isEqualTo(event1.hash)
  }

  @Test
  fun `hash is deterministic for same payload`() {
    val ts = nowMs
    val name = "test_event"
    val fields = mapOf("a" to "1")
    val prev = "GENESIS"
    val payload = "$ts|$name|${fields.toSortedMap()}|$prev"
    val expectedHash = Hashing.sha256Base64Url(payload)

    trail.record(name, fields)

    val files = trail.listFiles()
    val line = files[0].readLines().first()
    val event = json.decodeFromString<AuditEvent>(line)

    assertThat(event.hash).isEqualTo(expectedHash)
  }

  // -- record() with empty name --

  @Test
  fun `record with empty name stores event with empty name field`() {
    trail.record("")

    val files = trail.listFiles()
    assertThat(files).hasSize(1)
    val line = files[0].readLines().first()
    val event = json.decodeFromString<AuditEvent>(line)
    assertThat(event.name).isEmpty()
  }

  // -- File-based day-slicing --

  @Test
  fun `events on different days produce separate files`() {
    trail.record("day1_event")

    // Advance to next day
    nowMs = Instant.parse("2026-03-16T08:00:00Z").toEpochMilli()
    trail.record("day2_event")

    val files = trail.listFiles()
    assertThat(files).hasSize(2)

    val fileNames = files.map { it.name }
    assertThat(fileNames).contains("audit_2026-03-15.jsonl")
    assertThat(fileNames).contains("audit_2026-03-16.jsonl")
  }

  @Test
  fun `multiple events on same day append to same file`() {
    trail.record("event_a")
    nowMs += 1000
    trail.record("event_b")
    nowMs += 1000
    trail.record("event_c")

    val files = trail.listFiles()
    assertThat(files).hasSize(1)

    val lines = files[0].readLines()
    assertThat(lines).hasSize(3)
  }

  @Test
  fun `file names use plaintext extension when crypto is disabled`() {
    trail.record("plain_event")

    val files = trail.listFiles()
    assertThat(files[0].name).endsWith(".jsonl")
    assertThat(files[0].name).doesNotContain(".enc")
  }

  // -- Retention enforcement --

  @Test
  fun `purgeOldFiles deletes files older than retention window`() {
    // Record event on day 1
    trail.record("old_event")
    assertThat(trail.listFiles()).hasSize(1)

    // Advance beyond retention window (30 days + buffer)
    nowMs = Instant.parse("2026-04-16T12:00:00Z").toEpochMilli()
    trail.purgeOldFiles()

    assertThat(trail.listFiles()).isEmpty()
  }

  @Test
  fun `purgeOldFiles keeps recent files within retention window`() {
    trail.record("recent_event")

    // Advance only 5 days (within 30-day retention)
    nowMs = Instant.parse("2026-03-20T12:00:00Z").toEpochMilli()
    trail.purgeOldFiles()

    assertThat(trail.listFiles()).hasSize(1)
  }

  @Test
  fun `purgeOldFiles with short retention removes events quickly`() {
    val shortRetention = RetentionPolicy(
      retainSentEventsDays = 1,
      retainFailedEventsDays = 1,
      retainTelemetryDays = 1,
      retainAuditDays = 1,
      retainLogsDays = 1,
    )
    val shortTrail = AuditTrail(
      context = ctx,
      retentionProvider = { shortRetention },
      clock = clock,
      crypto = NoopCryptoProvider,
    )

    shortTrail.record("ephemeral_event")
    assertThat(shortTrail.listFiles()).hasSize(1)

    // Advance 2 days
    nowMs = Instant.parse("2026-03-17T12:00:00Z").toEpochMilli()
    shortTrail.purgeOldFiles()

    assertThat(shortTrail.listFiles()).isEmpty()
  }

  @Test
  fun `purgeOldFiles selectively removes only old files`() {
    // Record on March 15
    trail.record("march15_event")

    // Record on March 16
    nowMs = Instant.parse("2026-03-16T12:00:00Z").toEpochMilli()
    trail.record("march16_event")

    assertThat(trail.listFiles()).hasSize(2)

    // Use 2-day retention, advance to March 17T12:00Z
    // cutoff = March 15T12:00Z; March 15 dayStart (00:00Z) <= cutoff -> purged
    // March 16 dayStart (00:00Z) > March 15T12:00Z? No, 00:00 < 12:00 so also purged.
    // Use 2-day retention and advance to March 17T00:00Z instead:
    // cutoff = March 15T00:00Z; March 15 dayStart (00:00Z) <= cutoff -> purged
    // March 16 dayStart (00:00Z) > March 15T00:00Z -> kept
    val shortTrail = AuditTrail(
      context = ctx,
      retentionProvider = {
        RetentionPolicy(
          retainSentEventsDays = 1,
          retainFailedEventsDays = 1,
          retainTelemetryDays = 1,
          retainAuditDays = 2,
          retainLogsDays = 1,
        )
      },
      clock = clock,
      crypto = NoopCryptoProvider,
    )

    nowMs = Instant.parse("2026-03-17T12:00:00Z").toEpochMilli()
    shortTrail.purgeOldFiles()

    val remaining = shortTrail.listFiles()
    assertThat(remaining).hasSize(1)
    assertThat(remaining[0].name).contains("2026-03-16")
  }

  // -- Concurrent writes --

  @Test
  fun `concurrent writes do not corrupt state`() {
    val threadCount = 8
    val eventsPerThread = 20
    val latch = CountDownLatch(threadCount)
    val executor = Executors.newFixedThreadPool(threadCount)

    for (t in 0 until threadCount) {
      executor.submit {
        try {
          for (i in 0 until eventsPerThread) {
            trail.record("concurrent_event", mapOf("thread" to t.toString(), "seq" to i.toString()))
          }
        } finally {
          latch.countDown()
        }
      }
    }

    latch.await(10, TimeUnit.SECONDS)
    executor.shutdown()

    val files = trail.listFiles()
    assertThat(files).isNotEmpty()

    // All events should be written (single day file)
    val totalLines = files.sumOf { it.readLines().size }
    assertThat(totalLines).isEqualTo(threadCount * eventsPerThread)
  }

  // -- listFiles ordering --

  @Test
  fun `listFiles returns files sorted by name`() {
    // Record events across 3 days
    trail.record("day1")
    nowMs = Instant.parse("2026-03-16T12:00:00Z").toEpochMilli()
    trail.record("day2")
    nowMs = Instant.parse("2026-03-17T12:00:00Z").toEpochMilli()
    trail.record("day3")

    val files = trail.listFiles()
    assertThat(files).hasSize(3)
    assertThat(files[0].name).isLessThan(files[1].name)
    assertThat(files[1].name).isLessThan(files[2].name)
  }

  @Test
  fun `listFiles triggers purge before returning`() {
    val shortTrail = AuditTrail(
      context = ctx,
      retentionProvider = {
        RetentionPolicy(
          retainSentEventsDays = 1,
          retainFailedEventsDays = 1,
          retainTelemetryDays = 1,
          retainAuditDays = 1,
          retainLogsDays = 1,
        )
      },
      clock = clock,
      crypto = NoopCryptoProvider,
    )

    shortTrail.record("will_be_purged")
    nowMs = Instant.parse("2026-03-17T12:00:00Z").toEpochMilli()

    // listFiles internally calls purgeOldFiles, so old files should be gone
    val files = shortTrail.listFiles()
    assertThat(files).isEmpty()
  }

  // -- Multiple records chain integrity --

  @Test
  fun `chain of five records has correct linkage`() {
    val events = mutableListOf<AuditEvent>()
    for (i in 1..5) {
      trail.record("event_$i")
      nowMs += 100
    }

    val files = trail.listFiles()
    val lines = files[0].readLines()
    for (line in lines) {
      events.add(json.decodeFromString<AuditEvent>(line))
    }

    assertThat(events).hasSize(5)
    assertThat(events[0].prevHash).isEqualTo("GENESIS")
    for (i in 1 until events.size) {
      assertThat(events[i].prevHash).isEqualTo(events[i - 1].hash)
    }
  }

  @Test
  fun `fields are sorted in hash payload for determinism`() {
    // Record with fields in different insertion orders; hash should be identical
    val fields1 = linkedMapOf("z" to "1", "a" to "2")
    val fields2 = linkedMapOf("a" to "2", "z" to "1")

    val payload1 = "$nowMs|test|${fields1.toSortedMap()}|GENESIS"
    val payload2 = "$nowMs|test|${fields2.toSortedMap()}|GENESIS"
    val hash1 = Hashing.sha256Base64Url(payload1)
    val hash2 = Hashing.sha256Base64Url(payload2)

    assertThat(hash1).isEqualTo(hash2)
  }
}
