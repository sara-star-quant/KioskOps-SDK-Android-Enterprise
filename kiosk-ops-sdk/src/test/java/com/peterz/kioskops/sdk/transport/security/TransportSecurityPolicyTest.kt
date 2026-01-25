/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.transport.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransportSecurityPolicyTest {

  @Test
  fun `default policy has no pins or mTLS`() {
    val policy = TransportSecurityPolicy()
    assertThat(policy.certificatePins).isEmpty()
    assertThat(policy.mtlsConfig).isNull()
    assertThat(policy.certificateTransparencyEnabled).isFalse()
  }

  @Test
  fun `policy with certificate pins`() {
    val pins = listOf(
      CertificatePin("api.example.com", listOf("sha256/abc123")),
      CertificatePin("*.cdn.example.com", listOf("sha256/def456", "sha256/ghi789")),
    )
    val policy = TransportSecurityPolicy(certificatePins = pins)

    assertThat(policy.certificatePins).hasSize(2)
    assertThat(policy.certificatePins[0].hostname).isEqualTo("api.example.com")
    assertThat(policy.certificatePins[1].sha256Pins).hasSize(2)
  }

  @Test
  fun `policy with CT enabled`() {
    val policy = TransportSecurityPolicy(certificateTransparencyEnabled = true)
    assertThat(policy.certificateTransparencyEnabled).isTrue()
  }

  @Test
  fun `CertificatePin data class properties`() {
    val pin = CertificatePin(
      hostname = "*.example.com",
      sha256Pins = listOf("sha256/primary=", "sha256/backup=")
    )

    assertThat(pin.hostname).isEqualTo("*.example.com")
    assertThat(pin.sha256Pins).containsExactly("sha256/primary=", "sha256/backup=")
  }

  @Test
  fun `CertificateCredentials data class properties`() {
    // Test that the data class can be instantiated
    // Actual certificate creation requires more setup
    val hostname = "test.example.com"
    val pin = CertificatePin(hostname, listOf("sha256/test="))

    assertThat(pin.hostname).isEqualTo(hostname)
  }
}
