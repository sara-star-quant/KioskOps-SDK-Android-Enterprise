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
 * Manual drill, see scripts/drills/audit-drills.sh. The writer records and erases users in a loop
 * until the host kills the process; the verifier opens whatever the kill left behind.
 */
@RunWith(AndroidJUnit4::class)
class AuditEraseKillDrillTest {

  private val ctx = ApplicationProvider.getApplicationContext<Context>()
  private val args = InstrumentationRegistry.getArguments()

  private fun trail() = PersistentAuditTrail(
    ctx,
    { RetentionPolicy.maximalistDefaults() },
    Clock.SYSTEM,
    { KeystoreAttestationProvider(ctx) },
  )

  @Test
  fun writerLoopsUntilKilled() = runBlocking {
    assumeTrue(args.getString("drill") == "kill-writer")
    if (args.getString("fresh") == "true") ctx.deleteDatabase("kioskops_audit.db")
    val trail = trail()
    var n = 0
    while (true) {
      n++
      for (i in 1..6) trail.record("k_${n}_$i", mapOf("n" to "$n"), sign = i % 2 == 0, userId = "kill-u$n")
      trail.record("bystander_$n", userId = "bystander")
      Log.i("DRILL", "CYCLE $n recorded")
      trail.eraseUser("kill-u$n", mapOf("queueDeleted" to "0"), sign = true)
      Log.i("DRILL", "CYCLE $n erased")
    }
  }

  @Test
  fun verifierAfterKill() = runBlocking {
    assumeTrue(args.getString("drill") == "kill-verify")
    val trail = trail()
    val result = trail.verifyChainIntegrity()
    val rows = trail.getEventsInRange(0, Long.MAX_VALUE)
    val partial = rows.filter { it.userId?.startsWith("kill-u") == true }.groupBy { it.userId }.keys
    Log.i("DRILL", "after kill: rows=${rows.size} verify=$result usersWithRowsLeft=$partial")
    assertThat(result).isInstanceOf(ChainVerificationResult.Valid::class.java)
    // Writing and erasing again on the survivor still verifies.
    trail.record("post_kill", userId = "kill-post")
    trail.eraseUser("kill-post", sign = true)
    assertThat(trail.verifyChainIntegrity()).isInstanceOf(ChainVerificationResult.Valid::class.java)
  }
}
