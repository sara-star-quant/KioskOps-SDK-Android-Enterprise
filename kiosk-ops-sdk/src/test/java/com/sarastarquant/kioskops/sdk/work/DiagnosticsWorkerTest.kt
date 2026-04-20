/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.KioskOpsSdk
import com.sarastarquant.kioskops.sdk.compliance.SecurityPolicy
import com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider
import com.sarastarquant.kioskops.sdk.diagnostics.DiagnosticsSchedulePolicy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [DiagnosticsSchedulerWorker].
 *
 * Verifies scheduling, cancellation, and doWork behavior under various
 * schedule policies using WorkManager test infrastructure.
 */
@RunWith(RobolectricTestRunner::class)
class DiagnosticsWorkerTest {

  private lateinit var ctx: Context

  @Before
  fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()
    ctx.deleteDatabase("kiosk_ops_queue.db")
    ctx.deleteDatabase("kioskops_audit.db")
    WorkManagerTestInitHelper.initializeTestWorkManager(
      ctx,
      Configuration.Builder()
        .setMinimumLoggingLevel(android.util.Log.DEBUG)
        .build()
    )
  }

  @After
  fun tearDown() {
    KioskOpsSdk.resetForTesting()
    KioskOpsSdk.skipLifecycleObserverRegistrationForTesting = true
  }

  private fun initSdk(diagnosticsPolicy: DiagnosticsSchedulePolicy = DiagnosticsSchedulePolicy.disabledDefaults()) {
    KioskOpsSdk.init(
      context = ctx,
      configProvider = {
        KioskOpsConfig(
          baseUrl = "https://example.invalid",
          locationId = "test-loc",
          kioskEnabled = true,
          securityPolicy = SecurityPolicy.maximalistDefaults().copy(
            encryptQueuePayloads = false,
            encryptTelemetryAtRest = false,
            encryptExportedLogs = false,
          ),
          diagnosticsSchedulePolicy = diagnosticsPolicy,
        )
      },
      cryptoProviderOverride = NoopCryptoProvider,
    )
  }

  // -------------------------------------------------------------------------
  // doWork: SDK not initialized
  // -------------------------------------------------------------------------

  @Test
  fun `doWork returns RETRY when SDK is not initialized`() = runTest {
    val worker = TestListenableWorkerBuilder<DiagnosticsSchedulerWorker>(ctx).build()
    val result = worker.doWork()
    assertThat(result).isEqualTo(ListenableWorker.Result.retry())
  }

  // -------------------------------------------------------------------------
  // doWork: scheduled disabled
  // -------------------------------------------------------------------------

  @Test
  fun `doWork returns SUCCESS when scheduling is disabled`() = runTest {
    initSdk(DiagnosticsSchedulePolicy.disabledDefaults())
    val worker = TestListenableWorkerBuilder<DiagnosticsSchedulerWorker>(ctx).build()
    val result = worker.doWork()
    assertThat(result).isEqualTo(ListenableWorker.Result.success())
  }

  // -------------------------------------------------------------------------
  // Companion: schedule with disabled policy cancels work
  // -------------------------------------------------------------------------

  @Test
  fun `schedule with disabled policy cancels existing work`() {
    val wm = WorkManager.getInstance(ctx)

    // First schedule with enabled policy
    DiagnosticsSchedulerWorker.schedule(
      ctx,
      DiagnosticsSchedulePolicy.enterpriseDefaults()
    )

    // Then schedule with disabled policy: should cancel
    DiagnosticsSchedulerWorker.schedule(
      ctx,
      DiagnosticsSchedulePolicy.disabledDefaults()
    )

    // Work should be cancelled; getWorkInfosForUniqueWork returns empty or cancelled
    val infos = wm.getWorkInfosForUniqueWork(DiagnosticsSchedulerWorker.WORK_NAME).get()
    val activeInfos = infos.filter { !it.state.isFinished }
    assertThat(activeInfos).isEmpty()
  }

  // -------------------------------------------------------------------------
  // Companion: schedule with daily policy enqueues work
  // -------------------------------------------------------------------------

  @Test
  fun `schedule with daily policy enqueues periodic work`() {
    DiagnosticsSchedulerWorker.schedule(
      ctx,
      DiagnosticsSchedulePolicy.enterpriseDefaults()
    )

    val wm = WorkManager.getInstance(ctx)
    val infos = wm.getWorkInfosForUniqueWork(DiagnosticsSchedulerWorker.WORK_NAME).get()
    assertThat(infos).isNotEmpty()
    assertThat(infos[0].tags).contains(DiagnosticsSchedulerWorker.WORK_TAG)
  }

  // -------------------------------------------------------------------------
  // Companion: schedule with weekly policy enqueues work
  // -------------------------------------------------------------------------

  @Test
  fun `schedule with weekly policy enqueues periodic work`() {
    DiagnosticsSchedulerWorker.schedule(
      ctx,
      DiagnosticsSchedulePolicy.weeklyDefaults()
    )

    val wm = WorkManager.getInstance(ctx)
    val infos = wm.getWorkInfosForUniqueWork(DiagnosticsSchedulerWorker.WORK_NAME).get()
    assertThat(infos).isNotEmpty()
  }

  // -------------------------------------------------------------------------
  // Companion: cancel removes work
  // -------------------------------------------------------------------------

  @Test
  fun `cancel removes scheduled work`() {
    // Schedule first
    DiagnosticsSchedulerWorker.schedule(
      ctx,
      DiagnosticsSchedulePolicy.enterpriseDefaults()
    )

    // Then cancel
    DiagnosticsSchedulerWorker.cancel(ctx)

    val wm = WorkManager.getInstance(ctx)
    val infos = wm.getWorkInfosForUniqueWork(DiagnosticsSchedulerWorker.WORK_NAME).get()
    val activeInfos = infos.filter { !it.state.isFinished }
    assertThat(activeInfos).isEmpty()
  }

  // -------------------------------------------------------------------------
  // Companion constants
  // -------------------------------------------------------------------------

  @Test
  fun `WORK_NAME constant is stable`() {
    assertThat(DiagnosticsSchedulerWorker.WORK_NAME).isEqualTo("kioskops_diagnostics_scheduled")
  }

  @Test
  fun `WORK_TAG constant is stable`() {
    assertThat(DiagnosticsSchedulerWorker.WORK_TAG).isEqualTo("kioskops_diagnostics")
  }

  // -------------------------------------------------------------------------
  // Schedule replaces existing work (UPDATE policy)
  // -------------------------------------------------------------------------

  @Test
  fun `schedule replaces existing work when called again`() {
    // Schedule with enterprise defaults
    DiagnosticsSchedulerWorker.schedule(
      ctx,
      DiagnosticsSchedulePolicy.enterpriseDefaults()
    )

    // Re-schedule with weekly defaults: should replace (UPDATE policy)
    DiagnosticsSchedulerWorker.schedule(
      ctx,
      DiagnosticsSchedulePolicy.weeklyDefaults()
    )

    val wm = WorkManager.getInstance(ctx)
    val infos = wm.getWorkInfosForUniqueWork(DiagnosticsSchedulerWorker.WORK_NAME).get()
    // Should still have exactly one unique work chain
    val activeInfos = infos.filter { !it.state.isFinished }
    assertThat(activeInfos).hasSize(1)
  }
}
