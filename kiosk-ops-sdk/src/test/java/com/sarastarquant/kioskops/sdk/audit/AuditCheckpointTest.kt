/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.audit.core.AuditChainCore
import com.sarastarquant.kioskops.sdk.audit.core.AuditCheckpoint
import com.sarastarquant.kioskops.sdk.audit.core.AuditHashFormat
import com.sarastarquant.kioskops.sdk.audit.core.PinResult
import com.sarastarquant.kioskops.sdk.audit.db.AuditDatabase
import com.sarastarquant.kioskops.sdk.compliance.RetentionPolicy
import com.sarastarquant.kioskops.sdk.util.Clock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Base64

/** Software ECDSA P-256 key standing in for the Android Keystore, which Robolectric lacks. */
internal class SoftwareAttestationProvider(
  private val keyPair: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair(),
  private val failSigning: Boolean = false,
) : DeviceAttestationProvider {
  override fun signAuditEntry(payload: String): String? {
    if (failSigning) return null
    val sig = Signature.getInstance("SHA256withECDSA")
    sig.initSign(keyPair.private)
    sig.update(payload.toByteArray(Charsets.UTF_8))
    return Base64.getEncoder().encodeToString(sig.sign())
  }

  override fun verifySignature(payload: String, signature: String): Boolean {
    val sig = Signature.getInstance("SHA256withECDSA")
    sig.initVerify(keyPair.public)
    sig.update(payload.toByteArray(Charsets.UTF_8))
    return sig.verify(Base64.getDecoder().decode(signature))
  }

  override fun getAttestationChain(): List<X509Certificate>? = null
  override val isHardwareBacked: Boolean = false
  override fun getAttestationBlob(): ByteArray? = null
}

@RunWith(RobolectricTestRunner::class)
class AuditCheckpointTest {

  private lateinit var ctx: Context
  private lateinit var db: AuditDatabase

  private var nowMs = 1_700_000_000_000L
  private val clock = object : Clock {
    override fun nowMs(): Long = nowMs
  }
  private val dayMs = 24 * 60 * 60 * 1000L
  private val provider = SoftwareAttestationProvider()

