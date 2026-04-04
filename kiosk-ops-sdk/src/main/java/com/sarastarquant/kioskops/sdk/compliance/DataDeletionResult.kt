/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.compliance

/**
 * Result of a GDPR data deletion operation (Art. 17 erasure).
 *
 * @since 0.5.0
 */
sealed class DataDeletionResult {
  data class Success(
    val queueEventsDeleted: Int,
    val auditEventsDeleted: Int,
  ) : DataDeletionResult()

  data class Failed(val reason: String) : DataDeletionResult()

  /** Operation blocked by [DataRightsAuthorizer]. @since 1.0.0 */
  data class Unauthorized(val operation: DataRightsOperation) : DataDeletionResult()
}
