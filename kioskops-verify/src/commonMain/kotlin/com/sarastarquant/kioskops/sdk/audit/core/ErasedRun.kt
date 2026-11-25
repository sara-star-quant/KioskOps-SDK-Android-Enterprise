/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A contiguous run of chain rows removed by a user erasure, described by its boundary hashes only.
 *
 * The erase record row lists every run it removed. A verifier bridges the resulting link mismatch
 * only when the runs, taken in order, connect the hash of the surviving row before the gap to the
 * prevHash of the surviving row after it and cover every seq in between.
 *
 * @property fromSeq First removed seq.
 * @property toSeq Last removed seq.
 * @property startPrev prevHash of the row at [fromSeq], which is the hash of the row before the run.
 * @property endHash Hash of the row at [toSeq].
 * @property ackedSeq headSeq of the last server-acknowledged checkpoint when it fell inside this run.
 * @property ackedHash Hash of the row at [ackedSeq].
 */
@Serializable
public data class ErasedRun(
  val fromSeq: Long,
  val toSeq: Long,
  val startPrev: String,
  val endHash: String,
  val ackedSeq: Long? = null,
  val ackedHash: String? = null,
)

private val runsJson = Json { ignoreUnknownKeys = true }

/** The value stored under the `runs` field of an erase record row. */
public fun List<ErasedRun>.toFieldValue(): String =
  runsJson.encodeToString(ListSerializer(ErasedRun.serializer()), this)

/** Parses an erase record's `runs` field; null when it is malformed or a run is inverted. */
public fun parseErasedRuns(fieldValue: String): List<ErasedRun>? {
  val runs = runCatching {
    runsJson.decodeFromString(ListSerializer(ErasedRun.serializer()), fieldValue)
  }.getOrNull() ?: return null
  return runs.takeIf { list -> list.all { it.fromSeq <= it.toSeq } }
}
