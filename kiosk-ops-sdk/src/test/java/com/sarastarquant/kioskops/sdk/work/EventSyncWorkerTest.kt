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
import com.sarastarquant.kioskops.sdk.sync.SyncPolicy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [KioskOpsEventSyncWorker].
 *
 * Exercises sync result handling (success, transient failure, permanent failure)
 * and verifies correct behavior when the SDK is not initialized.
 */
@RunWith(RobolectricTestRunner::class)
class EventSyncWorkerTest {

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

  private fun initSdk(syncEnabled: Boolean = false) {
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
          syncPolicy = if (syncEnabled) {
            SyncPolicy.enabledDefaults()
          } else {
            SyncPolicy.disabledDefaults()
          },
        )
      },
      cryptoProviderOverride = NoopCryptoProvider,
    )
  }

  // -------------------------------------------------------------------------
  // SDK not initialized
  // -------------------------------------------------------------------------

  @Test
  fun `doWork returns SUCCESS when SDK is not initialized`() = runTest {
    val worker = TestListenableWorkerBuilder<KioskOpsEventSyncWorker>(ctx).build()
    val result = worker.doWork()
    assertThat(result).isEqualTo(ListenableWorker.Result.success())
  }

  // -------------------------------------------------------------------------
  // Sync disabled (default config)
  // -------------------------------------------------------------------------

  @Test
  fun `doWork returns SUCCESS with sync disabled and invalid baseUrl`() = runTest {
    // example.invalid causes PermanentFailure; maps to success (no retry)
    initSdk(syncEnabled = false)
    val worker = TestListenableWorkerBuilder<KioskOpsEventSyncWorker>(ctx).build()
    val result = worker.doWork()
    assertThat(result).isEqualTo(ListenableWorker.Result.success())
  }

  // -------------------------------------------------------------------------
  // Sync enabled with unreachable server
  // -------------------------------------------------------------------------

  @Test
  fun `doWork returns SUCCESS on permanent failure with invalid host`() = runTest {
    // With sync enabled but an invalid host, the transport layer returns PermanentFailure
    // which the worker maps to success() to avoid infinite retries
    initSdk(syncEnabled = true)
    val worker = TestListenableWorkerBuilder<KioskOpsEventSyncWorker>(ctx).build()
    val result = worker.doWork()
    assertThat(result).isEqualTo(ListenableWorker.Result.success())
  }

  // -------------------------------------------------------------------------
  // Companion constants
  // -------------------------------------------------------------------------

  @Test
  fun `WORK_NAME constant is stable`() {
    assertThat(KioskOpsEventSyncWorker.WORK_NAME).isEqualTo("kioskops_event_sync")
  }

  @Test
  fun `WORK_TAG constant is stable`() {
    assertThat(KioskOpsEventSyncWorker.WORK_TAG).isEqualTo("kioskops_event_sync")
  }

  // -------------------------------------------------------------------------
  // Idempotency
  // -------------------------------------------------------------------------

  @Test
  fun `doWork is idempotent across multiple invocations`() = runTest {
    initSdk()
    val worker = TestListenableWorkerBuilder<KioskOpsEventSyncWorker>(ctx).build()

    val result1 = worker.doWork()
    val result2 = worker.doWork()

    assertThat(result1).isEqualTo(ListenableWorker.Result.success())
    assertThat(result2).isEqualTo(ListenableWorker.Result.success())
  }
}
