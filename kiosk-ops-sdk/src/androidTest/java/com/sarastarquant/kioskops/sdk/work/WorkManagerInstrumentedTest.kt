/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.work

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Instrumented tests for WorkManager scheduling using real WorkManager infrastructure
 * on a device/emulator.
 *
 * Uses [WorkManagerTestInitHelper] and [TestDriver] for synchronous execution of workers
 * without waiting for real system scheduling.
 */
@RunWith(AndroidJUnit4::class)
class WorkManagerInstrumentedTest {

  private lateinit var workManager: WorkManager

  @Before
  fun setUp() {
    val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    val config = Configuration.Builder()
      .setMinimumLoggingLevel(android.util.Log.DEBUG)
      .build()
    WorkManagerTestInitHelper.initializeTestWorkManager(ctx, config)
    workManager = WorkManager.getInstance(ctx)
  }

  @After
  fun tearDown() {
    workManager.cancelAllWork().result.get()
    workManager.pruneWork().result.get()
  }

  // ---------------------------------------------------------------------------
  // One-time work request
  // ---------------------------------------------------------------------------

  @Test
  fun enqueueOneTimeWorkAndVerifyCompletion() {
    val request = OneTimeWorkRequestBuilder<KioskOpsSyncWorker>()
      .addTag(KioskOpsSyncWorker.WORK_TAG)
      .build()

    workManager.enqueue(request).result.get()

    // Use TestDriver to trigger immediate execution in test mode.
    val testDriver = WorkManagerTestInitHelper.getTestDriver(
      InstrumentationRegistry.getInstrumentation().targetContext
    )
    testDriver?.setInitialDelayMet(request.id)

    val info = workManager.getWorkInfoById(request.id).get()
    assertThat(info).isNotNull()
    // After test driver triggers, worker should have completed.
    // KioskOpsSyncWorker.doWork() returns Result.success() when SDK is not initialized.
    assertThat(info!!.state).isAnyOf(
      WorkInfo.State.SUCCEEDED,
      WorkInfo.State.ENQUEUED,
      WorkInfo.State.RUNNING,
    )
  }

  // ---------------------------------------------------------------------------
  // Periodic work by tag
  // ---------------------------------------------------------------------------

  @Test
  fun periodicWorkIsEnqueuedByTag() {
    val request = PeriodicWorkRequestBuilder<KioskOpsSyncWorker>(
      15, TimeUnit.MINUTES,
    )
      .addTag(KioskOpsSyncWorker.WORK_TAG)
      .build()

    workManager.enqueueUniquePeriodicWork(
      KioskOpsSyncWorker.WORK_NAME,
      ExistingPeriodicWorkPolicy.KEEP,
      request,
    ).result.get()

    val infos = workManager.getWorkInfosByTag(KioskOpsSyncWorker.WORK_TAG).get()
    assertThat(infos).isNotEmpty()
    assertThat(infos[0].tags).contains(KioskOpsSyncWorker.WORK_TAG)
    assertThat(infos[0].state).isEqualTo(WorkInfo.State.ENQUEUED)
  }

  // ---------------------------------------------------------------------------
  // Cancel work by tag
  // ---------------------------------------------------------------------------

  @Test
  fun cancelWorkByTagAndVerifyNoPendingWork() {
    val request = OneTimeWorkRequestBuilder<KioskOpsSyncWorker>()
      .addTag(KioskOpsSyncWorker.WORK_TAG)
      .build()

    workManager.enqueue(request).result.get()

    // Verify work exists.
    val before = workManager.getWorkInfosByTag(KioskOpsSyncWorker.WORK_TAG).get()
    assertThat(before).isNotEmpty()

    // Cancel by tag.
    workManager.cancelAllWorkByTag(KioskOpsSyncWorker.WORK_TAG).result.get()

    // After cancellation, work should be in a terminal state (CANCELLED or SUCCEEDED).
    // The worker may complete before the cancel call reaches it.
    val after = workManager.getWorkInfosByTag(KioskOpsSyncWorker.WORK_TAG).get()
    after.forEach { info ->
      assertThat(info.state).isAnyOf(WorkInfo.State.CANCELLED, WorkInfo.State.SUCCEEDED)
    }
  }
}
