/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.compliance.SecurityPolicy
import com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FlowApisTest {

  private val ctx = ApplicationProvider.getApplicationContext<Context>()

  private val testConfig = KioskOpsConfig(
    baseUrl = "https://example.invalid/",
    locationId = "TEST-FLOW",
    kioskEnabled = true,
    securityPolicy = SecurityPolicy.maximalistDefaults().copy(
      encryptQueuePayloads = false,
      encryptTelemetryAtRest = false,
      encryptDiagnosticsBundle = false,
      encryptExportedLogs = false,
    ),
  )

  @Before
  fun setUp() {
    WorkManagerTestInitHelper.initializeTestWorkManager(
      ctx, Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build()
    )
    KioskOpsSdk.resetForTesting()
    KioskOpsSdk.init(
      context = ctx,
      configProvider = { testConfig },
      cryptoProviderOverride = NoopCryptoProvider,
    )
  }

  @After
  fun tearDown() {
    KioskOpsSdk.resetForTesting()
  }

  @Test
  fun `queueDepthFlow emits initial depth`() = runTest {
    val sdk = KioskOpsSdk.get()
    val depth = sdk.queueDepthFlow(intervalMs = 100L).first()
    assertThat(depth).isEqualTo(0L)
  }

  @Test
  fun `healthStatusFlow emits health result`() = runTest {
    val sdk = KioskOpsSdk.get()
    val health = sdk.healthStatusFlow(intervalMs = 100L).first()
    assertThat(health.isInitialized).isTrue()
    assertThat(health.sdkVersion).isNotEmpty()
  }

  @Test
  fun `queueDepthFlow reflects enqueue via first()`() = runTest {
    val sdk = KioskOpsSdk.get()
    assertThat(sdk.queueDepthFlow().first()).isEqualTo(0L)

    sdk.enqueue("evt", "{\"x\":1}")
    // Flow is Room-reactive since 1.2.0: first() after the enqueue sees the new depth.
    assertThat(sdk.queueDepthFlow().first()).isEqualTo(1L)
  }
}
