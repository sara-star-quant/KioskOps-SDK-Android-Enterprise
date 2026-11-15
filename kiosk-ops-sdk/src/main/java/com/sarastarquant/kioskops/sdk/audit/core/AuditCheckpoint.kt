/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit.core

import kotlinx.serialization.Serializable

/**
 * A signed snapshot of the audit chain head, sent with sync batches so a server can pin it.
 *
 * Once a server pins a checkpoint, a later checkpoint from the same device that rolls back,
 * truncates or rewrites the chain fails [AuditChainCore.compareToPin].
 *
 * @property headSeq Seq of the newest stored event; [prunedThroughSeq] when every event was pruned.
 * @property headHash Hash of the event at [headSeq]; [prunedThroughHash] when every event was pruned.
 * @property eventCount Events ever recorded in this chain, pruned ones included.
 * @property chainGeneration Chain generation of the device audit chain.
 * @property prunedThroughSeq Highest seq removed by retention; 0 when nothing was pruned.
 * @property prunedThroughHash Hash of the event at [prunedThroughSeq]; `GENESIS` when nothing was pruned.
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
  val signature: String?,
) {
  /**
   * The exact string the device key signs: `kioskops-audit-checkpoint-v1|` followed by headSeq,
   * headHash, eventCount, chainGeneration, prunedThroughSeq and prunedThroughHash, each as
   * `"<utf8 byte length>:<value>"`. [signature] is not part of it.
   */
  fun signingPayload(): String =
    "kioskops-audit-checkpoint-v1|" + AuditChainCore.lengthPrefixed(
      headSeq.toString(),
      headHash,
      eventCount.toString(),
      chainGeneration.toString(),
      prunedThroughSeq.toString(),
      prunedThroughHash,
    )
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
