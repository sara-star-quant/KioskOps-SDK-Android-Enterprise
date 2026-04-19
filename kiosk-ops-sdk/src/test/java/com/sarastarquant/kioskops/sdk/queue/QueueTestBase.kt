/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.queue

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.compliance.IdempotencyConfig
import com.sarastarquant.kioskops.sdk.compliance.QueueLimits
import com.sarastarquant.kioskops.sdk.compliance.SecurityPolicy
import com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider
import com.sarastarquant.kioskops.sdk.logging.RingLog
import com.sarastarquant.kioskops.sdk.util.InstallSecret
import org.junit.After
import org.junit.Before

abstract class QueueTestBase {

  protected lateinit var ctx: Context

  protected val baseCfg = KioskOpsConfig(
    baseUrl = "https://example.invalid/",
    locationId = "LOC-1",
    kioskEnabled = false,
    securityPolicy = SecurityPolicy.maximalistDefaults().copy(
      encryptQueuePayloads = false,
      maxEventPayloadBytes = 64 * 1024,
    ),
    queueLimits = QueueLimits.maximalistDefaults(),
    idempotencyConfig = IdempotencyConfig.maximalistDefaults(),
  )

  @Before
  open fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()
    ctx.deleteDatabase("kiosk_ops_queue.db")
    // InstallSecret wraps its secret with AndroidKeyStore in production. Robolectric has
    // no Keystore provider, so tests that exercise deterministic idempotency inject a
    // fixed secret via the @VisibleForTesting override. Fixed-in-tests is fine; tests
    // only need reproducibility, not secrecy.
    InstallSecret.testSecretOverride = ByteArray(32) { it.toByte() }
  }

  @After
  open fun tearDown() {
    InstallSecret.testSecretOverride = null
  }

  protected fun newRepo(): QueueRepository =
    QueueRepository(ctx, RingLog(ctx), NoopCryptoProvider)
}
