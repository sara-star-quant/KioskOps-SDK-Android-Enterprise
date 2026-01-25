/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet.posture

import kotlinx.serialization.Serializable

/**
 * Storage status snapshot for fleet operations.
 *
 * Privacy (GDPR): Contains no PII. Only aggregate storage metrics.
 *
 * @property internalTotalBytes Internal storage total bytes
 * @property internalAvailableBytes Internal storage available bytes
 * @property internalUsagePercent Internal storage usage percentage 0-100
 * @property isLowStorage True if storage is critically low (<10%)
 * @property hasExternalStorage External storage available (SD card)
 * @property externalAvailableBytes External storage available bytes
 */
@Serializable
data class StorageStatus(
  val internalTotalBytes: Long,
  val internalAvailableBytes: Long,
  val internalUsagePercent: Int,
  val isLowStorage: Boolean,
  val hasExternalStorage: Boolean,
  val externalAvailableBytes: Long? = null,
) {
  /**
   * Check if storage is in a critical state (<5%).
   */
  val isCritical: Boolean
    get() = internalUsagePercent > 95

  /**
   * Get available storage in human-readable format.
   */
  fun formatAvailableStorage(): String {
    val bytes = internalAvailableBytes
    return when {
      bytes >= 1_000_000_000L -> "${bytes / 1_000_000_000L} GB"
      bytes >= 1_000_000L -> "${bytes / 1_000_000L} MB"
      bytes >= 1_000L -> "${bytes / 1_000L} KB"
      else -> "$bytes B"
    }
  }

  companion object {
    const val LOW_STORAGE_THRESHOLD_PERCENT = 10
    const val CRITICAL_STORAGE_THRESHOLD_PERCENT = 5
  }
}
