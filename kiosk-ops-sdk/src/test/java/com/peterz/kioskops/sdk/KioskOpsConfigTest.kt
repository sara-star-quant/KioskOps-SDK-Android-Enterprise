/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk

import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.compliance.SecurityPolicy
import com.peterz.kioskops.sdk.transport.security.CertificatePin
import com.peterz.kioskops.sdk.transport.security.TransportSecurityPolicy
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
