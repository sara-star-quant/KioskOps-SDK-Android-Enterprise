/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.audit.AuditTrail
import com.peterz.kioskops.sdk.compliance.RetentionPolicy
import com.peterz.kioskops.sdk.crypto.SoftwareAesGcmCryptoProvider
import com.peterz.kioskops.sdk.util.Clock
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for RemoteDiagnosticsTrigger.
 *
 * Security (BSI APP.4.4.A7): Validates rate limiting, cooldown, and deduplication.
 */
@RunWith(RobolectricTestRunner::class)
class RemoteDiagnosticsTriggerTest {

  private lateinit var context: Context
  private lateinit var auditTrail: AuditTrail
  private var currentTimeMs = 1700000000000L
  private val testClock = object : Clock {
    override fun nowMs(): Long = currentTimeMs
  }

  private var policy = DiagnosticsSchedulePolicy(
    remoteTriggerEnabled = true,
    maxRemoteTriggersPerDay = 3,
    remoteTriggerCooldownMs = 3_600_000L, // 1 hour
  )

  private lateinit var trigger: RemoteDiagnosticsTrigger

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()

    // Clear preferences from previous tests
    context.getSharedPreferences("kioskops_diag_trigger", Context.MODE_PRIVATE)
      .edit()
      .clear()
      .apply()

    auditTrail = AuditTrail(
      context = context,
      retentionProvider = { RetentionPolicy.maximalistDefaults() },
      clock = testClock,
      crypto = SoftwareAesGcmCryptoProvider(),
    )

    trigger = RemoteDiagnosticsTrigger(
      context = context,
      policyProvider = { policy },
      auditTrail = auditTrail,
      clock = testClock,
    )
  }

  @Test
  fun `trigger accepted when enabled`() {
    runBlocking {
      val result = trigger.processTrigger("trigger-1")
      assertThat(result).isInstanceOf(TriggerResult.Accepted::class.java)
      assertThat((result as TriggerResult.Accepted).triggerId).isEqualTo("trigger-1")
    }
  }

  @Test
  fun `trigger rejected when disabled`() {
    runBlocking {
      policy = DiagnosticsSchedulePolicy(remoteTriggerEnabled = false)
      trigger = RemoteDiagnosticsTrigger(context, { policy }, auditTrail, testClock)

      val result = trigger.processTrigger("trigger-1")
      assertThat(result).isInstanceOf(TriggerResult.Rejected::class.java)
      assertThat((result as TriggerResult.Rejected).reason)
        .isEqualTo(TriggerRejectionReason.DISABLED)
    }
  }

  @Test
  fun `rate limit enforced`() {
    runBlocking {
      // Use all 3 triggers
      repeat(3) { i ->
        currentTimeMs += 3_600_001L // Move past cooldown each time
        val result = trigger.processTrigger("trigger-$i")
        assertThat(result).isInstanceOf(TriggerResult.Accepted::class.java)
      }

      // 4th trigger should be rejected
      currentTimeMs += 3_600_001L
      val result = trigger.processTrigger("trigger-4")
      assertThat(result).isInstanceOf(TriggerResult.Rejected::class.java)
      assertThat((result as TriggerResult.Rejected).reason)
        .isEqualTo(TriggerRejectionReason.RATE_LIMIT_EXCEEDED)
    }
  }

  @Test
  fun `cooldown enforced`() {
    runBlocking {
      // First trigger accepted
      val result1 = trigger.processTrigger("trigger-1")
      assertThat(result1).isInstanceOf(TriggerResult.Accepted::class.java)

      // Advance time but not past cooldown
      currentTimeMs += 1_800_000L // 30 minutes

      // Second trigger should be rejected (cooldown active)
      val result2 = trigger.processTrigger("trigger-2")
      assertThat(result2).isInstanceOf(TriggerResult.Rejected::class.java)
      assertThat((result2 as TriggerResult.Rejected).reason)
        .isEqualTo(TriggerRejectionReason.COOLDOWN_ACTIVE)

      // Advance past cooldown
      currentTimeMs += 1_800_001L // past the 1 hour total

      // Third trigger should succeed
      val result3 = trigger.processTrigger("trigger-3")
      assertThat(result3).isInstanceOf(TriggerResult.Accepted::class.java)
    }
  }

  @Test
  fun `duplicate trigger rejected`() {
    runBlocking {
      // First trigger accepted
      val result1 = trigger.processTrigger("trigger-1")
      assertThat(result1).isInstanceOf(TriggerResult.Accepted::class.java)

      // Advance past cooldown
      currentTimeMs += 3_600_001L

      // Same trigger ID should be rejected
      val result2 = trigger.processTrigger("trigger-1")
      assertThat(result2).isInstanceOf(TriggerResult.Rejected::class.java)
      assertThat((result2 as TriggerResult.Rejected).reason)
        .isEqualTo(TriggerRejectionReason.DUPLICATE)
    }
  }

  @Test
  fun `remaining triggers today`() {
    runBlocking {
      assertThat(trigger.getRemainingTriggersToday()).isEqualTo(3)

      trigger.processTrigger("trigger-1")
      assertThat(trigger.getRemainingTriggersToday()).isEqualTo(2)

      currentTimeMs += 3_600_001L
      trigger.processTrigger("trigger-2")
      assertThat(trigger.getRemainingTriggersToday()).isEqualTo(1)

      currentTimeMs += 3_600_001L
      trigger.processTrigger("trigger-3")
      assertThat(trigger.getRemainingTriggersToday()).isEqualTo(0)
    }
  }

  @Test
  fun `time until next trigger allowed`() {
    runBlocking {
      // Initially no cooldown
      assertThat(trigger.getTimeUntilNextTriggerAllowed()).isEqualTo(0)

      trigger.processTrigger("trigger-1")

      // After trigger, should report cooldown remaining
      val remaining = trigger.getTimeUntilNextTriggerAllowed()
      assertThat(remaining).isEqualTo(3_600_000L)

      // Advance time
      currentTimeMs += 1_800_000L
      assertThat(trigger.getTimeUntilNextTriggerAllowed()).isEqualTo(1_800_000L)

      // Advance past cooldown
      currentTimeMs += 1_800_001L
      assertThat(trigger.getTimeUntilNextTriggerAllowed()).isEqualTo(0)
    }
  }

  @Test
  fun `metadata passed through to trigger result`() {
    runBlocking {
      val metadata = mapOf("source" to "fcm", "campaign" to "test")
      val result = trigger.processTrigger("trigger-1", metadata)
      assertThat(result).isInstanceOf(TriggerResult.Accepted::class.java)
    }
  }

  @Test
  fun `TriggerRejectionReason has all expected values`() {
    val reasons = TriggerRejectionReason.values()
    assertThat(reasons).hasLength(4)
    assertThat(reasons).asList().containsExactly(
      TriggerRejectionReason.DISABLED,
      TriggerRejectionReason.RATE_LIMIT_EXCEEDED,
      TriggerRejectionReason.COOLDOWN_ACTIVE,
      TriggerRejectionReason.DUPLICATE,
    )
  }
}
