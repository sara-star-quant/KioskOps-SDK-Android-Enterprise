/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.compliance.SecurityPolicy
import com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider
import com.sarastarquant.kioskops.sdk.queue.EnqueueResult
import com.sarastarquant.kioskops.sdk.transport.TransportResult
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class BlockingWrapperTest {

  private lateinit var ctx: Context
  private lateinit var sdk: KioskOpsSdk

  @Before
  fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()
    ctx.deleteDatabase("kiosk_ops_queue.db")
    ctx.deleteDatabase("kioskops_audit.db")
    WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
    sdk = KioskOpsSdk.init(
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

  @After
  fun tearDown() {
    KioskOpsSdk.resetForTesting()
  }

  @Test
  fun `enqueueBlocking completes with true`() {
    val future = sdk.enqueueBlocking("TEST", """{"key":"value"}""")
    val result = future.get(5, TimeUnit.SECONDS)
    assertThat(result).isTrue()
  }

  @Test
  fun `enqueueDetailedBlocking completes with Accepted`() {
    val future = sdk.enqueueDetailedBlocking("TEST", """{"key":"value"}""")
    val result = future.get(5, TimeUnit.SECONDS)
    assertThat(result).isInstanceOf(EnqueueResult.Accepted::class.java)
  }

  @Test
  fun `syncOnceBlocking completes with Success when sync disabled`() {
    val future = sdk.syncOnceBlocking()
    val result = future.get(5, TimeUnit.SECONDS)
    // Sync disabled by default, so returns Success with 0 attempted
    assertThat(result).isInstanceOf(TransportResult.Success::class.java)
  }

  @Test
  fun `heartbeatBlocking completes without error`() {
    val future = sdk.heartbeatBlocking("test_blocking")
    future.get(5, TimeUnit.SECONDS) // should not throw
  }

  @Test
  fun `queueDepthBlocking returns 0 initially`() {
    val future = sdk.queueDepthBlocking()
    val depth = future.get(5, TimeUnit.SECONDS)
    assertThat(depth).isEqualTo(0L)
  }

  @Test
  fun `healthCheckBlocking returns valid status`() {
    val future = sdk.healthCheckBlocking()
    val health = future.get(5, TimeUnit.SECONDS)
    assertThat(health.isInitialized).isTrue()
    assertThat(health.sdkVersion).isNotEmpty()
    assertThat(health.queueDepth).isEqualTo(0L)
  }
}
