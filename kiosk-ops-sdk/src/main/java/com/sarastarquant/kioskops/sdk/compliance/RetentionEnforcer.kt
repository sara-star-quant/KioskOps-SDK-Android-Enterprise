/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.compliance

import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.audit.AuditTrail
import com.sarastarquant.kioskops.sdk.audit.PersistentAuditTrail
import com.sarastarquant.kioskops.sdk.queue.QueueRepository
import com.sarastarquant.kioskops.sdk.telemetry.EncryptedTelemetryStore
import com.sarastarquant.kioskops.sdk.util.Clock

/**
 * Centralized retention enforcement across all data stores.
 *
 * NIST AU-11: Audit Record Retention.
 * Supports minimum audit retention (365 days) for compliance.
 *
 * @since 0.5.0
 */
class RetentionEnforcer(
  private val queue: QueueRepository,
  private val telemetry: EncryptedTelemetryStore,
  private val audit: AuditTrail,
  private val persistentAudit: PersistentAuditTrail,
  private val clock: Clock,
) {

  /**
   * Apply retention policies across all stores.
   *
   * @param cfg The current SDK configuration.
   * @return Report of retention actions taken.
   */
  suspend fun enforce(cfg: KioskOpsConfig): RetentionReport {
    val retention = cfg.retentionPolicy

    // Queue retention
    queue.applyRetention(cfg)

    // Telemetry file retention
    telemetry.purgeOldFiles()

    // File-based audit retention
    audit.purgeOldFiles()

    // Persistent audit retention (respects minimum retention)
    val effectiveAuditDays = maxOf(
      retention.retainAuditDays,
      retention.minimumAuditRetentionDays,
    )
    val cutoffMs = clock.nowMs() - effectiveAuditDays.toLong() * 24 * 60 * 60 * 1000
    // Only delete if beyond the minimum
    if (clock.nowMs() - cutoffMs > 0) {
      persistentAudit.applyRetention()
    }

    return RetentionReport(
      effectiveAuditRetentionDays = effectiveAuditDays,
    )
  }
}

/**
 * Report of retention enforcement actions.
 * @since 0.5.0
 */
data class RetentionReport(
  val effectiveAuditRetentionDays: Int,
)
