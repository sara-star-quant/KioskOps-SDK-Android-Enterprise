/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.pipeline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.KioskOpsSdk
import com.sarastarquant.kioskops.sdk.anomaly.AnomalyPolicy
import com.sarastarquant.kioskops.sdk.compliance.IdempotencyConfig
import com.sarastarquant.kioskops.sdk.compliance.QueueLimits
import com.sarastarquant.kioskops.sdk.compliance.SecurityPolicy
import com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider
import com.sarastarquant.kioskops.sdk.pii.DataClassificationPolicy
import com.sarastarquant.kioskops.sdk.pii.PiiPolicy
import com.sarastarquant.kioskops.sdk.util.InstallSecret
import com.sarastarquant.kioskops.sdk.validation.ValidationPolicy
import org.junit.After
import org.junit.Before

abstract class PipelineTestBase {

  protected lateinit var ctx: Context

  protected val noEncryptionSecurity: SecurityPolicy = SecurityPolicy.maximalistDefaults().copy(
    encryptQueuePayloads = false,
    encryptTelemetryAtRest = false,
    encryptDiagnosticsBundle = false,
    encryptExportedLogs = false,
  )

  @Before
  open fun setUp() {
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
    // Robolectric has no AndroidKeyStore; inject a fixed secret so deterministic
    // idempotency tests can run without Keystore.
    InstallSecret.testSecretOverride = ByteArray(32) { it.toByte() }
  }

  @After
  open fun tearDown() {
    KioskOpsSdk.resetForTesting()
    InstallSecret.testSecretOverride = null
  }

  protected fun initSdk(config: KioskOpsConfig): KioskOpsSdk {
    return KioskOpsSdk.init(
      context = ctx,
      configProvider = { config },
      cryptoProviderOverride = NoopCryptoProvider,
    )
  }

  protected fun baseConfig(
    validationPolicy: ValidationPolicy = ValidationPolicy.disabledDefaults(),
    piiPolicy: PiiPolicy = PiiPolicy.disabledDefaults(),
    anomalyPolicy: AnomalyPolicy = AnomalyPolicy.disabledDefaults(),
    securityPolicy: SecurityPolicy = noEncryptionSecurity,
    queueLimits: QueueLimits = QueueLimits.maximalistDefaults(),
    idempotencyConfig: IdempotencyConfig = IdempotencyConfig.maximalistDefaults(),
    dataClassificationPolicy: DataClassificationPolicy = DataClassificationPolicy.disabledDefaults(),
  ) = KioskOpsConfig(
    baseUrl = "https://test.invalid/",
    locationId = "REJECTION_TEST",
    kioskEnabled = false,
    securityPolicy = securityPolicy,
    validationPolicy = validationPolicy,
    piiPolicy = piiPolicy,
    anomalyPolicy = anomalyPolicy,
    queueLimits = queueLimits,
    idempotencyConfig = idempotencyConfig,
    dataClassificationPolicy = dataClassificationPolicy,
  )
}
