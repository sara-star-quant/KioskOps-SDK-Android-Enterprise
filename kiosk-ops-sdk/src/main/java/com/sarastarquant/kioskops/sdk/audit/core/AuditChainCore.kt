/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit.core

import com.sarastarquant.kioskops.sdk.util.Hashing

/** One stored link of the audit hash chain, independent of the storage layer. */
internal data class ChainLink(
  val seq: Long,
  val id: String,
  val ts: Long,
  val name: String,
  val fieldsJson: String,
  val prevHash: String,
  val hash: String,
  val formatVersion: Int,
)

/** Outcome of verifying one ordered page of chain links. */
internal sealed interface PageResult {
  /** Every link verified; [lastHash] and [lastSeq] seed the next page. */
  data class Ok(val lastHash: String, val lastSeq: Long) : PageResult

  /** The first failing link, by [seq]. */
  data class Broken(val seq: Long, val reason: String) : PageResult
}

/** Pure hash chain logic shared by the audit trails. No Android or storage dependencies. */
internal object AuditChainCore {

  /**
   * Builds the string hashed for one link. For [AuditHashFormat.FILE_V0], [id] is ignored and
   * [fieldsJson] carries the rendered sorted field map.
   */
  fun content(
    format: AuditHashFormat,
    id: String,
    ts: Long,
    name: String,
    fieldsJson: String,
    prevHash: String,
  ): String = when (format) {
    AuditHashFormat.FILE_V0 -> "$ts|$name|$fieldsJson|$prevHash"
    AuditHashFormat.V1 -> "$id|$ts|$name|$fieldsJson|$prevHash"
    AuditHashFormat.V2 -> buildString {
      for (value in arrayOf(id, ts.toString(), name, fieldsJson, prevHash)) {
        append(value.toByteArray(Charsets.UTF_8).size).append(':').append(value)
      }
    }
  }

  fun hash(content: String): String = Hashing.sha256Base64Url(content)

  /**
   * Verifies [events] in order: seq strictly increases, each prevHash links to the previous hash
   * (the first to [expectedPrevHash]), and each stored hash matches its recomputed content.
   * An empty page is Ok with [expectedPrevHash] and lastSeq 0.
   */
  fun verifyPage(events: List<ChainLink>, expectedPrevHash: String): PageResult {
    var prevHash = expectedPrevHash
    var lastSeq = 0L
    for ((index, e) in events.withIndex()) {
      val reason = linkError(e, prevHash, if (index == 0) null else lastSeq)
      if (reason != null) return PageResult.Broken(e.seq, reason)
      prevHash = e.hash
      lastSeq = e.seq
    }
    return PageResult.Ok(prevHash, lastSeq)
  }

  private fun linkError(e: ChainLink, prevHash: String, previousSeq: Long?): String? {
    val format = AuditHashFormat.forStoredVersion(e.formatVersion)
    return when {
      previousSeq != null && e.seq <= previousSeq -> "seq ${e.seq} does not follow $previousSeq"
      e.prevHash != prevHash -> "prevHash does not match the previous link"
      format == null -> "unknown format version ${e.formatVersion}"
      hash(content(format, e.id, e.ts, e.name, e.fieldsJson, e.prevHash)) != e.hash -> "hash does not match content"
      else -> null
    }
  }
}
