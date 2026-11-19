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
import com.sarastarquant.kioskops.sdk.audit.core.PinResult
import com.sarastarquant.kioskops.sdk.audit.db.AuditDatabase
import com.sarastarquant.kioskops.sdk.compliance.RetentionPolicy
import com.sarastarquant.kioskops.sdk.util.Clock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.cert.X509Certificate

@RunWith(RobolectricTestRunner::class)
class PersistentAuditTrailEraseTest {

  private lateinit var ctx: Context
  private lateinit var db: AuditDatabase
  private lateinit var trail: PersistentAuditTrail

  private var nowMs = 1_700_000_000_000L
  private val clock = object : Clock {
    override fun nowMs(): Long = nowMs
  }
  private val dayMs = 24 * 60 * 60 * 1000L

  private class ThrowingSigner : DeviceAttestationProvider {
    override fun signAuditEntry(payload: String): String = error("signer down")
    override fun verifySignature(payload: String, signature: String) = false
    override fun getAttestationChain(): List<X509Certificate>? = null
    override val isHardwareBacked = false
    override fun getAttestationBlob(): ByteArray? = null
  }

  @Before
  fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(ctx, AuditDatabase::class.java).allowMainThreadQueries().build()
    AuditDatabase.setInstance(db)
    trail = newTrail()
  }

  private fun newTrail(signer: DeviceAttestationProvider? = null) = PersistentAuditTrail(
    context = ctx,
    retentionProvider = { RetentionPolicy.maximalistDefaults() },
    clock = clock,
    attestationProvider = signer?.let { s -> { s } },
  )

  @After
  fun tearDown() {
    db.close()
    AuditDatabase.setInstance(null)
  }

  private suspend fun record(userId: String?, secret: String = "x") {
    nowMs += 10
    trail.record("evt", mapOf("secret" to secret), userId = userId)
  }

  private suspend fun assertValid(rows: Int) {
    val result = trail.verifyChainIntegrity()
    assertThat(result).isInstanceOf(ChainVerificationResult.Valid::class.java)
    assertThat((result as ChainVerificationResult.Valid).eventCount).isEqualTo(rows)
  }

  @Test
  fun `erasing a middle user keeps the chain valid`() = runTest {
    record("u1"); record("u2"); record("u1"); record("u2"); record("u1")

    assertThat(trail.eraseUser("u1")).isEqualTo(3)

    assertThat(trail.getEventsByUserId("u1")).isEmpty()
    assertValid(rows = 3)
  }

  @Test
  fun `erasing head and tail rows keeps the chain valid`() = runTest {
    record("u1"); record("u1"); record("u2"); record("u2"); record("u1"); record("u1")

    assertThat(trail.eraseUser("u1")).isEqualTo(4)

    assertValid(rows = 3)
  }

  @Test
  fun `two erasures that touch keep the chain valid`() = runTest {
    record("u1"); record("u2"); record("u3"); record("u4")

    trail.eraseUser("u2")
    trail.eraseUser("u3")

    assertValid(rows = 4)
  }

  @Test
  fun `erasing a user with no rows only adds the record`() = runTest {
    record("u1"); record("u2")

    assertThat(trail.eraseUser("nobody")).isEqualTo(0)

    assertValid(rows = 3)
  }

  @Test
  fun `erasing the acknowledged row leaves a consistent next checkpoint`() = runTest {
    record("u2")
    record("u1")
    val pin = trail.createCheckpoint(sign = false)
    trail.acknowledgeCheckpoint(pin)
    record("u2")

    trail.eraseUser("u1")

    val current = trail.createCheckpoint(sign = false)
    assertThat(current.linkSeq).isEqualTo(pin.headSeq)
    assertThat(current.linkHash).isEqualTo(pin.headHash)
    assertThat(current.headSeq).isGreaterThan(pin.headSeq)
    assertThat(AuditChainCore.compareToPin(pin, nowMs, current, nowMs, dayMs)).isEqualTo(PinResult.Consistent)
  }

  @Test
  fun `erasing the newest rows does not regress the head`() = runTest {
    record("u2")
    record("u1")
    val pin = trail.createCheckpoint(sign = false)
    trail.acknowledgeCheckpoint(pin)

    trail.eraseUser("u1")

    val current = trail.createCheckpoint(sign = false)
    assertThat(current.headSeq).isGreaterThan(pin.headSeq)
    assertThat(AuditChainCore.compareToPin(pin, nowMs, current, nowMs, dayMs)).isEqualTo(PinResult.Consistent)
  }

  @Test
  fun `deleting a row without an erase record is still detected`() = runTest {
    record("u1"); record("u2"); record("u3")
    trail.eraseUser("u1")

    db.openHelper.writableDatabase.execSQL("DELETE FROM audit_events WHERE userId = 'u2'")

    assertThat(trail.verifyChainIntegrity()).isNotInstanceOf(ChainVerificationResult.Valid::class.java)
  }

  @Test
  fun `an erase record edited after the fact is detected`() = runTest {
    record("u1"); record("u2")
    trail.eraseUser("u1")

    db.openHelper.writableDatabase.execSQL(
      "UPDATE audit_events SET fieldsJson = REPLACE(fieldsJson, 'runs', 'runz') WHERE name = 'user_data_deleted'",
    )

    assertThat(trail.verifyChainIntegrity()).isNotInstanceOf(ChainVerificationResult.Valid::class.java)
  }

  @Test
  fun `the erase leaves neither the user id nor erased content behind`() = runTest {
    record("u1", secret = "very-secret-value")
    record("u2")

    trail.eraseUser("u1", mapOf("queueDeleted" to "0"))

    val sql = db.openHelper.writableDatabase
    sql.query("SELECT COUNT(*) FROM audit_events WHERE userId = 'u1' OR fieldsJson LIKE '%very-secret-value%'").use {
      it.moveToFirst()
      assertThat(it.getInt(0)).isEqualTo(0)
    }
    sql.query("SELECT fieldsJson FROM audit_events WHERE name = 'user_data_deleted'").use {
      it.moveToFirst()
      assertThat(it.getString(0)).doesNotContain("u1")
      assertThat(it.getString(0)).contains("queueDeleted")
    }
  }

  @Test
  fun `a failing signer rolls the whole erase back`() = runTest {
    record("u1"); record("u2")
    val signing = newTrail(ThrowingSigner())

    assertThrows(IllegalStateException::class.java) {
      kotlinx.coroutines.runBlocking { signing.eraseUser("u1", sign = true) }
    }

    assertThat(trail.getEventsByUserId("u1")).hasSize(1)
    assertValid(rows = 2)
  }
}
