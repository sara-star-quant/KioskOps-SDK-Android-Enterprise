/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.work

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sarastarquant.kioskops.sdk.KioskOpsSdk

/**
 * Applies retention across queue, telemetry, audit, and exports on its own schedule, so retention
 * does not depend on a heartbeat succeeding. No network, no constraints.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
class KioskOpsRetentionWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val sdk = KioskOpsSdk.getOrNull() ?: return Result.success()
    sdk.enforceRetention()
    return Result.success()
  }

  companion object {
    const val WORK_NAME = "kioskops_retention"
    const val WORK_TAG = "kioskops_retention"
  }
}
