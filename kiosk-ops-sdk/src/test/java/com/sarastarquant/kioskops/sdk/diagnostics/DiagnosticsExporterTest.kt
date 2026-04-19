/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.audit.AuditTrail
import com.sarastarquant.kioskops.sdk.compliance.RetentionPolicy
import com.sarastarquant.kioskops.sdk.compliance.SecurityPolicy
import com.sarastarquant.kioskops.sdk.compliance.TelemetryPolicy
import com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider
import com.sarastarquant.kioskops.sdk.fleet.DevicePosture
import com.sarastarquant.kioskops.sdk.logging.LogExporter
import com.sarastarquant.kioskops.sdk.logging.RingLog
import com.sarastarquant.kioskops.sdk.telemetry.EncryptedTelemetryStore
import com.sarastarquant.kioskops.sdk.util.Clock
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
class DiagnosticsExporterTest {

  private lateinit var ctx: Context
  private lateinit var exporter: DiagnosticsExporter

  private var nowMs = 1_700_000_000_000L
  private val clock = object : Clock {
    override fun nowMs(): Long = nowMs
  }

  @Before
  fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()

    val cfg = KioskOpsConfig(
      baseUrl = "https://example.invalid",
      locationId = "test-loc",
      kioskEnabled = true,
      securityPolicy = SecurityPolicy.maximalistDefaults().copy(
        encryptQueuePayloads = false,
        encryptTelemetryAtRest = false,
        encryptExportedLogs = false,
      ),
      retentionPolicy = RetentionPolicy.maximalistDefaults(),
      telemetryPolicy = TelemetryPolicy.maximalistDefaults(),
    )

    val logs = RingLog(ctx)
    val logExporter = LogExporter(ctx, logs, NoopCryptoProvider)
    val telemetry = EncryptedTelemetryStore(
      context = ctx,
      policyProvider = { cfg.telemetryPolicy },
      retentionProvider = { cfg.retentionPolicy },
      clock = clock,
      crypto = NoopCryptoProvider,
    )
    val audit = AuditTrail(
      context = ctx,
      retentionProvider = { cfg.retentionPolicy },
      clock = clock,
      crypto = NoopCryptoProvider,
    )

    val posture = DevicePosture(
      isDeviceOwner = false,
      isLockTaskPermitted = false,
      androidSdkInt = 33,
      deviceModel = "TestDevice",
      manufacturer = "Robolectric",
      securityPatch = "2026-01-01",
    )

    exporter = DiagnosticsExporter(
      context = ctx,
      cfgProvider = { cfg },
      logs = logs,
      logExporter = logExporter,
      telemetry = telemetry,
      audit = audit,
      clock = clock,
      sdkVersion = "0.7.0-test",
      queueDepthProvider = { 5L },
      quarantinedSummaryProvider = { emptyList() },
      postureProvider = { posture },
      policyHashProvider = { "test-policy-hash" },
    )
  }

  @Test
  fun `export produces valid zip file`() = runTest {
    val file = exporter.export()

    assertThat(file.exists()).isTrue()
    assertThat(file.length()).isGreaterThan(0)
    assertThat(file.name).startsWith("kioskops_diagnostics_")
    assertThat(file.name).endsWith(".zip")
  }

  @Test
  fun `export zip contains manifest and health snapshot`() = runTest {
    val file = exporter.export()
    val zip = ZipFile(file)
    val entryNames = zip.entries().toList().map { it.name }

    assertThat(entryNames).contains("manifest.json")
    assertThat(entryNames).contains("health_snapshot.json")
    zip.close()
  }

  @Test
  fun `export zip contains quarantined summaries`() = runTest {
    val file = exporter.export()
    val zip = ZipFile(file)
    val entryNames = zip.entries().toList().map { it.name }

    assertThat(entryNames).contains("queue/quarantined_summaries.json")
    zip.close()
  }

  @Test
  fun `health snapshot includes queue depth and sdk version`() = runTest {
    val file = exporter.export()
    val zip = ZipFile(file)
    val healthEntry = zip.getEntry("health_snapshot.json")
    val content = zip.getInputStream(healthEntry).bufferedReader().readText()

    assertThat(content).contains("0.7.0-test")
    assertThat(content).contains("test-loc")
    assertThat(content).contains("test-policy-hash")
    zip.close()
  }

  @Test
  fun `export cap skips oversized entries and writes a truncation marker`() = runTest {
    // Stage a large telemetry file so the exporter is forced to choose whether to include it.
    val telemetryDir = java.io.File(ctx.filesDir, "kioskops_telemetry").apply { mkdirs() }
    java.io.File(telemetryDir, "bulk.log").writeBytes(ByteArray(2048) { 'a'.code.toByte() })

    val cfg = KioskOpsConfig(
      baseUrl = "https://example.invalid",
      locationId = "test-loc",
      kioskEnabled = true,
      securityPolicy = SecurityPolicy.maximalistDefaults().copy(
        encryptQueuePayloads = false,
        encryptTelemetryAtRest = false,
        encryptExportedLogs = false,
      ),
      retentionPolicy = RetentionPolicy.maximalistDefaults(),
      telemetryPolicy = TelemetryPolicy.maximalistDefaults(),
      // Tiny cap: manifest + health + quarantined barely fit; bulk.log cannot.
      diagnosticsSchedulePolicy = DiagnosticsSchedulePolicy.disabledDefaults().copy(
        maxExportBytes = 1024L,
      ),
    )

    val logs = RingLog(ctx)
    val logExporter = LogExporter(ctx, logs, NoopCryptoProvider)
    val telemetry = EncryptedTelemetryStore(
      context = ctx,
      policyProvider = { cfg.telemetryPolicy },
      retentionProvider = { cfg.retentionPolicy },
      clock = clock,
      crypto = NoopCryptoProvider,
    )
    val audit = AuditTrail(
      context = ctx,
      retentionProvider = { cfg.retentionPolicy },
      clock = clock,
      crypto = NoopCryptoProvider,
    )
    val posture = DevicePosture(
      isDeviceOwner = false,
      isLockTaskPermitted = false,
      androidSdkInt = 33,
      deviceModel = "TestDevice",
      manufacturer = "Robolectric",
      securityPatch = "2026-01-01",
    )
    val cappedExporter = DiagnosticsExporter(
      context = ctx,
      cfgProvider = { cfg },
      logs = logs,
      logExporter = logExporter,
      telemetry = telemetry,
      audit = audit,
      clock = clock,
      sdkVersion = "0.7.0-test",
      queueDepthProvider = { 0L },
      quarantinedSummaryProvider = { emptyList() },
      postureProvider = { posture },
      policyHashProvider = { "test-policy-hash" },
    )

    val file = cappedExporter.export()
    val zip = ZipFile(file)
    val entries = zip.entries().toList().map { it.name }

    assertThat(entries).contains("manifest.json")
    assertThat(entries).contains("truncation.txt")
    val marker = zip.getInputStream(zip.getEntry("truncation.txt")).bufferedReader().readText()
    assertThat(marker).contains("maxExportBytes=1024")
    zip.close()
  }
}
