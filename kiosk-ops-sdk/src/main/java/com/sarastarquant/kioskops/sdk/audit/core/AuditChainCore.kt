/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit.core

import com.sarastarquant.kioskops.sdk.audit.DeviceAttestationProvider
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
    AuditHashFormat.V2 -> lengthPrefixed(id, ts.toString(), name, fieldsJson, prevHash)
  }

  /** Concatenates each value as `"<utf8 byte length>:<value>"`, so no value can alias a neighbour. */
  fun lengthPrefixed(vararg values: String): String = buildString {
    for (value in values) append(value.toByteArray(Charsets.UTF_8).size).append(':').append(value)
  }

  fun hash(content: String): String = Hashing.sha256Base64Url(content)

  /**
   * The server rule for a device checkpoint, given the [pin] the server holds for that device
   * (received at [pinReceivedAtMs]) and the device's audit retention window. The first checkpoint
   * from a device has no pin: the server pins it on first contact and does not call this.
   *
   * - R1 Regressed: chainGeneration changed, or headSeq or eventCount is below the pin.
   * - R3 prune reached the pin (`current.prunedThroughSeq` advanced past `pin.prunedThroughSeq`
   *   and is at or above `pin.headSeq`): Forked if less than [retentionWindowMs] has passed since
   *   the pin arrived, since retention cannot have removed the pinned head yet. Otherwise, if the
   *   link points at the prune point it must carry the prune hash; any other link re-baselines the
   *   pin on trust (the pinned row is gone, nothing can be compared).
   * - R2 Forked: the link (linkSeq, linkHash) matches neither (pin.headSeq, pin.headHash) nor, when
   *   [previousPin] is given, (previousPin.headSeq, previousPin.headHash). The previous pin covers a
   *   response lost after the server pinned; a link to it goes through R3 against the previous pin
   *   (received at [previousPinReceivedAtMs]). A null link while a pin exists is Forked.
   * - Consistent otherwise.
   *
   * A prune record forged below the pinned head the checkpoint links to passes: it can erase older
   * acknowledged history early, while that pinned head still binds everything after it.
   */
  fun compareToPin(
    pin: AuditCheckpoint,
    pinReceivedAtMs: Long,
    current: AuditCheckpoint,
    nowMs: Long,
    retentionWindowMs: Long,
    previousPin: AuditCheckpoint? = null,
    previousPinReceivedAtMs: Long = pinReceivedAtMs,
  ): PinResult {
    return regression(pin, current) ?: pruneRule(pin, nowMs - pinReceivedAtMs, current, retentionWindowMs) ?: when {
      linksTo(current, pin) -> PinResult.Consistent
      previousPin != null && linksTo(current, previousPin) ->
        pruneRule(previousPin, nowMs - previousPinReceivedAtMs, current, retentionWindowMs) ?: PinResult.Consistent
      else -> PinResult.Forked("link (${current.linkSeq}) does not match the pinned head ${pin.headSeq}")
    }
  }

  /** R3 against [anchor]; null when the prune point did not advance to the anchor's head. */
  private fun pruneRule(
    anchor: AuditCheckpoint,
    sinceAnchorMs: Long,
    current: AuditCheckpoint,
    retentionWindowMs: Long,
  ): PinResult? = when {
    current.prunedThroughSeq <= anchor.prunedThroughSeq || current.prunedThroughSeq < anchor.headSeq -> null
    sinceAnchorMs < retentionWindowMs ->
      PinResult.Forked("prune point ${current.prunedThroughSeq} reached the pin inside the retention window")
    current.linkSeq == current.prunedThroughSeq && current.linkHash != current.prunedThroughHash ->
      PinResult.Forked("link at the prune point does not carry the prune hash")
    else -> PinResult.Consistent
  }

  private fun linksTo(current: AuditCheckpoint, pinned: AuditCheckpoint): Boolean =
    current.linkSeq == pinned.headSeq && current.linkHash == pinned.headHash

  private fun regression(pin: AuditCheckpoint, current: AuditCheckpoint): PinResult.Regressed? = when {
    current.chainGeneration != pin.chainGeneration ->
      PinResult.Regressed("chain generation ${current.chainGeneration} differs from pinned ${pin.chainGeneration}")
    current.headSeq < pin.headSeq -> PinResult.Regressed("headSeq ${current.headSeq} is below pinned ${pin.headSeq}")
    current.eventCount < pin.eventCount ->
      PinResult.Regressed("eventCount ${current.eventCount} is below pinned ${pin.eventCount}")
    else -> null
  }

  /** True when [checkpoint] carries a signature that [provider] accepts over its signing payload. */
  fun verifyCheckpointSignature(checkpoint: AuditCheckpoint, provider: DeviceAttestationProvider): Boolean =
    checkpoint.signature?.let { provider.verifySignature(checkpoint.signingPayload(), it) } ?: false

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
