/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ErasedRunsTest {

  private fun link(seq: Long, prevHash: String): ChainLink {
    val id = "id-$seq"
    val ts = 1_000L + seq
    val name = "event_$seq"
    val content = AuditChainCore.content(AuditHashFormat.V2, id, ts, name, "{}", prevHash)
    return ChainLink(seq, id, ts, name, "{}", prevHash, AuditChainCore.hash(content), AuditHashFormat.V2.version)
  }

  private val full: List<ChainLink> = buildList {
    var prev = "GENESIS"
    for (seq in 1L..8L) add(link(seq, prev).also { prev = it.hash })
  }

  private fun row(seq: Long) = full[(seq - 1).toInt()]

  private fun run(from: Long, to: Long) = ErasedRun(from, to, row(from).prevHash, row(to).hash)

  private fun without(vararg erased: LongRange): List<ChainLink> =
    full.filter { l -> erased.none { l.seq in it } }

  @Test
  fun `middle run bridges`() {
    val result = AuditChainCore.verifyPage(without(3L..5L), "GENESIS", listOf(run(3, 5)))
    assertThat(result).isEqualTo(PageResult.Ok(row(8).hash, 8))
  }

  @Test
  fun `run at the head bridges from the seed`() {
    val result = AuditChainCore.verifyPage(without(1L..2L), "GENESIS", listOf(run(1, 2)))
    assertThat(result).isEqualTo(PageResult.Ok(row(8).hash, 8))
  }

  @Test
  fun `run at the tail needs no bridge`() {
    val result = AuditChainCore.verifyPage(without(7L..8L), "GENESIS", listOf(run(7, 8)))
    assertThat(result).isEqualTo(PageResult.Ok(row(6).hash, 6))
  }

  @Test
  fun `two touching runs tile the gap`() {
    val gaps = listOf(run(3, 3), run(4, 5))
    val result = AuditChainCore.verifyPage(without(3L..5L), "GENESIS", gaps)
    assertThat(result).isEqualTo(PageResult.Ok(row(8).hash, 8))
  }

  @Test
  fun `two separate runs bridge two gaps`() {
    val gaps = listOf(run(2, 2), run(5, 6))
    val result = AuditChainCore.verifyPage(without(2L..2L, 5L..6L), "GENESIS", gaps)
    assertThat(result).isEqualTo(PageResult.Ok(row(8).hash, 8))
  }

  @Test
  fun `gap without a run is broken at the first row after it`() {
    val result = AuditChainCore.verifyPage(without(3L..5L), "GENESIS")
    assertThat(result).isInstanceOf(PageResult.Broken::class.java)
    assertThat((result as PageResult.Broken).seq).isEqualTo(6)
  }

  @Test
  fun `run with a wrong end hash is broken`() {
    val bad = run(3, 5).copy(endHash = row(4).hash)
    val result = AuditChainCore.verifyPage(without(3L..5L), "GENESIS", listOf(bad))
    assertThat((result as PageResult.Broken).seq).isEqualTo(6)
  }

  @Test
  fun `run with a wrong start hash is broken`() {
    val bad = run(3, 5).copy(startPrev = row(5).prevHash)
    val result = AuditChainCore.verifyPage(without(3L..5L), "GENESIS", listOf(bad))
    assertThat((result as PageResult.Broken).seq).isEqualTo(6)
  }

  @Test
  fun `runs that leave a seq hole are broken`() {
    val result = AuditChainCore.verifyPage(without(3L..5L), "GENESIS", listOf(run(3, 4)))
    assertThat((result as PageResult.Broken).seq).isEqualTo(6)
  }

  @Test
  fun `run that overlaps the surviving row is broken`() {
    val result = AuditChainCore.verifyPage(without(3L..4L), "GENESIS", listOf(run(3, 5)))
    assertThat((result as PageResult.Broken).seq).isEqualTo(5)
  }

  @Test
  fun `a bridged link still checks its own hash`() {
    val tampered = without(3L..5L).map { if (it.seq == 6L) it.copy(name = "forged") else it }
    val result = AuditChainCore.verifyPage(tampered, "GENESIS", listOf(run(3, 5)))
    assertThat((result as PageResult.Broken).seq).isEqualTo(6)
  }

  @Test
  fun `runs round trip through the field value`() {
    val runs = listOf(run(3, 3), run(4, 5).copy(ackedSeq = 5, ackedHash = row(5).hash))
    assertThat(parseErasedRuns(runs.toFieldValue())).isEqualTo(runs)
  }

  @Test
  fun `malformed or inverted runs do not parse`() {
    assertThat(parseErasedRuns("{")).isNull()
    assertThat(parseErasedRuns("[]")).isEmpty()
    assertThat(parseErasedRuns(listOf(ErasedRun(5, 3, "a", "b")).toFieldValue())).isNull()
  }
}
