/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit

import com.sarastarquant.kioskops.sdk.audit.core.ErasedRun
import com.sarastarquant.kioskops.sdk.audit.db.AuditEventEntity

/** A signing key announced by a marker row at [seq]. */
internal class KeyMarker(val seq: Long, val publicKey: ByteArray)

/** Contiguous seq groups of [rows], with the acknowledged checkpoint hash when it falls inside one. */
internal fun erasedRunsOf(rows: List<AuditEventEntity>, ackedSeq: Long): List<ErasedRun> {
  val runs = mutableListOf<ErasedRun>()
  var group = mutableListOf<AuditEventEntity>()
  fun flush() {
    if (group.isEmpty()) return
    val acked = group.firstOrNull { it.seq == ackedSeq && ackedSeq > 0 }
    runs += ErasedRun(
      fromSeq = group.first().seq,
      toSeq = group.last().seq,
      startPrev = group.first().prevHash,
      endHash = group.last().hash,
      ackedSeq = acked?.seq,
      ackedHash = acked?.hash,
    )
    group = mutableListOf()
  }
  for (row in rows.sortedBy { it.seq }) {
    if (group.isNotEmpty() && row.seq != group.last().seq + 1) flush()
    group += row
  }
  flush()
  return runs
}

/**
 * A row is checked against the newest key marker at or below its seq; rows before the first marker
 * use the first marker's key (the key in place when markers began). With no markers, or when the
 * provider cannot verify with an explicit key and the row belongs to the newest key, the provider's
 * current key decides, as before key markers existed.
 */
internal fun signatureHolds(
  provider: DeviceAttestationProvider,
  markers: List<KeyMarker>,
  seq: Long,
  content: String,
  signature: String,
): Boolean {
  val marker = markers.lastOrNull { it.seq <= seq } ?: markers.firstOrNull()
  val explicit = marker != null && provider.verifyWithPublicKey(marker.publicKey, content, signature)
  val usesCurrentKey = marker == null || marker === markers.last()
  return explicit || (usesCurrentKey && provider.verifySignature(content, signature))
}
