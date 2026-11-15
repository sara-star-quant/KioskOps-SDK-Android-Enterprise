/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit

import android.content.Context
import com.sarastarquant.kioskops.sdk.audit.core.AuditChainCore
import com.sarastarquant.kioskops.sdk.audit.core.AuditCheckpoint
import com.sarastarquant.kioskops.sdk.audit.core.AuditHashFormat
import com.sarastarquant.kioskops.sdk.audit.core.ChainLink
import com.sarastarquant.kioskops.sdk.audit.core.PageResult
import com.sarastarquant.kioskops.sdk.audit.db.AuditChainState
import com.sarastarquant.kioskops.sdk.audit.db.AuditDatabase
import com.sarastarquant.kioskops.sdk.audit.db.AuditEventEntity
import com.sarastarquant.kioskops.sdk.compliance.RetentionPolicy
import com.sarastarquant.kioskops.sdk.util.Clock
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
 * @property attestationProvider Optional provider for signing audit entries.
 */
class PersistentAuditTrail(
  private val context: Context,
  private val retentionProvider: () -> RetentionPolicy,
  private val clock: Clock = Clock.SYSTEM,
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
    internal const val VERIFY_PAGE_SIZE = 500
    private val RECORD_FORMAT = AuditHashFormat.V2
  }

  /**
   * Record an audit event.
   *
   * @param name Event name (e.g., "sdk_initialized").
   * @param fields Additional key-value context.
   * @param sign If true and attestation provider is available, sign the entry.
   * @param userId Optional user identifier for GDPR data subject tracking.
   * @since 0.5.0 userId parameter added
   */
  suspend fun record(
    name: String,
    fields: Map<String, String> = emptyMap(),
    sign: Boolean = false,
    userId: String? = null,
  ) {
    mutex.withLock {
      val ts = clock.nowMs()
      val id = UUID.randomUUID().toString()

      // Get current chain state or initialize
      val chainState = dao.getChainState() ?: initializeChainState()
      val prevHash = chainState.lastHash

      // Build event content for hashing
      val fieldsJson = json.encodeToString(fields)
      val eventContent = AuditChainCore.content(RECORD_FORMAT, id, ts, name, fieldsJson, prevHash)
      val hash = AuditChainCore.hash(eventContent)

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
        userId = userId,
        seq = chainState.eventCount + 1,
        formatVersion = RECORD_FORMAT.version,
      )

      // Update chain state
      val newChainState = chainState.copy(
        lastHash = hash,
        lastTs = ts,
        eventCount = chainState.eventCount + 1,
      )

      // Atomic insert
      dao.insertEventWithChainUpdate(event, newChainState)
    }
  }

  /**
   * Verify the integrity of the audit chain, reading at most [VERIFY_PAGE_SIZE] rows at a time.
   *
   * When [fromTs] or [toTs] is given, the range resolves to the contiguous seq interval from the
   * lowest to the highest seq among rows whose ts is inside the window, and that whole interval is
   * verified. Rows inside the interval but outside the ts window are verified too, so a backward
   * clock step cannot cause a false break.
   *
   * Seed: when the verified interval starts at the lowest stored seq, the first row must link to
   * the recorded prune point (`prunedThroughHash`, GENESIS if retention never pruned), so deleting
   * the oldest rows breaks verification. A ranged interval that starts later cannot know its
   * predecessor and seeds from its first row's own prevHash.
   *
   * Pages are read without holding the record lock, so retention can prune between pages. When the
   * prune point moved during the run, the whole verification runs once more from the start and that
   * result is returned, even if retention moved again during the rerun.
   *
   * @param fromTs Optional start timestamp (inclusive).
   * @param toTs Optional end timestamp (inclusive).
   * @return Verification result.
   */
  suspend fun verifyChainIntegrity(
    fromTs: Long? = null,
    toTs: Long? = null,
  ): ChainVerificationResult {
    val prunedBefore = dao.getChainState()?.prunedThroughSeq
    val result = verifyOnce(fromTs, toTs)
    if (dao.getChainState()?.prunedThroughSeq == prunedBefore) return result
    return verifyOnce(fromTs, toTs)
  }

  private suspend fun verifyOnce(fromTs: Long?, toTs: Long?): ChainVerificationResult {
    val effectiveFromTs = fromTs ?: 0L
    val effectiveToTs = toTs ?: Long.MAX_VALUE

    val seqRange = resolveSeqRange(fromTs, toTs)
      ?: return ChainVerificationResult.EmptyRange(effectiveFromTs, effectiveToTs)
    var afterSeq = seqRange.first - 1
    val maxSeq = seqRange.last

    val chainStartSeq = dao.getSeqBoundsInRange(Long.MIN_VALUE, Long.MAX_VALUE).minSeq
    val pruneSeed = dao.getChainState()?.prunedThroughHash ?: GENESIS_HASH
    var expectedPrevHash: String? = null
    var count = 0
    var firstTs = 0L
    var lastTs = 0L

    do {
      val page = dao.getEventsPageBySeq(afterSeq, VERIFY_PAGE_SIZE).filter { it.seq <= maxSeq }
      if (page.isEmpty()) break
      if (expectedPrevHash == null) firstTs = page.first().ts
      val seed = expectedPrevHash ?: startSeed(page.first(), chainStartSeq, pruneSeed)

      val result = AuditChainCore.verifyPage(page.map { it.toLink() }, seed)
      val verified = if (result is PageResult.Broken) page.takeWhile { it.seq < result.seq } else page

      signatureFailure(verified)?.let { return it }
      if (result is PageResult.Broken) return brokenResult(page.first { it.seq == result.seq }, verified, seed)

      result as PageResult.Ok
      expectedPrevHash = result.lastHash
      afterSeq = result.lastSeq
      count += page.size
      lastTs = page.last().ts
    } while (page.size == VERIFY_PAGE_SIZE)

    if (count == 0) return ChainVerificationResult.EmptyRange(effectiveFromTs, effectiveToTs)
    return ChainVerificationResult.Valid(eventCount = count, fromTs = firstTs, toTs = lastTs)
  }

  /** The chain's first stored row links to the prune point; any later row can only vouch for itself. */
  private fun startSeed(first: AuditEventEntity, chainStartSeq: Long?, pruneSeed: String): String =
    if (first.seq == chainStartSeq) pruneSeed else first.prevHash

  /** Unranged: the whole chain. Ranged: the seq interval spanned by the ts window, or null if empty. */
  private suspend fun resolveSeqRange(fromTs: Long?, toTs: Long?): LongRange? {
    if (fromTs == null && toTs == null) return 1L..Long.MAX_VALUE
    val bounds = dao.getSeqBoundsInRange(fromTs ?: 0L, toTs ?: Long.MAX_VALUE)
    val (min, max) = bounds.minSeq to bounds.maxSeq
    return if (min == null || max == null) null else min..max
  }

  private fun AuditEventEntity.toLink() =
    ChainLink(seq, id, ts, name, fieldsJson, prevHash, hash, formatVersion)

  private fun signatureFailure(events: List<AuditEventEntity>): ChainVerificationResult? {
    val provider = attestationProvider?.invoke() ?: return null
    val bad = events.firstOrNull { event ->
      val format = AuditHashFormat.forStoredVersion(event.formatVersion)
      event.signature != null && format != null &&
        !provider.verifySignature(
          AuditChainCore.content(format, event.id, event.ts, event.name, event.fieldsJson, event.prevHash),
          event.signature,
        )
    }
    return bad?.let { ChainVerificationResult.SignatureInvalid(it.id, it.ts, "Signature verification failed") }
  }

  /**
   * A link failure is a chain break; anything else (hash, unknown format version) is the event itself.
   * HashMismatch has no reason field, so an unknown format version is not distinguishable there.
   */
  private fun brokenResult(
    event: AuditEventEntity,
    verified: List<AuditEventEntity>,
    seed: String,
  ): ChainVerificationResult {
    val expected = verified.lastOrNull()?.hash ?: seed
    return if (event.prevHash != expected) {
      ChainVerificationResult.Broken(event.id, event.ts, expected, event.prevHash)
    } else {
      ChainVerificationResult.HashMismatch(event.id, event.ts)
    }
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
   *
   * Deletes the longest chain prefix whose events are all older than the cutoff, and records the
   * last deleted seq and hash as the prune point in the same transaction. An event older than the
   * cutoff that follows a newer one (backward clock step) waits until its predecessors expire.
   * Rows are never deleted early, but a row written while the clock ran ahead blocks every later
   * prune until the cutoff passes that row's timestamp, so data minimization depends on a sane
   * device clock.
   */
  suspend fun applyRetention() {
    val retention = retentionProvider()
    val cutoffMs = clock.nowMs() - retention.retainAuditDays.toLong() * 24 * 60 * 60 * 1000

    mutex.withLock {
      val boundary = dao.getPruneBoundary(cutoffMs) ?: return@withLock
      val state = dao.getChainState() ?: return@withLock
      dao.pruneThrough(state.copy(prunedThroughSeq = boundary.seq, prunedThroughHash = boundary.hash))
    }
  }

  /**
   * Snapshot the chain head for a server to pin.
   *
   * Head fields come from the newest stored row, not the cached chain state, so deleted tail rows
   * show up as a lower headSeq. The link names the last acknowledged checkpoint ([acknowledgeCheckpoint])
   * and the hash this database now holds at that seq. When [sign] is true and the device key cannot sign (no provider,
   * or signing returned null), the checkpoint is returned unsigned and a `checkpoint_unsigned`
   * audit event is recorded.
   *
   * @param sign Sign [AuditCheckpoint.signingPayload] with the device attestation key.
   * @since 1.6.0
   */
  internal suspend fun createCheckpoint(sign: Boolean): AuditCheckpoint {
    val unsigned = mutex.withLock {
      val state = dao.getChainState()
      val head = dao.getHeadEvent()
      val prunedSeq = state?.prunedThroughSeq ?: 0L
      val prunedHash = state?.prunedThroughHash ?: GENESIS_HASH
      val linkSeq = state?.lastAckedSeq?.takeIf { it > 0 }
      val linkHash = linkSeq?.let { seq ->
        dao.getEventsPageBySeq(seq - 1, 1).firstOrNull { it.seq == seq }?.hash
          ?: prunedHash.takeIf { seq == prunedSeq }
      }
      AuditCheckpoint(
        headSeq = head?.seq ?: prunedSeq,
        headHash = head?.hash ?: prunedHash,
        eventCount = state?.eventCount ?: 0L,
        chainGeneration = state?.chainGeneration ?: 1,
        prunedThroughSeq = prunedSeq,
        prunedThroughHash = prunedHash,
        linkSeq = linkSeq,
        linkHash = linkHash,
        signature = null,
      )
    }
    if (!sign) return unsigned

    val provider = attestationProvider?.invoke()
    val signature = provider?.signAuditEntry(unsigned.signingPayload())
    if (signature == null) {
      val reason = if (provider == null) "no_attestation_provider" else "signing_failed"
      record("checkpoint_unsigned", mapOf("reason" to reason, "headSeq" to unsigned.headSeq.toString()))
    }
    return unsigned.copy(signature = signature)
  }

  /**
   * Record that a server accepted [checkpoint]; the next checkpoint links to its headSeq.
   * Monotonic: an older acknowledgement arriving late never moves the link back.
   */
  internal suspend fun acknowledgeCheckpoint(checkpoint: AuditCheckpoint) {
    mutex.withLock {
      val state = dao.getChainState() ?: return@withLock
      if (checkpoint.headSeq > state.lastAckedSeq) dao.upsertChainState(state.copy(lastAckedSeq = checkpoint.headSeq))
    }
  }

  /**
   * Get all events in a time range.
   */
  suspend fun getEventsInRange(fromTs: Long, toTs: Long): List<AuditEventEntity> {
    return dao.getEventsInRange(fromTs, toTs)
  }

  /**
   * Get all events for a specific user.
   * @since 0.5.0
   */
  suspend fun getEventsByUserId(userId: String): List<AuditEventEntity> {
    return dao.getEventsByUserId(userId)
  }

  /**
   * Delete all events for a specific user.
   *
   * Removes chain links: [verifyChainIntegrity] reports Broken for a chain that contains deleted
   * rows, and deleting the row at the last acknowledged checkpoint makes a pinning server report the
   * next checkpoint as Forked.
   * @since 0.5.0
   */
  suspend fun deleteEventsByUserId(userId: String): Int {
    return dao.deleteEventsByUserId(userId)
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
}
