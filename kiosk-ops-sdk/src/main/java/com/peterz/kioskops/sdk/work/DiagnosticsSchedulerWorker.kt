/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.peterz.kioskops.sdk.KioskOpsSdk
import com.peterz.kioskops.sdk.diagnostics.DiagnosticsSchedulePolicy
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for scheduled diagnostics collection.
 *
 * Security (BSI APP.4.4.A7):
 * - Runs at configured schedule to minimize battery impact
 * - Prefers idle device and unmetered network for upload
 * - All collections are audit logged
 *
 * Power Efficiency (BSI SYS.3.2.2.A8):
 * - Uses flexible scheduling window
 * - Respects battery constraints
 */
class DiagnosticsSchedulerWorker(
  context: Context,
  params: WorkerParameters,
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val sdk = KioskOpsSdk.getOrNull() ?: return Result.retry()
    val policy = sdk.currentConfig().diagnosticsSchedulePolicy

    if (!policy.scheduledEnabled) {
      return Result.success() // Disabled, no-op
    }

    return try {
      // Collect diagnostics
      val diagnosticsFile = sdk.exportDiagnostics()

      sdk.logger().i(TAG, "Scheduled diagnostics collected: ${diagnosticsFile.name}")

      // Auto-upload if enabled and uploader configured
      if (policy.autoUploadEnabled) {
        val uploaded = sdk.uploadDiagnosticsNow(
          metadata = mapOf(
            "trigger" to "scheduled",
            "schedule_type" to policy.scheduleType.name,
          )
        )
        if (uploaded) {
          sdk.logger().i(TAG, "Scheduled diagnostics uploaded")
        } else {
          sdk.logger().w(TAG, "No diagnostics uploader configured for auto-upload")
        }
      }

      Result.success()
    } catch (e: Exception) {
      sdk.logger().e(TAG, "Scheduled diagnostics collection failed", e)
      if (runAttemptCount < MAX_RETRIES) {
        Result.retry()
      } else {
        Result.failure()
      }
    }
  }

  companion object {
    private const val TAG = "DiagnosticsScheduler"
    const val WORK_NAME = "kioskops_diagnostics_scheduled"
    const val WORK_TAG = "kioskops_diagnostics"
    private const val MAX_RETRIES = 3

    /**
     * Schedule diagnostics collection based on policy.
     *
     * @param context Application context
     * @param policy Diagnostics schedule policy
     */
    fun schedule(context: Context, policy: DiagnosticsSchedulePolicy) {
      val workManager = WorkManager.getInstance(context)

      if (!policy.scheduledEnabled) {
        workManager.cancelUniqueWork(WORK_NAME)
        return
      }

      val delay = calculateInitialDelay(policy)
      val interval = when (policy.scheduleType) {
        DiagnosticsSchedulePolicy.ScheduleType.DAILY -> 24L to TimeUnit.HOURS
        DiagnosticsSchedulePolicy.ScheduleType.WEEKLY -> 7L to TimeUnit.DAYS
      }

      val constraints = Constraints.Builder()
        .setRequiredNetworkType(
          if (policy.autoUploadEnabled) NetworkType.UNMETERED
          else NetworkType.NOT_REQUIRED
        )
        .setRequiresBatteryNotLow(true)
        .build()

      val request = PeriodicWorkRequestBuilder<DiagnosticsSchedulerWorker>(
        interval.first, interval.second
      )
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setConstraints(constraints)
        .addTag(WORK_TAG)
        .build()

      workManager.enqueueUniquePeriodicWork(
        WORK_NAME,
        ExistingPeriodicWorkPolicy.UPDATE,
        request
      )
    }

    /**
     * Cancel scheduled diagnostics collection.
     */
    fun cancel(context: Context) {
      WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * Calculate initial delay to align with schedule.
     */
    private fun calculateInitialDelay(policy: DiagnosticsSchedulePolicy): Long {
      val now = Calendar.getInstance()
      val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, policy.scheduleHour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)

        if (policy.scheduleType == DiagnosticsSchedulePolicy.ScheduleType.WEEKLY) {
          // Convert 1-7 (Monday-Sunday) to Calendar day of week
          val calendarDayOfWeek = when (policy.scheduleDayOfWeek) {
            7 -> Calendar.SUNDAY
            else -> policy.scheduleDayOfWeek + 1
          }
          set(Calendar.DAY_OF_WEEK, calendarDayOfWeek)
        }

        // If target time is in the past, move to next occurrence
        if (before(now)) {
          when (policy.scheduleType) {
            DiagnosticsSchedulePolicy.ScheduleType.DAILY -> add(Calendar.DAY_OF_YEAR, 1)
            DiagnosticsSchedulePolicy.ScheduleType.WEEKLY -> add(Calendar.WEEK_OF_YEAR, 1)
          }
        }
      }

      return target.timeInMillis - now.timeInMillis
    }
  }
}
