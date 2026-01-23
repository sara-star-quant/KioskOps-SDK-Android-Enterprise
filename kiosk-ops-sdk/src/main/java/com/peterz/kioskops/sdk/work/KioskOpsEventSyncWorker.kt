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
    val sdk = KioskOpsSdk.getOrNull()
    if (sdk == null) {
      // SDK not initialized; this is a configuration issue. Log and succeed to avoid infinite retries.
      android.util.Log.w(TAG, "KioskOpsSdk not initialized; skipping sync worker")
      return Result.success()
    }

    val r = sdk.syncOnce()
    sdk.heartbeat(reason = "periodic_sync_worker")

    return when (r) {
      is com.peterz.kioskops.sdk.transport.TransportResult.Success -> Result.success()
      is com.peterz.kioskops.sdk.transport.TransportResult.TransientFailure -> {
        android.util.Log.w(TAG, "Sync transient failure: ${r.message}; will retry")
        Result.retry()
      }
      is com.peterz.kioskops.sdk.transport.TransportResult.PermanentFailure -> {
        // Permanent failure (e.g., baseUrl not configured, auth error). Don't retry—operator must fix config.
        android.util.Log.e(TAG, "Sync permanent failure: ${r.message}; check SDK configuration")
        Result.success()
      }
    }
  }

  companion object {
    private const val TAG = "KioskOpsSyncWorker"

    const val WORK_NAME = "kioskops_event_sync"
    const val WORK_TAG = "kioskops_event_sync"
  }
}
