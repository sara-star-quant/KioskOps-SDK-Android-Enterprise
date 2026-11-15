/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit.core

import kotlinx.serialization.Serializable

/**
 * A signed snapshot of the audit chain head, sent with sync batches so a server can pin it.
 *
 * The server pins each acknowledged checkpoint and checks the next one with
 * [AuditChainCore.compareToPin]: counts must not go down and the link must name the pinned head.
 *
 * @property headSeq Seq of the newest stored event; [prunedThroughSeq] when every event was pruned.
 * @property headHash Hash of the event at [headSeq]; [prunedThroughHash] when every event was pruned.
 * @property eventCount Events ever recorded in this chain, pruned ones included.
 * @property chainGeneration Chain generation of the device audit chain.
 * @property prunedThroughSeq Highest seq removed by retention; 0 when nothing was pruned. A database
 *   upgraded after retention removed its oldest events reports 0 with a non-GENESIS [prunedThroughHash].
 * @property prunedThroughHash Hash of the event at [prunedThroughSeq]; `GENESIS` when nothing was pruned.
 * @property linkSeq headSeq of the last checkpoint the server acknowledged; null before the first ack.
 * @property linkHash Hash the device holds at [linkSeq] when this checkpoint is built ([prunedThroughHash]
 *   when retention pruned exactly through [linkSeq]); null when [linkSeq] is null or the row is gone.
 * @property signature Base64 device-key signature over [signingPayload], or null when unsigned.
 * @since 1.6.0
 */
@Serializable
data class AuditCheckpoint(
  val headSeq: Long,
  val headHash: String,
  val eventCount: Long,
  val chainGeneration: Int,
  val prunedThroughSeq: Long,
  val prunedThroughHash: String,
  val linkSeq: Long?,
  val linkHash: String?,
  val signature: String?,
) {
  /**
   * The exact string the device key signs: `kioskops-audit-checkpoint-v1|` followed by headSeq,
   * headHash, eventCount, chainGeneration, prunedThroughSeq, prunedThroughHash, linkSeq and
   * linkHash, each as `"<utf8 byte length>:<value>"`. A null linkSeq or linkHash is written as `-`,
   * which no length prefix starts with, so null and empty never alias. [signature] is not part of it.
   */
  fun signingPayload(): String =
    "kioskops-audit-checkpoint-v1|" + AuditChainCore.lengthPrefixed(
      headSeq.toString(),
      headHash,
      eventCount.toString(),
      chainGeneration.toString(),
      prunedThroughSeq.toString(),
      prunedThroughHash,
    ) + nullable(linkSeq?.toString()) + nullable(linkHash)

  private fun nullable(value: String?): String = value?.let { AuditChainCore.lengthPrefixed(it) } ?: "-"
}

/** Outcome of comparing a device checkpoint with the one a server pinned earlier. */
internal sealed interface PinResult {
  /** The current chain extends the pinned one. */
  data object Consistent : PinResult

  /** The chain lost events the pin covered: rollback or truncation. */
  data class Regressed(val reason: String) : PinResult

  /** The chain no longer contains the pinned head: rewrite or restart. */
  data class Forked(val reason: String) : PinResult
}
