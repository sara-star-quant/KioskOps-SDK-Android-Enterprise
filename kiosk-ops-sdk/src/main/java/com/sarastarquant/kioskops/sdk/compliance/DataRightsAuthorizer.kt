/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.compliance

/**
 * Authorization callback for data rights operations.
 *
 * The host application implements this interface to verify the caller's identity
 * before the SDK performs destructive data operations (export, delete, wipe).
 *
 * On shared kiosk devices, this prevents one user from accessing or erasing
 * another user's local data. The SDK calls this before every data rights
 * operation; if the authorizer returns false, the operation is blocked and
 * an audit record is created.
 *
 * If no authorizer is configured and the config requires authorization
 * (CUI and CJIS presets), operations return [DataDeletionResult.Unauthorized]
 * or [DataExportResult.Unauthorized].
 *
 * @since 1.0.0
 */
fun interface DataRightsAuthorizer {
  /**
   * Authorize a data rights operation.
   *
   * @param operation The type of operation being requested.
   * @param userId The user ID the operation targets (empty for WIPE).
   * @return true to allow the operation, false to block it.
   */
  suspend fun authorize(operation: DataRightsOperation, userId: String): Boolean
}

/**
 * Type of data rights operation requiring authorization.
 *
 * @since 1.0.0
 */
enum class DataRightsOperation {
  EXPORT,
  DELETE,
  WIPE,
}
