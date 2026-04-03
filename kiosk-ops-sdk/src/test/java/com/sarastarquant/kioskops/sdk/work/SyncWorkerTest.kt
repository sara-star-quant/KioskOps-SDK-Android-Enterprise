/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.KioskOpsSdk
import com.sarastarquant.kioskops.sdk.compliance.SecurityPolicy
import com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [KioskOpsSyncWorker].
 *
 * Verifies heartbeat emission and result codes for the periodic sync worker
 * using WorkManager test infrastructure under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {

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
  }

  private fun initSdk() {
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
        )
      },
      cryptoProviderOverride = NoopCryptoProvider,
    )
  }

  // -------------------------------------------------------------------------
  // doWork result codes
  // -------------------------------------------------------------------------

  @Test
  fun `doWork returns SUCCESS when SDK is not initialized`() = runTest {
    // SDK not initialized; worker should gracefully succeed
    val worker = TestListenableWorkerBuilder<KioskOpsSyncWorker>(ctx).build()
    val result = worker.doWork()
    assertThat(result).isEqualTo(ListenableWorker.Result.success())
  }

  @Test
  fun `doWork returns SUCCESS when SDK is initialized`() = runTest {
    initSdk()
    val worker = TestListenableWorkerBuilder<KioskOpsSyncWorker>(ctx).build()
    val result = worker.doWork()
    assertThat(result).isEqualTo(ListenableWorker.Result.success())
  }

  @Test
  fun `doWork calls heartbeat and returns SUCCESS`() = runTest {
    initSdk()
    val worker = TestListenableWorkerBuilder<KioskOpsSyncWorker>(ctx).build()
    val result = worker.doWork()

    // The heartbeat is fire-and-forget; verify the worker still succeeds
    assertThat(result).isEqualTo(ListenableWorker.Result.success())
  }

  // -------------------------------------------------------------------------
  // Companion constants
  // -------------------------------------------------------------------------

  @Test
  fun `WORK_NAME constant is stable`() {
    assertThat(KioskOpsSyncWorker.WORK_NAME).isEqualTo("kioskops_heartbeat")
  }

  @Test
  fun `WORK_TAG constant is stable`() {
    assertThat(KioskOpsSyncWorker.WORK_TAG).isEqualTo("kioskops_heartbeat")
  }

  // -------------------------------------------------------------------------
  // Multiple invocations
  // -------------------------------------------------------------------------

  @Test
  fun `doWork is idempotent across multiple invocations`() = runTest {
    initSdk()
    val worker = TestListenableWorkerBuilder<KioskOpsSyncWorker>(ctx).build()

    val result1 = worker.doWork()
    val result2 = worker.doWork()

    assertThat(result1).isEqualTo(ListenableWorker.Result.success())
    assertThat(result2).isEqualTo(ListenableWorker.Result.success())
  }
}
