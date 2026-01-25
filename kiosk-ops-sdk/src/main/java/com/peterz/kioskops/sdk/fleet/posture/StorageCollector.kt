/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet.posture

import android.content.Context
import android.os.StatFs

/**
 * Collects storage status with minimal overhead.
 *
 * Privacy (GDPR): No file names, paths, or content is accessed.
 * Only aggregate storage metrics are collected.
 */
internal class StorageCollector(private val context: Context) {

  /**
   * Collect current storage status.
   *
   * @return StorageStatus or null if collection fails
   */
  fun collect(): StorageStatus? = runCatching {
    val internalStat = StatFs(context.filesDir.path)
    val externalDirs = context.getExternalFilesDirs(null)

    val total = internalStat.totalBytes
    val available = internalStat.availableBytes
    val used = total - available

    StorageStatus(
      internalTotalBytes = total,
      internalAvailableBytes = available,
      internalUsagePercent = if (total > 0) ((used * 100) / total).toInt() else 0,
      isLowStorage = available < total * 0.1,
      hasExternalStorage = externalDirs.size > 1 && externalDirs[1] != null,
      externalAvailableBytes = collectExternalAvailable(externalDirs),
    )
  }.getOrNull()

  private fun collectExternalAvailable(externalDirs: Array<java.io.File?>): Long? {
    val externalDir = externalDirs.getOrNull(1) ?: return null
    return runCatching {
      StatFs(externalDir.path).availableBytes
    }.getOrNull()
  }
}
