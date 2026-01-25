/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.audit

import android.content.Context
import com.peterz.kioskops.sdk.audit.db.AuditChainState
import com.peterz.kioskops.sdk.audit.db.AuditDatabase
import com.peterz.kioskops.sdk.audit.db.AuditEventEntity
import com.peterz.kioskops.sdk.compliance.RetentionPolicy
import com.peterz.kioskops.sdk.crypto.CryptoProvider
import com.peterz.kioskops.sdk.util.Clock
import com.peterz.kioskops.sdk.util.Hashing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.GZIPOutputStream

/**
 * Room-backed persistent audit trail with hash chain integrity.
 *
 * Unlike the file-based AuditTrail, this implementation:
 * - Persists the hash chain across app restarts
 * - Supports signed audit entries with device attestation
 * - Provides integrity verification API
 *
 * @property context Android context.
 * @property retentionProvider Provider for retention policy.
 * @property clock Time provider for testability.
 * @property crypto Optional crypto provider for encryption (currently unused, reserved for future).
 * @property attestationProvider Optional provider for signing audit entries.
 */
class PersistentAuditTrail(
  private val context: Context,
  private val retentionProvider: () -> RetentionPolicy,
  private val clock: Clock = Clock.SYSTEM,
  private val crypto: CryptoProvider? = null,
  private val attestationProvider: (() -> DeviceAttestationProvider)? = null,
) {

  private val database: AuditDatabase by lazy {
    AuditDatabase.getInstance(context)
  }

  private val dao by lazy { database.auditDao() }

  private val mutex = Mutex()

  private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
  }

  companion object {
    private const val GENESIS_HASH = "GENESIS"
  }

  /**
   * Record an audit event.
   *
   * @param name Event name (e.g., "sdk_initialized").
   * @param fields Additional key-value context.
   * @param sign If true and attestation provider is available, sign the entry.
   */
  suspend fun record(
    name: String,
    fields: Map<String, String> = emptyMap(),
    sign: Boolean = false,
  ) {
    mutex.withLock {
      val ts = clock.nowMs()
      val id = UUID.randomUUID().toString()

      // Get current chain state or initialize
      val chainState = dao.getChainState() ?: initializeChainState()
      val prevHash = chainState.lastHash

      // Build event content for hashing
      val fieldsJson = json.encodeToString(fields)
      val eventContent = buildEventContent(id, ts, name, fieldsJson, prevHash)
      val hash = Hashing.sha256Base64Url(eventContent)

      // Optional signing
      var signature: String? = null
      var attestationBlob: ByteArray? = null

      if (sign && attestationProvider != null) {
        val provider = attestationProvider.invoke()
        signature = provider.signAuditEntry(eventContent)
        if (signature != null) {
          attestationBlob = provider.getAttestationBlob()
        }
      }

      // Create event entity
      val event = AuditEventEntity(
        id = id,
        ts = ts,
        name = name,
        fieldsJson = fieldsJson,
        prevHash = prevHash,
        hash = hash,
        signature = signature,
        deviceAttestationBlob = attestationBlob,
        chainGeneration = chainState.chainGeneration,
      )

      // Update chain state
      val newChainState = AuditChainState(
        id = 1,
        lastHash = hash,
        lastTs = ts,
        chainGeneration = chainState.chainGeneration,
        eventCount = chainState.eventCount + 1,
      )

      // Atomic insert
      dao.insertEventWithChainUpdate(event, newChainState)
    }
  }

  /**
   * Verify the integrity of the audit chain.
   *
   * @param fromTs Optional start timestamp (inclusive).
   * @param toTs Optional end timestamp (inclusive).
   * @return Verification result.
   */
  suspend fun verifyChainIntegrity(
    fromTs: Long? = null,
    toTs: Long? = null,
  ): ChainVerificationResult {
    val effectiveFromTs = fromTs ?: 0L
    val effectiveToTs = toTs ?: Long.MAX_VALUE

    val events = dao.getEventsForVerification(effectiveFromTs, effectiveToTs)

    if (events.isEmpty()) {
      return ChainVerificationResult.EmptyRange(effectiveFromTs, effectiveToTs)
    }

    var expectedPrevHash = events.first().prevHash

    for (event in events) {
      // Verify the event's own hash
      val expectedHash = Hashing.sha256Base64Url(
        buildEventContent(event.id, event.ts, event.name, event.fieldsJson, event.prevHash)
      )

      if (event.hash != expectedHash) {
        return ChainVerificationResult.HashMismatch(
          eventId = event.id,
          eventTs = event.ts,
        )
      }

      // Verify chain linkage (skip for first event in range)
      if (event != events.first() && event.prevHash != expectedPrevHash) {
        return ChainVerificationResult.Broken(
          brokenAtId = event.id,
          brokenAtTs = event.ts,
          expectedPrevHash = expectedPrevHash,
          actualPrevHash = event.prevHash,
        )
      }

      // Verify signature if present
      if (event.signature != null && attestationProvider != null) {
        val provider = attestationProvider.invoke()
        val content = buildEventContent(event.id, event.ts, event.name, event.fieldsJson, event.prevHash)
        if (!provider.verifySignature(content, event.signature)) {
          return ChainVerificationResult.SignatureInvalid(
            eventId = event.id,
            eventTs = event.ts,
            reason = "Signature verification failed",
          )
        }
      }

      expectedPrevHash = event.hash
    }

    return ChainVerificationResult.Valid(
      eventCount = events.size,
      fromTs = events.first().ts,
      toTs = events.last().ts,
    )
  }

  /**
   * Get statistics about the audit trail.
   */
  suspend fun getStatistics(): AuditStatistics {
    val totalEvents = dao.countEvents()
    val oldestEvent = dao.getOldestEvent()
    val newestEvent = dao.getLatestEvent()
    val chainState = dao.getChainState()
    val eventCounts = dao.getEventNameCounts()

    // Count signed events
    val allEvents = dao.getAllEvents()
    val signedCount = allEvents.count { it.signature != null }.toLong()

    return AuditStatistics(
      totalEvents = totalEvents,
      oldestEventTs = oldestEvent?.ts,
      newestEventTs = newestEvent?.ts,
      chainGeneration = chainState?.chainGeneration ?: 1,
      signedEventCount = signedCount,
      eventsByName = eventCounts.associate { it.name to it.count },
    )
  }

  /**
   * Export signed audit events to a file.
   *
   * @param fromTs Start timestamp.
   * @param toTs End timestamp.
   * @return The exported file (gzipped JSONL).
   */
  suspend fun exportSignedAuditRange(fromTs: Long, toTs: Long): File {
    val events = dao.getEventsInRange(fromTs, toTs)

    val exportDir = File(context.cacheDir, "kioskops_audit_exports")
    exportDir.mkdirs()

    val fileName = "audit_export_${fromTs}_${toTs}.jsonl.gz"
    val file = File(exportDir, fileName)

    GZIPOutputStream(FileOutputStream(file)).use { gzip ->
      for (event in events) {
        val line = json.encodeToString(
          mapOf(
            "id" to event.id,
            "ts" to event.ts.toString(),
            "name" to event.name,
            "fields" to event.fieldsJson,
            "prevHash" to event.prevHash,
            "hash" to event.hash,
            "signature" to (event.signature ?: ""),
            "chainGeneration" to event.chainGeneration.toString(),
          )
        ) + "\n"
        gzip.write(line.toByteArray(Charsets.UTF_8))
      }
    }

    return file
  }

  /**
   * Apply retention policy to delete old events.
   */
  suspend fun applyRetention() {
    val retention = retentionProvider()
    val cutoffMs = clock.nowMs() - retention.retainAuditDays.toLong() * 24 * 60 * 60 * 1000

    mutex.withLock {
      dao.deleteEventsBefore(cutoffMs)
    }
  }

  /**
   * Get all events in a time range.
   */
  suspend fun getEventsInRange(fromTs: Long, toTs: Long): List<AuditEventEntity> {
    return dao.getEventsInRange(fromTs, toTs)
  }

  private suspend fun initializeChainState(): AuditChainState {
    val state = AuditChainState(
      id = 1,
      lastHash = GENESIS_HASH,
      lastTs = clock.nowMs(),
      chainGeneration = 1,
      eventCount = 0,
    )
    dao.upsertChainState(state)
    return state
  }

  private fun buildEventContent(
    id: String,
    ts: Long,
    name: String,
    fieldsJson: String,
    prevHash: String,
  ): String {
    // Deterministic content format for hashing
    return "$id|$ts|$name|$fieldsJson|$prevHash"
  }
}
