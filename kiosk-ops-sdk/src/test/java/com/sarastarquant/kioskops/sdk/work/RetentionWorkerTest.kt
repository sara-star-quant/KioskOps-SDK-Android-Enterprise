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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tests for [KioskOpsRetentionWorker] and its scheduling. */
@RunWith(RobolectricTestRunner::class)
class RetentionWorkerTest {

  private lateinit var ctx: Context
  private lateinit var cfg: KioskOpsConfig

  @Before
  fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()
    ctx.deleteDatabase("kiosk_ops_queue.db")
    ctx.deleteDatabase("kioskops_audit.db")
    WorkManagerTestInitHelper.initializeTestWorkManager(
      ctx,
      Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
    )
    cfg = KioskOpsConfig(
      baseUrl = "https://example.invalid",
      locationId = "test-loc",
      kioskEnabled = true,
      securityPolicy = SecurityPolicy.maximalistDefaults().copy(
        encryptQueuePayloads = false,
        encryptTelemetryAtRest = false,
        encryptExportedLogs = false,
      ),
    )
  }

  @After
  fun tearDown() {
    KioskOpsSdk.resetForTesting()
  }

  private fun initSdk(): KioskOpsSdk =
    KioskOpsSdk.init(context = ctx, configProvider = { cfg }, cryptoProviderOverride = NoopCryptoProvider)

  @Test
  fun `doWork returns SUCCESS when SDK is not initialized`() = runTest {
    val worker = TestListenableWorkerBuilder<KioskOpsRetentionWorker>(ctx).build()
    assertThat(worker.doWork()).isEqualTo(ListenableWorker.Result.success())
  }

  @Test
  fun `doWork returns SUCCESS when SDK is initialized`() = runTest {
    initSdk()
    val worker = TestListenableWorkerBuilder<KioskOpsRetentionWorker>(ctx).build()
    assertThat(worker.doWork()).isEqualTo(ListenableWorker.Result.success())
  }

  @Test
  fun `enforceRetention reports the effective audit retention`() = runTest {
    val sdk = initSdk()
    val policy = cfg.retentionPolicy
    val report = sdk.enforceRetention()
    assertThat(report.effectiveAuditRetentionDays)
      .isEqualTo(maxOf(policy.retainAuditDays, policy.minimumAuditRetentionDays))
  }

  @Test
  fun `applySchedulingFromConfig enqueues the retention worker`() {
    val sdk = initSdk()
    sdk.applySchedulingFromConfig()
    val infos = WorkManager.getInstance(ctx).getWorkInfosByTag(KioskOpsRetentionWorker.WORK_TAG).get()
    assertThat(infos).hasSize(1)
  }

  @Test
  fun `constants are stable`() {
    assertThat(KioskOpsRetentionWorker.WORK_NAME).isEqualTo("kioskops_retention")
    assertThat(KioskOpsRetentionWorker.WORK_TAG).isEqualTo("kioskops_retention")
  }
}
