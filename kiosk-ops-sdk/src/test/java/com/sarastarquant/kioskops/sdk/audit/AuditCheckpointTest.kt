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
    assertThat(provider.verifySignature(checkpoint.signingPayload(), checkpoint.signature!!)).isTrue()
    val tampered = checkpoint.copy(headSeq = 4)
    assertThat(provider.verifySignature(tampered.signingPayload(), checkpoint.signature!!)).isFalse()
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

  @Test
  fun `checkpoint after retention records prune point and verification is valid`() = runTest {
    val trail = trail(RetentionPolicy.maximalistDefaults().copy(retainAuditDays = 1))
    trail.record("old_1")
    nowMs += 1000
    trail.record("old_2")
    val prunedHash = hashAtSeq(2)
    val pin = trail.createCheckpoint(sign = true)
    nowMs += 2 * dayMs
    trail.record("new_1")

    trail.applyRetention()

    val checkpoint = trail.createCheckpoint(sign = true)
    assertThat(checkpoint.prunedThroughSeq).isEqualTo(2L)
    assertThat(checkpoint.prunedThroughHash).isEqualTo(prunedHash)
    assertThat(checkpoint.headSeq).isEqualTo(3L)
    assertThat(trail.verifyChainIntegrity()).isInstanceOf(ChainVerificationResult.Valid::class.java)
    // The pinned head was pruned legitimately: consistent, not a truncation.
    assertThat(AuditChainCore.compareToPin(pin, checkpoint, hashAtSeq(pin.headSeq))).isEqualTo(PinResult.Consistent)
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
    recordFive(trail)
    val pin = trail.createCheckpoint(sign = true)

    sql("DELETE FROM audit_events WHERE seq >= 4")
    // A careful attacker rolls the cached chain state back too.
    sql("UPDATE audit_chain_state SET eventCount = 3, lastHash = '${hashAtSeq(3)}'")

    val current = trail.createCheckpoint(sign = true)
    assertThat(trail.verifyChainIntegrity()).isInstanceOf(ChainVerificationResult.Valid::class.java)
    assertThat(AuditChainCore.compareToPin(pin, current, hashAtSeq(pin.headSeq)))
      .isInstanceOf(PinResult.Regressed::class.java)
  }

  @Test
  fun `full chain recompute yields Forked against pin`() = runTest {
    val trail = trail()
    recordFive(trail)
    val pin = trail.createCheckpoint(sign = true)

    // Rewrite every row with new content and recomputed hashes; same count, same seqs.
    var prev = "GENESIS"
    for (row in db.auditDao().getEventsPageBySeq(0, 100)) {
      val fields = "{\"rewritten\":\"true\"}"
      val hash = AuditChainCore.hash(AuditChainCore.content(AuditHashFormat.V2, row.id, row.ts, row.name, fields, prev))
      sql("UPDATE audit_events SET fieldsJson = '$fields', prevHash = '$prev', hash = '$hash' WHERE id = '${row.id}'")
      prev = hash
    }
    sql("UPDATE audit_chain_state SET lastHash = '$prev'")

    val current = trail.createCheckpoint(sign = true)
    assertThat(trail.verifyChainIntegrity()).isInstanceOf(ChainVerificationResult.Valid::class.java)
    assertThat(current.headSeq).isEqualTo(pin.headSeq)
    assertThat(AuditChainCore.compareToPin(pin, current, hashAtSeq(pin.headSeq)))
      .isInstanceOf(PinResult.Forked::class.java)
  }

  @Test
  fun `appending events stays consistent with pin`() = runTest {
    val trail = trail()
    recordFive(trail)
    val pin = trail.createCheckpoint(sign = true)
    trail.record("event_6")

    val current = trail.createCheckpoint(sign = true)
    assertThat(AuditChainCore.compareToPin(pin, current, hashAtSeq(pin.headSeq))).isEqualTo(PinResult.Consistent)
  }

  @Test
  fun `missing pinned row without prune record is Regressed`() {
    val pin = AuditCheckpoint(5, "h5", 5, 1, 0, "GENESIS", null)
    val current = AuditCheckpoint(6, "h6", 6, 1, 0, "GENESIS", null)
    assertThat(AuditChainCore.compareToPin(pin, current, null)).isInstanceOf(PinResult.Regressed::class.java)
    assertThat(AuditChainCore.compareToPin(pin, current.copy(prunedThroughSeq = 5, prunedThroughHash = "other"), null))
      .isInstanceOf(PinResult.Forked::class.java)
    assertThat(AuditChainCore.compareToPin(pin, current.copy(chainGeneration = 2), "h5"))
      .isInstanceOf(PinResult.Forked::class.java)
  }

  @Test
  fun `signing payload is deterministic and length-prefixed`() {
    val checkpoint = AuditCheckpoint(12, "ab", 13, 1, 0, "GENESIS", "ignored")
    assertThat(checkpoint.signingPayload())
      .isEqualTo("kioskops-audit-checkpoint-v1|2:122:ab2:131:11:07:GENESIS")
    assertThat(checkpoint.copy(signature = null).signingPayload()).isEqualTo(checkpoint.signingPayload())

    // These two join to the same "|"-delimited string; the length prefixes keep them apart.
    val a = AuditCheckpoint(1, "h|2", 3, 4, 0, "p", null)
    val b = AuditCheckpoint(1, "h", 2, 3, 4, "0|p", null)
    val naive = { c: AuditCheckpoint ->
      listOf(c.headSeq, c.headHash, c.eventCount, c.chainGeneration, c.prunedThroughSeq, c.prunedThroughHash)
        .joinToString("|")
    }
    assertThat(naive(a)).isEqualTo(naive(b))
    assertThat(a.signingPayload()).isNotEqualTo(b.signingPayload())
  }
}
