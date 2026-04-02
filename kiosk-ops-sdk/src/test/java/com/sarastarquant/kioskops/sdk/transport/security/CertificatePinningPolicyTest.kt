/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.transport.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CertificatePinningPolicyTest {

  // ---------------------------------------------------------------------------
  // fromPolicy() -- companion factory
  // ---------------------------------------------------------------------------

  @Test
  fun `fromPolicy returns null when no pins configured`() {
    val policy = TransportSecurityPolicy(certificatePins = emptyList())
    val interceptor = CertificatePinningInterceptor.fromPolicy(policy)
    assertThat(interceptor).isNull()
  }

  @Test
  fun `fromPolicy returns interceptor when pins configured`() {
    val policy = TransportSecurityPolicy(
      certificatePins = listOf(
        CertificatePin("example.com", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val interceptor = CertificatePinningInterceptor.fromPolicy(policy)
    assertThat(interceptor).isNotNull()
  }

  @Test
  fun `fromPolicy passes callback to interceptor`() {
    var callbackHost: String? = null
    val policy = TransportSecurityPolicy(
      certificatePins = listOf(
        CertificatePin("example.com", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val interceptor = CertificatePinningInterceptor.fromPolicy(policy) { host, _ ->
      callbackHost = host
    }
    assertThat(interceptor).isNotNull()
    // callback is not invoked until a request is processed
    assertThat(callbackHost).isNull()
  }

  @Test
  fun `fromPolicy with null callback creates interceptor without callback`() {
    val policy = TransportSecurityPolicy(
      certificatePins = listOf(
        CertificatePin("example.com", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val interceptor = CertificatePinningInterceptor.fromPolicy(policy, onPinValidationFailure = null)
    assertThat(interceptor).isNotNull()
  }

  // ---------------------------------------------------------------------------
  // buildPinner() -- sha256 prefix normalization (tested indirectly)
  // ---------------------------------------------------------------------------

  @Test
  fun `interceptor handles pins with sha256 prefix`() {
    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin(
          hostname = "api.example.com",
          sha256Pins = listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        )
      )
    )
    assertThat(interceptor).isNotNull()
  }

  @Test
  fun `interceptor handles pins without sha256 prefix`() {
    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin(
          hostname = "api.example.com",
          sha256Pins = listOf("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        )
      )
    )
    assertThat(interceptor).isNotNull()
  }

  @Test
  fun `interceptor handles multiple pins per hostname`() {
    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin(
          hostname = "api.example.com",
          sha256Pins = listOf(
            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
          )
        )
      )
    )
    assertThat(interceptor).isNotNull()
  }

  @Test
  fun `interceptor handles multiple hostnames`() {
    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin(
          hostname = "api.example.com",
          sha256Pins = listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        ),
        CertificatePin(
          hostname = "cdn.example.com",
          sha256Pins = listOf("sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")
        ),
      )
    )
    assertThat(interceptor).isNotNull()
  }

  // ---------------------------------------------------------------------------
  // fromPolicy -- edge cases
  // ---------------------------------------------------------------------------

  @Test
  fun `fromPolicy with single pin single host`() {
    val policy = TransportSecurityPolicy(
      certificatePins = listOf(
        CertificatePin("api.example.com", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val interceptor = CertificatePinningInterceptor.fromPolicy(policy)
    assertThat(interceptor).isNotNull()
  }

  @Test
  fun `fromPolicy with wildcard hostname`() {
    val policy = TransportSecurityPolicy(
      certificatePins = listOf(
        CertificatePin("*.example.com", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val interceptor = CertificatePinningInterceptor.fromPolicy(policy)
    assertThat(interceptor).isNotNull()
  }

  @Test
  fun `fromPolicy with multiple hosts and multiple pins`() {
    val policy = TransportSecurityPolicy(
      certificatePins = listOf(
        CertificatePin(
          "api.example.com",
          listOf(
            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
          )
        ),
        CertificatePin(
          "*.cdn.example.com",
          listOf("CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=")
        ),
      )
    )
    val interceptor = CertificatePinningInterceptor.fromPolicy(policy)
    assertThat(interceptor).isNotNull()
  }

  // ---------------------------------------------------------------------------
  // Constructor direct usage
  // ---------------------------------------------------------------------------

  @Test
  fun `constructor accepts empty pins list`() {
    val interceptor = CertificatePinningInterceptor(pins = emptyList())
    assertThat(interceptor).isNotNull()
  }
}
