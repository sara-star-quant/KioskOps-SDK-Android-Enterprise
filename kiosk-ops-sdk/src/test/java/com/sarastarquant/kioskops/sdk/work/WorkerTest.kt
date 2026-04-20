/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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

@RunWith(RobolectricTestRunner::class)
class WorkerTest {

  private lateinit var ctx: Context

  @Before
  fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()
    ctx.deleteDatabase("kiosk_ops_queue.db")
    ctx.deleteDatabase("kioskops_audit.db")
    WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
  }

  @After
  fun tearDown() {
    KioskOpsSdk.resetForTesting()
    KioskOpsSdk.skipLifecycleObserverRegistrationForTesting = true
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

  @Test
  fun `KioskOpsSyncWorker succeeds when SDK not initialized`() = runTest {
    val worker = TestListenableWorkerBuilder<KioskOpsSyncWorker>(ctx).build()
    val result = worker.doWork()
    assertThat(result).isEqualTo(ListenableWorker.Result.success())
  }

  @Test
  fun `KioskOpsSyncWorker succeeds when SDK initialized`() = runTest {
    initSdk()
    val worker = TestListenableWorkerBuilder<KioskOpsSyncWorker>(ctx).build()
    val result = worker.doWork()
    assertThat(result).isEqualTo(ListenableWorker.Result.success())
  }

  @Test
  fun `KioskOpsEventSyncWorker succeeds when SDK not initialized`() = runTest {
    val worker = TestListenableWorkerBuilder<KioskOpsEventSyncWorker>(ctx).build()
    val result = worker.doWork()
    assertThat(result).isEqualTo(ListenableWorker.Result.success())
  }

  @Test
  fun `KioskOpsEventSyncWorker returns success on permanent failure`() = runTest {
    // example.invalid baseUrl causes PermanentFailure in OkHttpTransport
    initSdk()
    val worker = TestListenableWorkerBuilder<KioskOpsEventSyncWorker>(ctx).build()
    val result = worker.doWork()
    // PermanentFailure maps to success() (no retry: operator must fix config)
    assertThat(result).isEqualTo(ListenableWorker.Result.success())
  }
}
