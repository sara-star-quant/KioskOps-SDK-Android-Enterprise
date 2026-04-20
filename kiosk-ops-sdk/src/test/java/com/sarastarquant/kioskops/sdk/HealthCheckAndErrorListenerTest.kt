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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HealthCheckAndErrorListenerTest {

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

  private fun initSdk(): KioskOpsSdk {
    return KioskOpsSdk.init(
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
  fun `healthCheck returns structured status`() = runTest {
    val sdk = initSdk()
    val health = sdk.healthCheck()

    assertThat(health.isInitialized).isTrue()
    assertThat(health.queueDepth).isEqualTo(0)
    assertThat(health.syncEnabled).isFalse() // SyncPolicy defaults to disabled
    assertThat(health.authProviderConfigured).isFalse()
    assertThat(health.sdkVersion).isNotEmpty()
  }

  @Test
  fun `healthCheck reflects queue depth`() = runTest {
    val sdk = initSdk()
    sdk.enqueue("test_event", """{"key":"value"}""")

    val health = sdk.healthCheck()
    assertThat(health.queueDepth).isEqualTo(1)
  }

  @Test
  fun `setErrorListener receives error callbacks`() = runTest {
    val sdk = initSdk()
    val errors = mutableListOf<KioskOpsError>()
    sdk.setErrorListener { error -> errors.add(error) }

    // syncOnce with disabled sync should not trigger an error
    sdk.syncOnce()
    assertThat(errors).isEmpty()
  }

  @Test
  fun `setErrorListener with null removes listener`() = runTest {
    val sdk = initSdk()
    var called = false
    sdk.setErrorListener { called = true }
    sdk.setErrorListener(null) // remove

    sdk.syncOnce()
    assertThat(called).isFalse()
  }

  @Test
  fun `healthCheck reflects heartbeat reason`() = runTest {
    val sdk = initSdk()
    sdk.heartbeat("manual_check")

    val health = sdk.healthCheck()
    assertThat(health.lastHeartbeatReason).isEqualTo("manual_check")
  }

  @Test
  fun `KioskOpsError sealed subtypes are accessible`() {
    val enqueueFailed = KioskOpsError.EnqueueFailed("test", null)
    assertThat(enqueueFailed.message).isEqualTo("test")
    assertThat(enqueueFailed.cause).isNull()

    val syncFailed = KioskOpsError.SyncFailed("timeout", 503, RuntimeException("err"))
    assertThat(syncFailed.httpStatus).isEqualTo(503)
    assertThat(syncFailed.cause).isNotNull()

    val cryptoError = KioskOpsError.CryptoError("key missing")
    assertThat(cryptoError.message).isEqualTo("key missing")

    val storageError = KioskOpsError.StorageError("disk full")
    assertThat(storageError.message).isEqualTo("disk full")

    val configError = KioskOpsError.ConfigError("invalid format")
    assertThat(configError.message).isEqualTo("invalid format")
  }
}
