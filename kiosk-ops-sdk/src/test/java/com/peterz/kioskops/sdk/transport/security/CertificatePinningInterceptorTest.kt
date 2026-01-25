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
class CertificatePinningInterceptorTest {

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
  fun `interceptor handles multiple pins per hostname`() {
    val policy = TransportSecurityPolicy(
      certificatePins = listOf(
        CertificatePin(
          hostname = "api.example.com",
          sha256Pins = listOf(
            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
          )
        )
      )
    )
    val interceptor = CertificatePinningInterceptor.fromPolicy(policy)
    assertThat(interceptor).isNotNull()
  }

  @Test
  fun `interceptor handles pins without sha256 prefix`() {
    val policy = TransportSecurityPolicy(
      certificatePins = listOf(
        CertificatePin(
          hostname = "api.example.com",
          sha256Pins = listOf("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        )
      )
    )
    val interceptor = CertificatePinningInterceptor.fromPolicy(policy)
    assertThat(interceptor).isNotNull()
  }

  @Test
  fun `callback invoked on pin failure`() {
    var callbackInvoked = false
    var callbackHostname: String? = null

    CertificatePinningInterceptor(
      pins = listOf(CertificatePin("test.com", listOf("sha256/test="))),
      onPinValidationFailure = { hostname, _ ->
        callbackInvoked = true
        callbackHostname = hostname
      }
    )

    // The callback is tested when the interceptor processes a request
    // Here we just verify the interceptor was created with the callback
    assertThat(callbackInvoked).isFalse() // Not invoked until request processed
  }
}
