/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.compliance.SecurityPolicy
import com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KioskOpsSdkInitTest {

  private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

  private val testConfig = KioskOpsConfig(
    baseUrl = "https://example.invalid/",
    locationId = "TEST",
    kioskEnabled = false,
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
  }

  @After
  fun tearDown() {
    KioskOpsSdk.resetForTesting()
  }

  @Test
  fun `get before init throws KioskOpsNotInitializedException`() {
    var thrown: Throwable? = null
    try {
      KioskOpsSdk.get()
    } catch (e: Throwable) {
      thrown = e
    }
    assertThat(thrown).isInstanceOf(KioskOpsNotInitializedException::class.java)
    assertThat(thrown).isInstanceOf(KioskOpsException::class.java)
  }

  @Test
  fun `getOrNull before init returns null`() {
    assertThat(KioskOpsSdk.getOrNull()).isNull()
  }

  @Test
  fun `init then get returns instance`() {
    val sdk = KioskOpsSdk.init(ctx, configProvider = { testConfig }, cryptoProviderOverride = NoopCryptoProvider)
    assertThat(KioskOpsSdk.get()).isSameInstanceAs(sdk)
    assertThat(KioskOpsSdk.getOrNull()).isSameInstanceAs(sdk)
  }

  @Test
  fun `double init throws KioskOpsAlreadyInitializedException`() {
    KioskOpsSdk.init(ctx, configProvider = { testConfig }, cryptoProviderOverride = NoopCryptoProvider)

    var thrown: Throwable? = null
    try {
      KioskOpsSdk.init(ctx, configProvider = { testConfig }, cryptoProviderOverride = NoopCryptoProvider)
    } catch (e: Throwable) {
      thrown = e
    }
    assertThat(thrown).isInstanceOf(KioskOpsAlreadyInitializedException::class.java)
    assertThat(thrown).isInstanceOf(KioskOpsException::class.java)
  }

  @Test
  fun `SDK_VERSION is not empty`() {
    KioskOpsSdk.init(ctx, configProvider = { testConfig }, cryptoProviderOverride = NoopCryptoProvider)
    assertThat(KioskOpsSdk.SDK_VERSION).isNotEmpty()
  }
}
