/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.drill

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.audit.ChainVerificationResult
import com.sarastarquant.kioskops.sdk.audit.KeystoreAttestationProvider
import com.sarastarquant.kioskops.sdk.audit.PersistentAuditTrail
import com.sarastarquant.kioskops.sdk.compliance.RetentionPolicy
import com.sarastarquant.kioskops.sdk.util.Clock
import kotlinx.coroutines.runBlocking
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/**
 * Manual drill, see scripts/drills/audit-drills.sh. Opens the audit database a previous SDK version
 * wrote on this device (50 rows, signed rows, one acknowledged checkpoint at seq 40), then erases a
 * user and replaces the Keystore signing key. Skipped unless run with -e drill upgrade-verify.
 */
@RunWith(AndroidJUnit4::class)
class AuditUpgradeDrillTest {

  private val ctx = ApplicationProvider.getApplicationContext<Context>()
  private val args = InstrumentationRegistry.getArguments()

  private fun trail() = PersistentAuditTrail(
    ctx,
    { RetentionPolicy.maximalistDefaults() },
    Clock.SYSTEM,
    { KeystoreAttestationProvider(ctx) },
  )

  @Test
  fun upgradeEraseAndKeyReset() = runBlocking {
    assumeTrue(args.getString("drill") == "upgrade-verify")
    val trail = trail()

    val before = trail.verifyChainIntegrity()
    Log.i("DRILL", "opened old database: $before")
    assertThat(before).isInstanceOf(ChainVerificationResult.Valid::class.java)
    assertThat((before as ChainVerificationResult.Valid).eventCount).isEqualTo(50)

    val checkpoint = trail.createCheckpoint(sign = false)
    assertThat(checkpoint.linkSeq).isEqualTo(40)
    assertThat(checkpoint.linkHash).isNotNull()

    val erased = trail.eraseUser("drill-u1", mapOf("queueDeleted" to "0"), sign = true)
    Log.i("DRILL", "erased $erased rows")
    assertThat(erased).isEqualTo(23)
    assertThat(trail.getEventsByUserId("drill-u1")).isEmpty()
    val afterErase = trail.verifyChainIntegrity()
    Log.i("DRILL", "after erase: $afterErase")
    assertThat(afterErase).isInstanceOf(ChainVerificationResult.Valid::class.java)
    val consistent = trail.createCheckpoint(sign = false)
    assertThat(consistent.linkSeq).isEqualTo(40)
    assertThat(consistent.linkHash).isEqualTo(checkpoint.linkHash)

    // First signed write on the upgraded database announces the current key.
    trail.record("drill_pre_reset", sign = true)
    assertThat(trail.createCheckpoint(sign = false).chainGeneration).isEqualTo(1)

    // Lose the key the way a device reset or invalidation does.
    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("kioskops_audit_signing")
    trail.record("drill_after_reset", sign = true)

    val names = trail.getEventsInRange(0, Long.MAX_VALUE).sortedBy { it.seq }.map { it.name }
    Log.i("DRILL", "marker rows: ${names.filter { it.startsWith("signing_key") }}")
    assertThat(names).contains("signing_key_changed")
    assertThat(trail.createCheckpoint(sign = true).chainGeneration).isEqualTo(2)
    val afterReset = trail.verifyChainIntegrity()
    Log.i("DRILL", "after key reset: $afterReset")
    assertThat(afterReset).isInstanceOf(ChainVerificationResult.Valid::class.java)
  }
}
