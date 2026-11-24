/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.drill

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.audit.ChainVerificationResult
import com.sarastarquant.kioskops.sdk.audit.KeystoreAttestationProvider
import com.sarastarquant.kioskops.sdk.audit.PersistentAuditTrail
import com.sarastarquant.kioskops.sdk.compliance.RetentionPolicy
import com.sarastarquant.kioskops.sdk.util.Clock
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Manual drill, see scripts/drills/audit-drills.sh. Writes an audit database with the real Keystore
 * key: 50 rows, every second one signed, users drill-u1 and drill-u2, one acknowledged checkpoint at
 * seq 40. Uses only APIs that exist in 1.6.0 so the script can run it from an older tag.
 */
@RunWith(AndroidJUnit4::class)
class AuditDrillWriterTest {
  @Test
  fun writeDatabase() = runBlocking {
    assumeTrue(InstrumentationRegistry.getArguments().getString("drill") == "write")
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    ctx.deleteDatabase("kioskops_audit.db")
    val provider = KeystoreAttestationProvider(ctx)
    val trail = PersistentAuditTrail(ctx, { RetentionPolicy.maximalistDefaults() }, Clock.SYSTEM, { provider })
    for (i in 1..40) {
      trail.record("evt_$i", mapOf("i" to "$i"), sign = i % 2 == 0, userId = if (i % 3 == 0) "drill-u1" else "drill-u2")
    }
    val cp = trail.createCheckpoint(sign = true)
    trail.acknowledgeCheckpoint(cp)
    for (i in 41..50) trail.record("evt_$i", mapOf("i" to "$i"), sign = true, userId = "drill-u1")
    val result = trail.verifyChainIntegrity()
    Log.i("DRILL", "wrote 50 rows, acked head=${cp.headSeq}, verify=$result")
    assertThat(result).isInstanceOf(ChainVerificationResult.Valid::class.java)
  }
}
