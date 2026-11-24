/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.sync

/**
 * Outcome of one sync flush attempt.
 *
 * These are counters only (no payloads, no identifiers) to keep telemetry safe.
 */
data class SyncOnceResult(
  val attempted: Int,
  val sent: Int,
  val permanentFailed: Int,
  val transientFailed: Int,
  val rejected: Int,
)
