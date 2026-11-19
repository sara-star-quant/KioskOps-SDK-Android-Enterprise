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
import com.sarastarquant.kioskops.sdk.audit.core.ErasedRun
import com.sarastarquant.kioskops.sdk.audit.core.parseErasedRuns
import com.sarastarquant.kioskops.sdk.audit.core.toFieldValue
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
import java.util.Base64
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
    private const val ERASE_EVENT = "user_data_deleted"
    private const val RUNS_FIELD = "runs"
    private const val ACK_ERASED_EVENT = "acked_head_erased"
    private const val KEY_EVENT = "signing_key"
    private const val KEY_CHANGED_EVENT = "signing_key_changed"
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
      val provider = if (sign) attestationProvider?.invoke() else null
      provider?.let { guardSigningKey(it) }
      val chainState = dao.getChainState() ?: initializeChainState()
      val (event, newChainState) = nextEntry(chainState, name, fields, userId, provider)
      dao.insertEventWithChainUpdate(event, newChainState)
    }
  }

  /**
   * The row that follows [chainState] and the chain state after it. Signs with [provider] when given.
   * Callers hold [mutex].
   */
  private fun nextEntry(
    chainState: AuditChainState,
    name: String,
    fields: Map<String, String>,
    userId: String?,
    provider: DeviceAttestationProvider?,
  ): Pair<AuditEventEntity, AuditChainState> {
    val ts = clock.nowMs()
    val id = UUID.randomUUID().toString()
    val prevHash = chainState.lastHash

    val fieldsJson = json.encodeToString(fields)
    val eventContent = AuditChainCore.content(RECORD_FORMAT, id, ts, name, fieldsJson, prevHash)
    val hash = AuditChainCore.hash(eventContent)

    val signature = provider?.signAuditEntry(eventContent)
    val attestationBlob = if (signature != null) provider.getAttestationBlob() else null

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
    val newChainState = chainState.copy(
      lastHash = hash,
      lastTs = ts,
      eventCount = chainState.eventCount + 1,
    )
    return event to newChainState
  }

  /**
   * Erase every row of [userId] and append one `user_data_deleted` row that lists the removed seq
   * runs by boundary hash, in a single transaction, so the chain keeps verifying. The row carries
   * [extraFields] and the runs, never the user id or any erased content. Signs the row when [sign] is
   * true and a provider exists; a signing exception rolls the whole erase back.
   *
   * @return Number of rows removed.
   */
  internal suspend fun eraseUser(
    userId: String,
    extraFields: Map<String, String> = emptyMap(),
    sign: Boolean = false,
  ): Int = mutex.withLock {
    val provider = if (sign) attestationProvider?.invoke() else null
    dao.eraseRowsWithRecord(userId) { rows ->
      val state = dao.getChainState() ?: initializeChainState()
      val runs = erasedRunsOf(rows, state.lastAckedSeq)
      val fields = extraFields + mapOf("auditDeleted" to rows.size.toString(), RUNS_FIELD to runs.toFieldValue())
      nextEntry(state, ERASE_EVENT, fields, null, provider)
    }
  }

  /** Runs recorded by erase rows, keeping only runs that sit before the row that records them. */
  private suspend fun loadErasedRuns(): List<ErasedRun> = dao.getEventsByName(ERASE_EVENT).flatMap { row ->
    val field = runCatching { json.decodeFromString<Map<String, String>>(row.fieldsJson)[RUNS_FIELD] }.getOrNull()
    (field?.let(::parseErasedRuns) ?: emptyList()).filter { it.toSeq < row.seq }
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
    val before = verifyStamp()
    val result = verifyOnce(fromTs, toTs)
    if (verifyStamp() == before) return result
    return verifyOnce(fromTs, toTs)
  }

  /** What a concurrent prune or erase changes while pages are read without the lock. */
  private suspend fun verifyStamp(): Pair<Long?, Int> =
    dao.getChainState()?.prunedThroughSeq to dao.getEventsByName(ERASE_EVENT).size

  private suspend fun verifyOnce(fromTs: Long?, toTs: Long?): ChainVerificationResult {
    val effectiveFromTs = fromTs ?: 0L
    val effectiveToTs = toTs ?: Long.MAX_VALUE

    val seqRange = resolveSeqRange(fromTs, toTs)
      ?: return ChainVerificationResult.EmptyRange(effectiveFromTs, effectiveToTs)
    var afterSeq = seqRange.first - 1
    val maxSeq = seqRange.last

    val pageContext = PageContext(
      chainStartSeq = dao.getSeqBoundsInRange(Long.MIN_VALUE, Long.MAX_VALUE).minSeq,
      pruneSeed = dao.getChainState()?.prunedThroughHash ?: GENESIS_HASH,
      gaps = loadErasedRuns(),
      keyMarkers = loadKeyMarkers(),
    )
    var expectedPrevHash: String? = null
    var count = 0
    var firstTs = 0L
    var lastTs = 0L

    do {
      val page = dao.getEventsPageBySeq(afterSeq, VERIFY_PAGE_SIZE).filter { it.seq <= maxSeq }
      if (page.isEmpty()) break
      if (expectedPrevHash == null) firstTs = page.first().ts
      val (seed, result) = checkPage(page, expectedPrevHash, afterSeq, pageContext)
      val verified = if (result is PageResult.Broken) page.takeWhile { it.seq < result.seq } else page

      signatureFailure(verified, pageContext.keyMarkers)?.let { return it }
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

  /**
   * Before signing: when the provider's key differs from the one the newest marker row names, record
   * the change. The first marker (`signing_key`) claims no change. Later ones (`signing_key_changed`)
   * start a new chain generation, so an auditor can read the discontinuity and a pinning server sees
   * the generation change. Callers hold [mutex]. A provider that cannot name its key is skipped.
   */
  private suspend fun guardSigningKey(provider: DeviceAttestationProvider) {
    val keyId = provider.signingKeyId()
    val publicKey = provider.signingPublicKey()?.let { Base64.getEncoder().encodeToString(it) }
    if (keyId == null || publicKey == null) return
    val newestId = keyMarkerRows().maxByOrNull { it.seq }?.let { markerField(it, "keyId") }
    if (newestId != keyId) writeKeyMarker(keyId, publicKey, newestId)
  }

  /** Appends the marker row for [keyId]; [oldKeyId] is the key it replaces, null for the first marker. */
  private suspend fun writeKeyMarker(keyId: String, publicKey: String, oldKeyId: String?) {
    val state = dao.getChainState() ?: initializeChainState()
    val changed = oldKeyId != null
    val markerState = if (changed) state.copy(chainGeneration = state.chainGeneration + 1) else state
    val fields = buildMap {
      put("keyId", keyId)
      put("publicKey", publicKey)
      if (changed) {
        put("oldKeyId", oldKeyId)
        put("reason", "key_replaced")
      }
    }
    val (event, next) = nextEntry(markerState, if (changed) KEY_CHANGED_EVENT else KEY_EVENT, fields, null, null)
    dao.insertEventWithChainUpdate(event, next)
  }

  private suspend fun keyMarkerRows(): List<AuditEventEntity> =
    dao.getEventsByName(KEY_EVENT) + dao.getEventsByName(KEY_CHANGED_EVENT)

  /** Public keys named by marker rows, in chain order. Rows with an unreadable key are skipped. */
  private suspend fun loadKeyMarkers(): List<KeyMarker> = keyMarkerRows().sortedBy { it.seq }.mapNotNull { row ->
    val key = markerField(row, "publicKey")?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
    key?.let { KeyMarker(row.seq, it) }
  }

  /** What every page of one verification run shares. */
  private class PageContext(
    val chainStartSeq: Long?,
    val pruneSeed: String,
    val gaps: List<ErasedRun>,
    val keyMarkers: List<KeyMarker>,
  )

  /** Verifies one page; returns the seed it was checked against and the result. */
  private fun checkPage(
    page: List<AuditEventEntity>,
    expectedPrevHash: String?,
    afterSeq: Long,
    context: PageContext,
  ): Pair<String, PageResult> {
    val seed = expectedPrevHash ?: startSeed(page.first(), context.chainStartSeq, context.pruneSeed)
    val knownAfter = afterSeq.takeIf { expectedPrevHash != null }
    return seed to AuditChainCore.verifyPage(page.map { it.toLink() }, seed, context.gaps, knownAfter)
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

  private fun signatureFailure(events: List<AuditEventEntity>, markers: List<KeyMarker>): ChainVerificationResult? {
    val provider = attestationProvider?.invoke() ?: return null
    val bad = events.firstOrNull { event ->
      val format = AuditHashFormat.forStoredVersion(event.formatVersion)
      event.signature != null && format != null &&
        !signatureHolds(
          provider,
          markers,
          event.seq,
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
            "seq" to event.seq.toString(),
            "formatVersion" to event.formatVersion.toString(),
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
      val boundary = dao.getPruneBoundary(cutoffMs)
      val state = dao.getChainState()
      if (boundary != null && state != null && keysSurvive(boundary)) {
        dao.pruneThrough(dao.getChainState()!!.copy(prunedThroughSeq = boundary.seq, prunedThroughHash = boundary.hash))
      }
    }
  }

  /**
   * True when pruning through [boundary] leaves every surviving row with a key marker that governs it.
   * When the governing marker would be pruned and no later marker survives, a copy of it is appended
   * first. When a later marker survives, the rows between the boundary and that marker still need the
   * old one, so the prune waits until that later marker is itself old enough. Callers hold [mutex].
   */
  private suspend fun keysSurvive(boundary: AuditEventEntity): Boolean {
    val markers = keyMarkerRows()
    val governing = markers.filter { it.seq <= boundary.seq }.maxByOrNull { it.seq }
    return when {
      governing == null -> true
      markers.any { it.seq > boundary.seq } -> false
      else -> {
        val fields = listOf("keyId", "publicKey").associateWith { markerField(governing, it).orEmpty() }
        val (event, next) = nextEntry(dao.getChainState()!!, KEY_EVENT, fields, null, null)
        dao.insertEventWithChainUpdate(event, next)
        true
      }
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
    if (sign) mutex.withLock { attestationProvider?.invoke()?.let { guardSigningKey(it) } }
    val unsigned = mutex.withLock {
      val state = dao.getChainState()
      val head = dao.getHeadEvent()
      val prunedSeq = state?.prunedThroughSeq ?: 0L
      val prunedHash = state?.prunedThroughHash ?: GENESIS_HASH
      val linkSeq = state?.lastAckedSeq?.takeIf { it > 0 }
      val linkHash = linkSeq?.let { linkHashFor(it, prunedSeq, prunedHash) }
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

  /** Hash this database holds at [seq]: the stored row, the prune point, or a recorded erased run. */
  private suspend fun linkHashFor(seq: Long, prunedSeq: Long, prunedHash: String): String? =
    dao.getEventsPageBySeq(seq - 1, 1).firstOrNull { it.seq == seq }?.hash
      ?: prunedHash.takeIf { seq == prunedSeq }
      ?: loadErasedRuns().firstOrNull { it.ackedSeq == seq }?.ackedHash
      ?: dao.getEventsByName(ACK_ERASED_EVENT).firstNotNullOfOrNull { row ->
        markerField(row, "hash").takeIf { markerField(row, "seq") == seq.toString() }
      }

  /**
   * Record that a server accepted [checkpoint]; the next checkpoint links to its headSeq.
   * Monotonic: an older acknowledgement arriving late never moves the link back.
   */
  internal suspend fun acknowledgeCheckpoint(checkpoint: AuditCheckpoint) {
    mutex.withLock {
      val state = dao.getChainState()
      if (state != null && checkpoint.headSeq > state.lastAckedSeq) {
        val acked = state.copy(lastAckedSeq = checkpoint.headSeq)
        val gone = checkpoint.headSeq > state.prunedThroughSeq &&
          dao.getEventsPageBySeq(checkpoint.headSeq - 1, 1).none { it.seq == checkpoint.headSeq }
        if (gone) {
          // An erase removed the acknowledged head while the request was in flight: keep its hash.
          val fields = mapOf("seq" to checkpoint.headSeq.toString(), "hash" to checkpoint.headHash)
          val (event, next) = nextEntry(acked, ACK_ERASED_EVENT, fields, null, null)
          dao.insertEventWithChainUpdate(event, next)
        } else {
          dao.upsertChainState(acked)
        }
      }
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
  @Deprecated(
    "Removes chain links without recording the erase. Use DataRightsManager.deleteUserData, which keeps the chain verifiable.",
  )
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
