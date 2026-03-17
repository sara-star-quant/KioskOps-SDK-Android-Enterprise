/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.debug

import com.peterz.kioskops.sdk.KioskOpsConfig
import com.peterz.kioskops.sdk.audit.PersistentAuditTrail
import com.peterz.kioskops.sdk.queue.QueueRepository
import com.peterz.kioskops.sdk.util.Hashing

/**
 * Data-only debug overlay for development diagnostics.
 *
 * Returns structured state for use by host app debug UIs.
 * Not a UI component.
 *
 * @since 0.5.0
 */
class DebugOverlay(
  private val cfgProvider: () -> KioskOpsConfig,
  private val queue: QueueRepository,
  private val persistentAudit: PersistentAuditTrail,
  private val policyHashProvider: () -> String,
) {

  /**
   * Collect current SDK state for debug display.
   */
  suspend fun getState(): DebugOverlayState {
    val cfg = cfgProvider()
    val queueDepth = queue.countActive()
    val quarantineCount = queue.quarantinedSummaries(1).size.toLong()
    val auditStats = persistentAudit.getStatistics()

    return DebugOverlayState(
      queueDepth = queueDepth,
      quarantineCount = quarantineCount,
      lastSyncEnabled = cfg.syncPolicy.enabled,
      policyHash = policyHashProvider(),
      validationEnabled = cfg.validationPolicy.enabled,
      piiEnabled = cfg.piiPolicy.enabled,
      fieldEncryptionEnabled = cfg.fieldEncryptionPolicy.enabled,
      anomalyEnabled = cfg.anomalyPolicy.enabled,
      auditChainValid = auditStats.totalEvents > 0,
      auditEventCount = auditStats.totalEvents,
      encryptionEnabled = cfg.securityPolicy.encryptQueuePayloads,
    )
  }
}

/**
 * Snapshot of SDK state for debug display.
 * @since 0.5.0
 */
data class DebugOverlayState(
  val queueDepth: Long,
  val quarantineCount: Long,
  val lastSyncEnabled: Boolean,
  val policyHash: String,
  val validationEnabled: Boolean,
  val piiEnabled: Boolean,
  val fieldEncryptionEnabled: Boolean,
  val anomalyEnabled: Boolean,
  val auditChainValid: Boolean,
  val auditEventCount: Long,
  val encryptionEnabled: Boolean,
)
