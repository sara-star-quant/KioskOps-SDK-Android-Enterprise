/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.diagnostics

import android.content.Context
import android.content.SharedPreferences
import com.peterz.kioskops.sdk.audit.AuditTrail
import com.peterz.kioskops.sdk.util.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Handles remote diagnostics trigger requests.
 *
 * Security Controls (BSI APP.4.4.A7):
 * - Rate Limiting: Enforces maxRemoteTriggersPerDay
 * - Cooldown: Enforces minimum interval between triggers
 * - Audit: All triggers (accepted and rejected) are logged
 *
 * @property context Application context
 * @property policyProvider Provider for current diagnostics policy
 * @property auditTrail Audit trail for logging
 * @property clock Clock for time-based checks
 */
class RemoteDiagnosticsTrigger(
  private val context: Context,
  private val policyProvider: () -> DiagnosticsSchedulePolicy,
  private val auditTrail: AuditTrail,
  private val clock: Clock,
) {
  private val prefs: SharedPreferences by lazy {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  /**
   * Process a remote diagnostics trigger request.
   *
   * @param triggerId Unique identifier for this trigger (for deduplication)
   * @param metadata Additional context from the trigger source
   * @return Result indicating acceptance or rejection
   */
  suspend fun processTrigger(
    triggerId: String,
    metadata: Map<String, String> = emptyMap(),
  ): TriggerResult = withContext(Dispatchers.IO) {
    val policy = policyProvider()

    // Check if remote trigger is enabled
    if (!policy.remoteTriggerEnabled) {
      auditTrigger(triggerId, TriggerStatus.REJECTED, TriggerRejectionReason.DISABLED, metadata)
      return@withContext TriggerResult.Rejected(TriggerRejectionReason.DISABLED)
    }

    val now = clock.nowMs()
    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    // Rate limit check
    val storedDate = prefs.getString(KEY_DATE, null)
    val todayCount = if (storedDate == today) {
      prefs.getInt(KEY_COUNT, 0)
    } else {
      0
    }

    if (todayCount >= policy.maxRemoteTriggersPerDay) {
      auditTrigger(triggerId, TriggerStatus.REJECTED, TriggerRejectionReason.RATE_LIMIT_EXCEEDED, metadata)
      return@withContext TriggerResult.Rejected(TriggerRejectionReason.RATE_LIMIT_EXCEEDED)
    }

    // Cooldown check
    val lastTriggerMs = prefs.getLong(KEY_LAST_MS, 0L)
    if (now - lastTriggerMs < policy.remoteTriggerCooldownMs) {
      auditTrigger(triggerId, TriggerStatus.REJECTED, TriggerRejectionReason.COOLDOWN_ACTIVE, metadata)
      return@withContext TriggerResult.Rejected(TriggerRejectionReason.COOLDOWN_ACTIVE)
    }

    // Deduplication check
    val lastTriggerId = prefs.getString(KEY_LAST_TRIGGER_ID, null)
    if (lastTriggerId == triggerId) {
      auditTrigger(triggerId, TriggerStatus.REJECTED, TriggerRejectionReason.DUPLICATE, metadata)
      return@withContext TriggerResult.Rejected(TriggerRejectionReason.DUPLICATE)
    }

    // Accept the trigger
    prefs.edit()
      .putString(KEY_DATE, today)
      .putInt(KEY_COUNT, todayCount + 1)
      .putLong(KEY_LAST_MS, now)
      .putString(KEY_LAST_TRIGGER_ID, triggerId)
      .apply()

    auditTrigger(triggerId, TriggerStatus.ACCEPTED, null, metadata)
    TriggerResult.Accepted(triggerId)
  }

  /**
   * Get remaining triggers allowed today.
   */
  fun getRemainingTriggersToday(): Int {
    val policy = policyProvider()
    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    val storedDate = prefs.getString(KEY_DATE, null)

    if (storedDate != today) {
      return policy.maxRemoteTriggersPerDay
    }

    val todayCount = prefs.getInt(KEY_COUNT, 0)
    return (policy.maxRemoteTriggersPerDay - todayCount).coerceAtLeast(0)
  }

  /**
   * Get time until next trigger is allowed (in milliseconds).
   */
  fun getTimeUntilNextTriggerAllowed(): Long {
    val policy = policyProvider()
    val now = clock.nowMs()
    val lastTriggerMs = prefs.getLong(KEY_LAST_MS, 0L)
    val elapsed = now - lastTriggerMs

    return if (elapsed >= policy.remoteTriggerCooldownMs) {
      0L
    } else {
      policy.remoteTriggerCooldownMs - elapsed
    }
  }

  private suspend fun auditTrigger(
    triggerId: String,
    status: TriggerStatus,
    reason: TriggerRejectionReason?,
    metadata: Map<String, String>,
  ) {
    val fields = buildMap {
      put("trigger_id", triggerId)
      put("status", status.name)
      reason?.let { put("rejection_reason", it.name) }
      putAll(metadata)
    }
    auditTrail.record("remote_diagnostics_trigger", fields)
  }

  private enum class TriggerStatus {
    ACCEPTED,
    REJECTED,
  }

  companion object {
    private const val PREFS_NAME = "kioskops_diag_trigger"
    private const val KEY_DATE = "trigger_date"
    private const val KEY_COUNT = "trigger_count"
    private const val KEY_LAST_MS = "trigger_last_ms"
    private const val KEY_LAST_TRIGGER_ID = "trigger_last_id"
  }
}

/**
 * Result of a remote diagnostics trigger request.
 */
sealed class TriggerResult {
  /**
   * Trigger was accepted and diagnostics will be collected.
   */
  data class Accepted(val triggerId: String) : TriggerResult()

  /**
   * Trigger was rejected.
   */
  data class Rejected(val reason: TriggerRejectionReason) : TriggerResult()
}

/**
 * Reason for trigger rejection.
 */
enum class TriggerRejectionReason {
  /** Remote trigger is disabled in policy. */
  DISABLED,
  /** Maximum triggers per day exceeded. */
  RATE_LIMIT_EXCEEDED,
  /** Cooldown period has not elapsed. */
  COOLDOWN_ACTIVE,
  /** Duplicate trigger ID (already processed). */
  DUPLICATE,
}
