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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SdkLifecycleTest {

  private val ctx = ApplicationProvider.getApplicationContext<Context>()

  private val testConfig = KioskOpsConfig(
    baseUrl = "https://example.invalid/",
    locationId = "TEST-LIFECYCLE",
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
    KioskOpsSdk.skipLifecycleObserverRegistrationForTesting = true
  }

  @After
  fun tearDown() {
    KioskOpsSdk.resetForTesting()
  }

  @Test
  fun `shutdown clears singleton instance`() = runTest {
    KioskOpsSdk.init(
      context = ctx,
      configProvider = { testConfig },
      cryptoProviderOverride = NoopCryptoProvider,
    )

    assertThat(KioskOpsSdk.getOrNull()).isNotNull()
    KioskOpsSdk.get().shutdown()
    assertThat(KioskOpsSdk.getOrNull()).isNull()
  }

  @Test
  fun `reinitialize after shutdown`() = runTest {
    KioskOpsSdk.init(
      context = ctx,
      configProvider = { testConfig },
      cryptoProviderOverride = NoopCryptoProvider,
    )

    KioskOpsSdk.get().shutdown()

    val sdk = KioskOpsSdk.init(
      context = ctx,
      configProvider = { testConfig.copy(locationId = "TEST-2") },
      cryptoProviderOverride = NoopCryptoProvider,
    )

    assertThat(sdk.currentConfig().locationId).isEqualTo("TEST-2")
  }

  @Test
  fun `resetForTesting cancels scopes and clears instance`() {
    KioskOpsSdk.init(
      context = ctx,
      configProvider = { testConfig },
      cryptoProviderOverride = NoopCryptoProvider,
    )

    assertThat(KioskOpsSdk.getOrNull()).isNotNull()
    KioskOpsSdk.resetForTesting()
    assertThat(KioskOpsSdk.getOrNull()).isNull()
  }
}
