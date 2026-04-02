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
import com.sarastarquant.kioskops.sdk.queue.EnqueueResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Integration tests for [KioskOpsSdk] orchestrator.
 *
 * Exercises the enqueue pipeline end-to-end, health observability,
 * heartbeat lifecycle, config reflection, and error listener plumbing.
 */
@RunWith(RobolectricTestRunner::class)
class KioskOpsSdkIntegrationTest {

  private lateinit var ctx: Context

  private val testConfig = KioskOpsConfig(
    baseUrl = "https://example.invalid/",
    locationId = "INT_TEST",
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
    KioskOpsSdk.resetForTesting()
    ctx = ApplicationProvider.getApplicationContext()
    ctx.deleteDatabase("kiosk_ops_queue.db")
    ctx.deleteDatabase("kioskops_audit.db")
    WorkManagerTestInitHelper.initializeTestWorkManager(
      ctx,
      Configuration.Builder()
        .setMinimumLoggingLevel(android.util.Log.DEBUG)
        .build(),
    )
  }

  @After
  fun tearDown() {
    KioskOpsSdk.resetForTesting()
  }

  private fun initSdk(): KioskOpsSdk {
    return KioskOpsSdk.init(
      context = ctx,
      configProvider = { testConfig },
      cryptoProviderOverride = NoopCryptoProvider,
    )
  }

  // -- enqueue pipeline end-to-end ------------------------------------------

  @Test
  fun `enqueue returns true for valid event`() = runTest {
    val sdk = initSdk()
    val result = sdk.enqueue("test.event", """{"key":"value"}""")
    assertThat(result).isTrue()
  }

  @Test
  fun `enqueueDetailed returns Accepted for valid event`() = runTest {
    val sdk = initSdk()
    val result = sdk.enqueueDetailed("test.event", """{"amount":42}""")
    assertThat(result).isInstanceOf(EnqueueResult.Accepted::class.java)
    val accepted = result as EnqueueResult.Accepted
    assertThat(accepted.id).isNotEmpty()
    assertThat(accepted.idempotencyKey).isNotEmpty()
  }

  @Test
  fun `queueDepth reflects enqueued events`() = runTest {
    val sdk = initSdk()
    assertThat(sdk.queueDepth()).isEqualTo(0)

    sdk.enqueue("depth.check", """{"seq":1}""")
    assertThat(sdk.queueDepth()).isEqualTo(1)

    sdk.enqueue("depth.check", """{"seq":2}""")
    assertThat(sdk.queueDepth()).isEqualTo(2)
  }

  // -- heartbeat ------------------------------------------------------------

  @Test
  fun `heartbeat completes without error`() = runTest {
    val sdk = initSdk()
    // Should not throw; verifies the full heartbeat pipeline executes cleanly.
    sdk.heartbeat("integration_test")
  }

  // -- health check ---------------------------------------------------------

  @Test
  fun `healthCheck returns valid HealthCheckResult`() = runTest {
    val sdk = initSdk()
    val health = sdk.healthCheck()

    assertThat(health.isInitialized).isTrue()
    assertThat(health.queueDepth).isEqualTo(0)
    assertThat(health.sdkVersion).isNotEmpty()
    assertThat(health.syncEnabled).isFalse()
    assertThat(health.authProviderConfigured).isFalse()
    assertThat(health.encryptionEnabled).isFalse()
  }

  @Test
  fun `healthCheck queueDepth updates after enqueue`() = runTest {
    val sdk = initSdk()
    sdk.enqueue("health.depth", """{"v":1}""")
    val health = sdk.healthCheck()
    assertThat(health.queueDepth).isEqualTo(1)
  }

  // -- config and policy ----------------------------------------------------

  @Test
  fun `currentConfig returns the configured values`() {
    val sdk = initSdk()
    val cfg = sdk.currentConfig()

    assertThat(cfg.baseUrl).isEqualTo("https://example.invalid/")
    assertThat(cfg.locationId).isEqualTo("INT_TEST")
    assertThat(cfg.kioskEnabled).isFalse()
    assertThat(cfg.securityPolicy.encryptQueuePayloads).isFalse()
    assertThat(cfg.securityPolicy.encryptTelemetryAtRest).isFalse()
    assertThat(cfg.securityPolicy.encryptDiagnosticsBundle).isFalse()
    assertThat(cfg.securityPolicy.encryptExportedLogs).isFalse()
  }

