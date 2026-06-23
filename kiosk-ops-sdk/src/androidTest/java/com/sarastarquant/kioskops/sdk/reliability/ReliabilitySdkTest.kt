/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.reliability

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.KioskOpsSdk
import com.sarastarquant.kioskops.sdk.audit.ChainVerificationResult
import com.sarastarquant.kioskops.sdk.queue.EnqueueResult
import java.security.KeyStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before

/**
 * Base for instrumented tests that boot the real SDK on a device/emulator.
 *
 * Each test starts from a clean slate: the SDK singleton, all Room databases,
 * the SQLCipher passphrase store, the SDK's files, and its Keystore aliases are
 * wiped in [cleanSlate]. Without this, switching between an encrypted preset
 * (cuiDefaults) and an unencrypted one against the same files produces spurious
 * "file is not a database" failures rather than real findings.
 *
 * WorkManager is initialized via the test helper because the SDK manifest strips
 * WorkManager's default initializer, so `init()` would otherwise fail when it
 * schedules background work.
 */
abstract class ReliabilitySdkTest {

  protected lateinit var ctx: Context

  @Before
  fun baseSetUp() {
    ctx = ApplicationProvider.getApplicationContext()
    cleanSlate()
    WorkManagerTestInitHelper.initializeTestWorkManager(ctx, Configuration.Builder().build())
  }

  @After
  fun baseTearDown() {
    cleanSlate()
  }

  protected fun cleanSlate() {
    KioskOpsSdk.resetForTesting()
    for (db in DATABASES) ctx.deleteDatabase(db)
    ctx.getSharedPreferences(DB_ENCRYPTION_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    ctx.filesDir.listFiles()?.forEach { if (it.name.startsWith("kioskops")) it.deleteRecursively() }
    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    for (alias in KEYSTORE_ALIASES) {
      if (ks.containsAlias(alias)) ks.deleteEntry(alias)
    }
  }

  /**
   * Boot the SDK with [config] and run the core consumer flow end to end:
   * init -> enqueue -> persist -> heartbeat -> health check -> audit verify.
   * This exercises every runtime subsystem the preset turns on.
   */
  protected fun runPresetFlow(config: KioskOpsConfig) = runBlocking {
    val sdk = KioskOpsSdk.init(ctx, { config })
    try {
      sdk.setDataRightsAuthorizer { _, _ -> true }
      sdk.schemaRegistry.register(EVENT_TYPE, SCAN_SCHEMA)

      val result = sdk.enqueueDetailed(EVENT_TYPE, """{"scan":"ABC-123"}""")
      assertThat(result).isInstanceOf(EnqueueResult.Accepted::class.java)
      assertThat(sdk.queueDepth()).isAtLeast(1L)

      sdk.heartbeat("reliability_test")
      assertThat(sdk.healthCheck().isInitialized).isTrue()

      assertThat(sdk.verifyAuditIntegrity()).isInstanceOf(ChainVerificationResult.Valid::class.java)
    } finally {
      sdk.shutdown()
    }
  }

  companion object {
    const val BASE_URL = "https://example.invalid/"
    const val LOCATION_ID = "TEST-LOC"
    const val EVENT_TYPE = "kiosk_scan"
    const val SCAN_SCHEMA =
      """{"type":"object","required":["scan"],"properties":{"scan":{"type":"string","minLength":1}}}"""

    private const val DB_ENCRYPTION_PREFS = "kioskops_db_encryption"
    private val DATABASES = listOf("kiosk_ops_queue.db", "kioskops_audit.db", "kioskops_config.db")
    private val KEYSTORE_ALIASES = listOf(
      "kioskops_queue_aesgcm_v1",
      "kioskops_telemetry_aesgcm_v1",
      "kioskops_exports_aesgcm_v1",
      "kioskops_db_encryption_v1",
      "kioskops_audit_signing",
    )
  }
}
