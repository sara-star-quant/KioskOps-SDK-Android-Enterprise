package com.sarastarquant.kioskops.sdk.work

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sarastarquant.kioskops.sdk.KioskOpsSdk

/**
 * Security/compliance-first worker:
 * - emits a redacted heartbeat
 * - applies retention locally
 * - never uploads data (host app controls data residency & transfer)
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
class KioskOpsSyncWorker(
  appContext: Context,
  params: WorkerParameters
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val sdk = KioskOpsSdk.getOrNull() ?: return Result.success()
    sdk.heartbeat(reason = "periodic_worker")
    return Result.success()
  }

  companion object {
    const val WORK_NAME = "kioskops_heartbeat"
    const val WORK_TAG = "kioskops_heartbeat"
  }
}