  @Test
  fun `currentPolicyHash returns non-empty string`() {
    val sdk = initSdk()
    val hash = sdk.currentPolicyHash()
    assertThat(hash).isNotEmpty()
  }

  @Test
  fun `currentPolicyHash is deterministic`() {
    val sdk = initSdk()
    val hash1 = sdk.currentPolicyHash()
    val hash2 = sdk.currentPolicyHash()
    assertThat(hash1).isEqualTo(hash2)
  }

  // -- device posture -------------------------------------------------------

  @Test
  fun `devicePosture returns non-null posture`() {
    val sdk = initSdk()
    val posture = sdk.devicePosture()
    assertThat(posture).isNotNull()
    assertThat(posture.deviceModel).isNotNull()
    assertThat(posture.manufacturer).isNotNull()
  }

  // -- error listener -------------------------------------------------------

  @Test
  fun `setErrorListener receives errors on sync failure`() = runTest {
    // Init with sync enabled but no real server; syncOnce will hit a transport error.
    val syncConfig = testConfig.copy(
      syncPolicy = testConfig.syncPolicy.copy(enabled = true),
    )
    val sdk = KioskOpsSdk.init(
      context = ctx,
      configProvider = { syncConfig },
      cryptoProviderOverride = NoopCryptoProvider,
    )

    val errors = mutableListOf<KioskOpsError>()
    sdk.setErrorListener { error -> errors.add(error) }

    // Enqueue something so sync attempts to flush.
    sdk.enqueue("sync.test", """{"data":"x"}""")
    sdk.syncOnce()

    // Transport to example.invalid should fail; listener should receive the error.
    assertThat(errors).isNotEmpty()
    assertThat(errors[0]).isInstanceOf(KioskOpsError.SyncFailed::class.java)
  }

  @Test
  fun `setErrorListener with null stops receiving callbacks`() = runTest {
    val sdk = initSdk()
    var callbackCount = 0
    sdk.setErrorListener { callbackCount++ }
    sdk.setErrorListener(null)

    sdk.syncOnce()
    assertThat(callbackCount).isEqualTo(0)
  }

  // -- quarantined events ---------------------------------------------------

  @Test
  fun `quarantinedEvents returns list`() = runTest {
    val sdk = initSdk()
    val quarantined = sdk.quarantinedEvents()
    assertThat(quarantined).isNotNull()
    assertThat(quarantined).isEmpty()
  }

  // -- enqueue pipeline with multiple events --------------------------------

  @Test
  fun `enqueueDetailed multiple events are all Accepted`() = runTest {
    val sdk = initSdk()
    val types = listOf("order.placed", "order.updated", "order.completed")

    val results = types.map { type ->
      sdk.enqueueDetailed(type, """{"type":"$type"}""")
    }

    results.forEach { result ->
      assertThat(result).isInstanceOf(EnqueueResult.Accepted::class.java)
    }
    assertThat(sdk.queueDepth()).isEqualTo(3)
  }

  @Test
  fun `enqueueDetailed assigns unique idempotency keys`() = runTest {
    val sdk = initSdk()
    val r1 = sdk.enqueueDetailed("unique.test", """{"n":1}""") as EnqueueResult.Accepted
    val r2 = sdk.enqueueDetailed("unique.test", """{"n":2}""") as EnqueueResult.Accepted

    assertThat(r1.idempotencyKey).isNotEqualTo(r2.idempotencyKey)
  }

  // -- heartbeat updates healthCheck ----------------------------------------

  @Test
  fun `heartbeat reason is reflected in healthCheck`() = runTest {
    val sdk = initSdk()

    sdk.heartbeat("boot")
    assertThat(sdk.healthCheck().lastHeartbeatReason).isEqualTo("boot")

    sdk.heartbeat("periodic")
    assertThat(sdk.healthCheck().lastHeartbeatReason).isEqualTo("periodic")
  }
}