  @Before
  fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(ctx, AuditDatabase::class.java).allowMainThreadQueries().build()
    AuditDatabase.setInstance(db)
  }

  @After
  fun tearDown() {
    db.close()
    AuditDatabase.setInstance(null)
  }

  private fun trail(
    retention: RetentionPolicy = RetentionPolicy.maximalistDefaults(),
    attestation: DeviceAttestationProvider? = provider,
  ) = PersistentAuditTrail(
    context = ctx,
    retentionProvider = { retention },
    clock = clock,
    attestationProvider = attestation?.let { p -> { p } },
  )

  private suspend fun recordFive(trail: PersistentAuditTrail) {
    for (i in 1..5) {
      trail.record("event_$i")
      nowMs += 1000
    }
  }

  private suspend fun hashAtSeq(seq: Long): String? =
    db.auditDao().getEventsPageBySeq(seq - 1, 1).firstOrNull { it.seq == seq }?.hash

  private fun sql(statement: String) = db.openHelper.writableDatabase.execSQL(statement)

  @Test
  fun `checkpoint signature verifies with the device key`() = runTest {
    val trail = trail()
    recordFive(trail)

    val checkpoint = trail.createCheckpoint(sign = true)

    assertThat(checkpoint.headSeq).isEqualTo(5L)
    assertThat(checkpoint.headHash).isEqualTo(hashAtSeq(5))
    assertThat(checkpoint.eventCount).isEqualTo(5L)
    assertThat(checkpoint.signature).isNotNull()
    assertThat(AuditChainCore.verifyCheckpointSignature(checkpoint, provider)).isTrue()
    assertThat(AuditChainCore.verifyCheckpointSignature(checkpoint.copy(headSeq = 4), provider)).isFalse()
    assertThat(AuditChainCore.verifyCheckpointSignature(checkpoint.copy(linkSeq = 1), provider)).isFalse()
    assertThat(AuditChainCore.verifyCheckpointSignature(checkpoint.copy(signature = null), provider)).isFalse()
    assertThat(trail.getEventsInRange(0, Long.MAX_VALUE).map { it.name }).doesNotContain("checkpoint_unsigned")
  }

  @Test
  fun `unsigned checkpoint records checkpoint_unsigned event`() = runTest {
    for ((attestation, reason) in listOf(
      null to "no_attestation_provider",
      SoftwareAttestationProvider(failSigning = true) to "signing_failed",
    )) {
      val trail = trail(attestation = attestation)
      trail.record("first")
      val before = db.auditDao().countEvents()

      val checkpoint = trail.createCheckpoint(sign = true)

      assertThat(checkpoint.signature).isNull()
      val added = db.auditDao().getEventsPageBySeq(0, 100).drop(before.toInt())
      assertThat(added.map { it.name }).containsExactly("checkpoint_unsigned")
      assertThat(added.single().fieldsJson).contains(reason)
    }
  }

  @Test
  fun `unrequested signature records no event`() = runTest {
    val trail = trail(attestation = null)
    trail.record("first")
    assertThat(trail.createCheckpoint(sign = false).signature).isNull()
    assertThat(db.auditDao().countEvents()).isEqualTo(1L)
  }

  /** Server side: pins every accepted checkpoint and checks each new one against the last pin. */
  private inner class FakeServer(private val retentionWindowMs: Long = 30 * dayMs) {
    var pin: AuditCheckpoint? = null
    var pinReceivedAtMs = 0L

    fun receive(current: AuditCheckpoint): PinResult {
      val result = pin?.let { AuditChainCore.compareToPin(it, pinReceivedAtMs, current, nowMs, retentionWindowMs) }
        ?: PinResult.Consistent // First contact: the server pins.
      if (result == PinResult.Consistent) {
        pin = current
        pinReceivedAtMs = nowMs
      }
      return result
    }
  }

  /** One sync: build a checkpoint, let the server judge it, acknowledge it on acceptance. */
  private suspend fun sync(trail: PersistentAuditTrail, server: FakeServer): PinResult {
    val checkpoint = trail.createCheckpoint(sign = true)
    val result = server.receive(checkpoint)
    if (result == PinResult.Consistent) trail.acknowledgeCheckpoint(checkpoint)
    return result
  }

  private fun checkpoint(
    headSeq: Long = 5,
    eventCount: Long = headSeq,
    generation: Int = 1,
    prunedThroughSeq: Long = 0,
    prunedThroughHash: String = "GENESIS",
    linkSeq: Long? = null,
    linkHash: String? = null,
  ) = AuditCheckpoint(headSeq, "h$headSeq", eventCount, generation, prunedThroughSeq, prunedThroughHash, linkSeq, linkHash, null)

  @Test
  fun `legitimate sync sequence stays consistent`() = runTest {
    val trail = trail()
    val server = FakeServer()
    recordFive(trail)
    assertThat(sync(trail, server)).isEqualTo(PinResult.Consistent)
    trail.record("event_6")
    val second = trail.createCheckpoint(sign = true)
    assertThat(second.linkSeq).isEqualTo(5L)
    assertThat(second.linkHash).isEqualTo(hashAtSeq(5))
    assertThat(sync(trail, server)).isEqualTo(PinResult.Consistent)
    trail.record("event_7")
    assertThat(sync(trail, server)).isEqualTo(PinResult.Consistent)
  }

  @Test
  fun `checkpoint before any ack has no link`() = runTest {
    val trail = trail()
    recordFive(trail)
    val checkpoint = trail.createCheckpoint(sign = true)
    assertThat(checkpoint.linkSeq).isNull()
    assertThat(checkpoint.linkHash).isNull()
  }

  @Test
  fun `checkpoint after retention records prune point and verification is valid`() = runTest {
    val trail = trail(RetentionPolicy.maximalistDefaults().copy(retainAuditDays = 1))
    val server = FakeServer(retentionWindowMs = dayMs)
    trail.record("old_1")
    nowMs += 1000
    trail.record("old_2")
    val prunedHash = hashAtSeq(2)
    assertThat(sync(trail, server)).isEqualTo(PinResult.Consistent)
    nowMs += 2 * dayMs
    trail.record("new_1")

    trail.applyRetention()

    val checkpoint = trail.createCheckpoint(sign = true)
    assertThat(checkpoint.prunedThroughSeq).isEqualTo(2L)
    assertThat(checkpoint.prunedThroughHash).isEqualTo(prunedHash)
    assertThat(checkpoint.headSeq).isEqualTo(3L)
    // The acknowledged row was pruned exactly at the link: the link carries the prune hash.
    assertThat(checkpoint.linkSeq).isEqualTo(2L)
    assertThat(checkpoint.linkHash).isEqualTo(prunedHash)
    assertThat(trail.verifyChainIntegrity()).isInstanceOf(ChainVerificationResult.Valid::class.java)
    assertThat(server.receive(checkpoint)).isEqualTo(PinResult.Consistent)
  }

  @Test
  fun `prune past the pin is Forked inside the retention window and Consistent after it`() = runTest {
    val trail = trail(RetentionPolicy.maximalistDefaults().copy(retainAuditDays = 1))
    val server = FakeServer(retentionWindowMs = 10 * dayMs)
    trail.record("old_1")
    trail.record("old_2")
    assertThat(sync(trail, server)).isEqualTo(PinResult.Consistent)
    trail.record("old_3")
    nowMs += 2 * dayMs
    trail.record("new_1")
    trail.applyRetention()

    val checkpoint = trail.createCheckpoint(sign = true)
    assertThat(checkpoint.prunedThroughSeq).isEqualTo(3L)
    // Two days after the pin, a 10-day retention cannot have removed it: a forged prune record.
    assertThat(server.receive(checkpoint)).isInstanceOf(PinResult.Forked::class.java)

    nowMs += 10 * dayMs
    assertThat(server.receive(checkpoint)).isEqualTo(PinResult.Consistent)
  }

  @Test
  fun `deleting head events without prune record is not valid`() = runTest {
    val trail = trail()
    recordFive(trail)

    sql("DELETE FROM audit_events WHERE seq <= 2")

    val result = trail.verifyChainIntegrity()
    assertThat(result).isInstanceOf(ChainVerificationResult.Broken::class.java)
    assertThat((result as ChainVerificationResult.Broken).expectedPrevHash).isEqualTo("GENESIS")
  }

  @Test
  fun `deleting tail events yields Regressed against pin`() = runTest {
    val trail = trail()
    val server = FakeServer()
    recordFive(trail)
    assertThat(sync(trail, server)).isEqualTo(PinResult.Consistent)

    sql("DELETE FROM audit_events WHERE seq >= 4")
    // A careful attacker rolls the cached chain state back too.
    sql("UPDATE audit_chain_state SET eventCount = 3, lastHash = '${hashAtSeq(3)}', lastAckedSeq = 0")

    assertThat(trail.verifyChainIntegrity()).isInstanceOf(ChainVerificationResult.Valid::class.java)
    assertThat(sync(trail, server)).isInstanceOf(PinResult.Regressed::class.java)
  }

  @Test
  fun `tail delete then regrowth past the pin is Forked`() = runTest {
    val trail = trail()
    val server = FakeServer()
    recordFive(trail)
    assertThat(sync(trail, server)).isEqualTo(PinResult.Consistent)

    sql("DELETE FROM audit_events WHERE seq >= 4")
    sql("UPDATE audit_chain_state SET eventCount = 3, lastHash = '${hashAtSeq(3)}'")
    repeat(3) { trail.record("regrown_$it") }

    val current = trail.createCheckpoint(sign = true)
    assertThat(current.headSeq).isEqualTo(6L)
    assertThat(trail.verifyChainIntegrity()).isInstanceOf(ChainVerificationResult.Valid::class.java)
    assertThat(server.receive(current)).isInstanceOf(PinResult.Forked::class.java)
  }

  @Test
  fun `full chain recompute yields Forked against pin`() = runTest {
    val trail = trail()
    val server = FakeServer()
    recordFive(trail)
    assertThat(sync(trail, server)).isEqualTo(PinResult.Consistent)
    rewriteChain()

    assertThat(trail.verifyChainIntegrity()).isInstanceOf(ChainVerificationResult.Valid::class.java)
    assertThat(sync(trail, server)).isInstanceOf(PinResult.Forked::class.java)
  }

  @Test
  fun `full chain recompute plus one event is Forked`() = runTest {
    val trail = trail()
    val server = FakeServer()
    recordFive(trail)
    assertThat(sync(trail, server)).isEqualTo(PinResult.Consistent)
    rewriteChain()
    trail.record("event_6")

    assertThat(trail.verifyChainIntegrity()).isInstanceOf(ChainVerificationResult.Valid::class.java)
    assertThat(sync(trail, server)).isInstanceOf(PinResult.Forked::class.java)
  }

  /** Rewrite every row with new content and recomputed hashes; same count, same seqs. */
  private suspend fun rewriteChain() {
    var prev = "GENESIS"
    for (row in db.auditDao().getEventsPageBySeq(0, 100)) {
      val fields = "{\"rewritten\":\"true\"}"
      val hash = AuditChainCore.hash(AuditChainCore.content(AuditHashFormat.V2, row.id, row.ts, row.name, fields, prev))
      sql("UPDATE audit_events SET fieldsJson = '$fields', prevHash = '$prev', hash = '$hash' WHERE id = '${row.id}'")
      prev = hash
    }
    sql("UPDATE audit_chain_state SET lastHash = '$prev'")
  }

  @Test
  fun `retention after a backward clock step never deletes early`() = runTest {
    val trail = trail(RetentionPolicy.maximalistDefaults().copy(retainAuditDays = 1))
    val start = nowMs
    trail.record("old")
    nowMs = start + 3 * dayMs
    trail.record("future")
    nowMs = start + 1000
    trail.record("old_after_step_back")
    nowMs = start + 2 * dayMs

    trail.applyRetention()

    val names = db.auditDao().getEventsPageBySeq(0, 100).map { it.name }
    assertThat(names).containsExactly("future", "old_after_step_back").inOrder()
    assertThat(db.auditDao().getChainState()!!.prunedThroughSeq).isEqualTo(1L)
    assertThat(trail.verifyChainIntegrity()).isInstanceOf(ChainVerificationResult.Valid::class.java)
  }

  @Test
  fun `pin rules on synthetic checkpoints`() {
    val pin = checkpoint(headSeq = 5)
    fun judge(current: AuditCheckpoint, now: Long = 0) = AuditChainCore.compareToPin(pin, 0, current, now, dayMs)

    assertThat(judge(checkpoint(headSeq = 6, linkSeq = 5, linkHash = "h5"))).isEqualTo(PinResult.Consistent)
    assertThat(judge(checkpoint(headSeq = 6, generation = 2, linkSeq = 5, linkHash = "h5")))
      .isInstanceOf(PinResult.Regressed::class.java)
    assertThat(judge(checkpoint(headSeq = 6, eventCount = 4, linkSeq = 5, linkHash = "h5")))
      .isInstanceOf(PinResult.Regressed::class.java)
    // A pin exists, so a missing link is a fork.
    assertThat(judge(checkpoint(headSeq = 6))).isInstanceOf(PinResult.Forked::class.java)
    assertThat(judge(checkpoint(headSeq = 6, linkSeq = 5))).isInstanceOf(PinResult.Forked::class.java)
    assertThat(judge(checkpoint(headSeq = 6, linkSeq = 4, linkHash = "h5"))).isInstanceOf(PinResult.Forked::class.java)
    // Prune past the pin, after the window: a link at the prune point must carry the prune hash.
    val pruned = checkpoint(headSeq = 9, prunedThroughSeq = 7, prunedThroughHash = "p7", linkSeq = 7, linkHash = "x")
    assertThat(judge(pruned, now = dayMs)).isInstanceOf(PinResult.Forked::class.java)
    assertThat(judge(pruned.copy(linkHash = "p7"), now = dayMs)).isEqualTo(PinResult.Consistent)
    // Any other link re-baselines on trust.
    assertThat(judge(pruned.copy(linkSeq = 5, linkHash = null), now = dayMs)).isEqualTo(PinResult.Consistent)
  }

  @Test
  fun `signing payload is deterministic and length-prefixed`() {
    val checkpoint = AuditCheckpoint(12, "ab", 13, 1, 0, "GENESIS", 7, "cd", "ignored")
    assertThat(checkpoint.signingPayload())
      .isEqualTo("kioskops-audit-checkpoint-v1|2:122:ab2:131:11:07:GENESIS1:72:cd")
    assertThat(checkpoint.copy(signature = null).signingPayload()).isEqualTo(checkpoint.signingPayload())
    assertThat(checkpoint.copy(linkSeq = null, linkHash = null).signingPayload())
      .isEqualTo("kioskops-audit-checkpoint-v1|2:122:ab2:131:11:07:GENESIS--")
    // Null and empty link hash do not alias.
    assertThat(checkpoint.copy(linkHash = null).signingPayload())
      .isNotEqualTo(checkpoint.copy(linkHash = "").signingPayload())

    // These two join to the same "|"-delimited string; the length prefixes keep them apart.
    val a = AuditCheckpoint(1, "h|2", 3, 4, 0, "p", null, null, null)
    val b = AuditCheckpoint(1, "h", 2, 3, 4, "0|p", null, null, null)
    val naive = { c: AuditCheckpoint ->
      listOf(c.headSeq, c.headHash, c.eventCount, c.chainGeneration, c.prunedThroughSeq, c.prunedThroughHash)
        .joinToString("|")
    }
    assertThat(naive(a)).isEqualTo(naive(b))
    assertThat(a.signingPayload()).isNotEqualTo(b.signingPayload())
  }
}
