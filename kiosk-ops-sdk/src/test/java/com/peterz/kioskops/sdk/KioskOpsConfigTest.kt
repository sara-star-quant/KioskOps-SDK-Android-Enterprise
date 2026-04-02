/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk

import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.compliance.SecurityPolicy
import com.peterz.kioskops.sdk.pii.DataClassification
import com.peterz.kioskops.sdk.pii.PiiAction
import com.peterz.kioskops.sdk.transport.security.CertificatePin
import com.peterz.kioskops.sdk.transport.security.TransportSecurityPolicy
import com.peterz.kioskops.sdk.validation.UnknownEventTypeAction
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KioskOpsConfigTest {

  @Test
  fun `default config has empty transport security policy`() {
    val config = KioskOpsConfig(
      baseUrl = "https://api.example.com/",
      locationId = "STORE-001",
      kioskEnabled = true,
    )

    assertThat(config.transportSecurityPolicy).isNotNull()
    assertThat(config.transportSecurityPolicy.certificatePins).isEmpty()
    assertThat(config.transportSecurityPolicy.mtlsConfig).isNull()
    assertThat(config.transportSecurityPolicy.certificateTransparencyEnabled).isFalse()
  }

  @Test
  fun `config with certificate pinning`() {
    val pins = listOf(
      CertificatePin("api.example.com", listOf("sha256/abc123")),
    )
    val config = KioskOpsConfig(
      baseUrl = "https://api.example.com/",
      locationId = "STORE-001",
      kioskEnabled = true,
      transportSecurityPolicy = TransportSecurityPolicy(
        certificatePins = pins,
      ),
    )

    assertThat(config.transportSecurityPolicy.certificatePins).hasSize(1)
    assertThat(config.transportSecurityPolicy.certificatePins[0].hostname).isEqualTo("api.example.com")
  }

  @Test
  fun `config with CT enabled`() {
    val config = KioskOpsConfig(
      baseUrl = "https://api.example.com/",
      locationId = "STORE-001",
      kioskEnabled = true,
      transportSecurityPolicy = TransportSecurityPolicy(
        certificateTransparencyEnabled = true,
      ),
    )

    assertThat(config.transportSecurityPolicy.certificateTransparencyEnabled).isTrue()
  }

  @Test
  fun `config with high security policy`() {
    val config = KioskOpsConfig(
      baseUrl = "https://api.example.com/",
      locationId = "STORE-001",
      kioskEnabled = true,
      securityPolicy = SecurityPolicy.highSecurityDefaults(),
    )

    assertThat(config.securityPolicy.signAuditEntries).isTrue()
    assertThat(config.securityPolicy.keyRotationPolicy.autoRotateEnabled).isTrue()
  }

  // ========================================================================
  // Compliance preset tests
  // ========================================================================

  @Test
  fun `fedRampDefaults enables high security and strict validation`() {
    val config = KioskOpsConfig.fedRampDefaults("https://api.example.com/", "FED-001")

    assertThat(config.securityPolicy.signAuditEntries).isTrue()
    assertThat(config.securityPolicy.encryptQueuePayloads).isTrue()
    assertThat(config.validationPolicy.strictMode).isTrue()
    assertThat(config.piiPolicy.action).isEqualTo(PiiAction.REJECT)
    assertThat(config.fieldEncryptionPolicy.enabled).isTrue()
    assertThat(config.anomalyPolicy.enabled).isTrue()
    assertThat(config.retentionPolicy.retainAuditDays).isEqualTo(365)
    assertThat(config.retentionPolicy.minimumAuditRetentionDays).isEqualTo(365)
  }

  @Test
  fun `gdprDefaults uses redaction not rejection`() {
    val config = KioskOpsConfig.gdprDefaults("https://api.example.com/", "EU-001")

    assertThat(config.piiPolicy.action).isEqualTo(PiiAction.REDACT_VALUE)
    assertThat(config.validationPolicy.strictMode).isFalse()
    assertThat(config.fieldEncryptionPolicy.enabled).isTrue()
    assertThat(config.dataClassificationPolicy.enabled).isTrue()
  }

  @Test
  fun `cuiDefaults enforces NIST SP 800-171 controls`() {
    val config = KioskOpsConfig.cuiDefaults("https://api.example.com/", "CUI-001")

    assertThat(config.securityPolicy.signAuditEntries).isTrue()
    assertThat(config.securityPolicy.encryptQueuePayloads).isTrue()
    assertThat(config.securityPolicy.encryptTelemetryAtRest).isTrue()
    assertThat(config.securityPolicy.keyRotationPolicy.autoRotateEnabled).isTrue()
    assertThat(config.retentionPolicy.retainAuditDays).isEqualTo(365)
    assertThat(config.retentionPolicy.minimumAuditRetentionDays).isEqualTo(365)
    assertThat(config.validationPolicy.strictMode).isTrue()
    assertThat(config.validationPolicy.unknownEventTypeAction).isEqualTo(UnknownEventTypeAction.REJECT)
    assertThat(config.piiPolicy.action).isEqualTo(PiiAction.REJECT)
    assertThat(config.fieldEncryptionPolicy.enabled).isTrue()
    assertThat(config.dataClassificationPolicy.defaultClassification).isEqualTo(DataClassification.CONFIDENTIAL)
    assertThat(config.anomalyPolicy.enabled).isTrue()
  }

  @Test
  fun `cjisDefaults enforces CJIS Security Policy controls`() {
    val config = KioskOpsConfig.cjisDefaults("https://api.example.com/", "CJIS-001")

    assertThat(config.securityPolicy.signAuditEntries).isTrue()
    assertThat(config.securityPolicy.encryptQueuePayloads).isTrue()
    assertThat(config.retentionPolicy.retainAuditDays).isEqualTo(365)
    assertThat(config.validationPolicy.strictMode).isTrue()
    assertThat(config.piiPolicy.action).isEqualTo(PiiAction.REJECT)
    assertThat(config.dataClassificationPolicy.defaultClassification).isEqualTo(DataClassification.CONFIDENTIAL)
    assertThat(config.anomalyPolicy.enabled).isTrue()
  }

  @Test
  fun `asdEssentialEightDefaults uses redaction for Australian Privacy Act`() {
    val config = KioskOpsConfig.asdEssentialEightDefaults("https://api.example.com/", "ASD-001")

    assertThat(config.securityPolicy.signAuditEntries).isTrue()
    assertThat(config.securityPolicy.encryptQueuePayloads).isTrue()
    assertThat(config.retentionPolicy.retainAuditDays).isEqualTo(365)
    assertThat(config.validationPolicy.strictMode).isTrue()
    assertThat(config.piiPolicy.action).isEqualTo(PiiAction.REDACT_VALUE)
    assertThat(config.fieldEncryptionPolicy.enabled).isTrue()
    assertThat(config.dataClassificationPolicy.enabled).isTrue()
    assertThat(config.anomalyPolicy.enabled).isTrue()
  }

  @Test
  fun `compliance presets are customizable via copy`() {
    val config = KioskOpsConfig.cuiDefaults("https://api.example.com/", "CUI-001")
      .copy(syncIntervalMinutes = 5L, adminExitPin = "9999")

    assertThat(config.syncIntervalMinutes).isEqualTo(5L)
    assertThat(config.adminExitPin).isEqualTo("9999")
    // Preset values preserved
    assertThat(config.securityPolicy.signAuditEntries).isTrue()
    assertThat(config.piiPolicy.action).isEqualTo(PiiAction.REJECT)
  }

  @Test
  fun `config preserves all standard fields`() {
    val config = KioskOpsConfig(
      baseUrl = "https://api.example.com/",
      locationId = "STORE-001",
      kioskEnabled = true,
      syncIntervalMinutes = 30L,
      adminExitPin = "1234",
    )

    assertThat(config.baseUrl).isEqualTo("https://api.example.com/")
    assertThat(config.locationId).isEqualTo("STORE-001")
    assertThat(config.kioskEnabled).isTrue()
    assertThat(config.syncIntervalMinutes).isEqualTo(30L)
    assertThat(config.adminExitPin).isEqualTo("1234")
  }
}
