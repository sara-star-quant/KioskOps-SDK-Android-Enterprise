/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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
import java.security.spec.ECGenParameterSpec
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
class PersistentAuditTrailKeyChangeTest {

  private class SwappableProvider : DeviceAttestationProvider {
    var key: KeyPair = newKey()
    var namesKey = true
    var failSigning = false

    fun swapKey() {
      key = newKey()
    }

    override fun signAuditEntry(payload: String): String? {
      if (failSigning) return null
      return Signature.getInstance("SHA256withECDSA").run {
        initSign(key.private)
        update(payload.toByteArray(Charsets.UTF_8))
        Base64.getEncoder().encodeToString(sign())
      }
    }

    override fun verifySignature(payload: String, signature: String) =
      verifyEcdsaSha256(key.public.encoded, payload, signature)

    override fun getAttestationChain(): List<X509Certificate>? = null
    override val isHardwareBacked = false
    override fun getAttestationBlob(): ByteArray? = null
    override fun signingKeyId(): String? = if (namesKey) signingKeyIdOf(key.public.encoded) else null
    override fun signingPublicKey(): ByteArray? = if (namesKey) key.public.encoded else null
    override fun verifyWithPublicKey(publicKey: ByteArray, payload: String, signature: String) =
      verifyEcdsaSha256(publicKey, payload, signature)

    companion object {
      fun newKey(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
      }.generateKeyPair()
    }
  }

  private lateinit var ctx: Context
  private lateinit var db: AuditDatabase
  private lateinit var trail: PersistentAuditTrail
  private val provider = SwappableProvider()
  private var nowMs = 1_700_000_000_000L
  private val clock = object : Clock {
    override fun nowMs(): Long = nowMs
  }

  @Before
  fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(ctx, AuditDatabase::class.java).allowMainThreadQueries().build()
    AuditDatabase.setInstance(db)
    trail = PersistentAuditTrail(ctx, { RetentionPolicy.maximalistDefaults() }, clock, { provider })
  }

  @After
  fun tearDown() {
    db.close()
    AuditDatabase.setInstance(null)
  }

  private suspend fun signed(name: String) {
    nowMs += 10
    trail.record(name, sign = true)
  }

  private suspend fun names() = trail.getEventsInRange(0, Long.MAX_VALUE).sortedBy { it.seq }.map { it.name }

  private suspend fun assertValid() {
    assertThat(trail.verifyChainIntegrity()).isInstanceOf(ChainVerificationResult.Valid::class.java)
  }

  @Test
  fun `first signed row writes the initial key marker without a new generation`() = runTest {
    signed("a"); signed("b")

    assertThat(names()).containsExactly("signing_key", "a", "b").inOrder()
    assertThat(trail.createCheckpoint(sign = false).chainGeneration).isEqualTo(1)
    assertValid()
  }

  @Test
  fun `a replaced key records a change row and starts generation two`() = runTest {
    signed("a"); signed("b")
    provider.swapKey()
    signed("c")

    assertThat(names()).containsExactly("signing_key", "a", "b", "signing_key_changed", "c").inOrder()
    assertThat(trail.createCheckpoint(sign = false).chainGeneration).isEqualTo(2)
    val rows = trail.getEventsInRange(0, Long.MAX_VALUE).sortedBy { it.seq }
    assertThat(rows.map { it.chainGeneration }).containsExactly(1, 1, 1, 2, 2).inOrder()
    assertValid()
  }

  @Test
  fun `rows signed before the first marker verify against that marker's key`() = runTest {
    provider.namesKey = false
    signed("old1"); signed("old2")
    provider.namesKey = true
    signed("new1")
    provider.swapKey()
    signed("new2")

    assertValid()
  }

  @Test
  fun `a signature from the wrong key is still invalid`() = runTest {
    signed("a")
    provider.swapKey()
    signed("b")
    val forged = SwappableProvider().signAuditEntry("anything")

    db.openHelper.writableDatabase.execSQL("UPDATE audit_events SET signature = '$forged' WHERE name = 'a'")

    assertThat(trail.verifyChainIntegrity()).isInstanceOf(ChainVerificationResult.SignatureInvalid::class.java)
  }

  @Test
  fun `signing failure after a key change keeps the marker and the chain valid`() = runTest {
    signed("a")
    provider.swapKey()
    provider.failSigning = true
    signed("b")

    assertThat(names()).contains("signing_key_changed")
    assertThat(trail.getEventsInRange(0, Long.MAX_VALUE).single { it.name == "b" }.signature).isNull()
    assertValid()
  }

  @Test
  fun `a provider that cannot name its key writes no markers`() = runTest {
    provider.namesKey = false
    signed("a")
    provider.swapKey()
    signed("b")

    assertThat(names()).containsExactly("a", "b").inOrder()
    assertThat(trail.createCheckpoint(sign = false).chainGeneration).isEqualTo(1)
  }

  @Test
  fun `signing a checkpoint after a key change also records the change`() = runTest {
    signed("a")
    provider.swapKey()

    val checkpoint = trail.createCheckpoint(sign = true)

    assertThat(checkpoint.chainGeneration).isEqualTo(2)
    assertThat(checkpoint.signature).isNotNull()
    assertValid()
  }
}
