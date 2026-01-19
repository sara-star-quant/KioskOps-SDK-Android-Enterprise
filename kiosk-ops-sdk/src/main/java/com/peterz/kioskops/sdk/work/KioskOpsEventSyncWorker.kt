package com.peterz.kioskops.sdk.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.peterz.kioskops.sdk.KioskOpsSdk

/**
 * Network-constrained worker:
 * - attempts to upload queued operational events (idempotent batch)
 * - emits heartbeat + retention after sync
 */
class KioskOpsEventSyncWorker(
  appContext: Context,
  params: WorkerParameters
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val sdk = KioskOpsSdk.getOrNull() ?: return Result.success()
    // Attempt upload. If baseUrl is not configured, syncOnce returns PermanentFailure and we don't retry.
    val r = sdk.syncOnce()
    sdk.heartbeat(reason = "periodic_sync_worker")
    return when (r) {
      is com.peterz.kioskops.sdk.transport.TransportResult.TransientFailure -> Result.retry()
      else -> Result.success()
    }
  }

  companion object {
    const val WORK_NAME = "kioskops_event_sync"
    const val WORK_TAG = "kioskops_event_sync"
  }
}
