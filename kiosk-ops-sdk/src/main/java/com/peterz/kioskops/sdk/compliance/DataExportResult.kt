/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.compliance

import java.io.File

/**
 * Result of a GDPR data export operation (Art. 20 portability).
 *
 * @since 0.5.0
 */
sealed class DataExportResult {
  data class Success(
    val exportFile: File,
    val queueEventCount: Int,
    val auditEventCount: Int,
    val telemetryFileCount: Int,
  ) : DataExportResult()

  data class Failed(val reason: String) : DataExportResult()

  data class NoData(val userId: String) : DataExportResult()
}
