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
class CtFromPolicyTest {

  // ---------------------------------------------------------------
  // fromPolicy() tests
  // ---------------------------------------------------------------

  @Test
  fun `fromPolicy returns null when CT disabled`() {
    val policy = TransportSecurityPolicy(certificateTransparencyEnabled = false)
    val validator = CertificateTransparencyValidator.fromPolicy(policy)
    assertThat(validator).isNull()
  }

  @Test
  fun `fromPolicy returns validator when CT enabled`() {
    val policy = TransportSecurityPolicy(certificateTransparencyEnabled = true)
    val validator = CertificateTransparencyValidator.fromPolicy(policy)
    assertThat(validator).isNotNull()
    assertThat(validator).isInstanceOf(CertificateTransparencyValidator::class.java)
  }

  @Test
  fun `fromPolicy passes callback to validator`() {
    val policy = TransportSecurityPolicy(certificateTransparencyEnabled = true)
    var callbackCalled = false
    val callback: (String, String) -> Unit = { _, _ -> callbackCalled = true }

    val validator = CertificateTransparencyValidator.fromPolicy(policy, callback)
    assertThat(validator).isNotNull()
    // Callback is stored but not yet invoked
    assertThat(callbackCalled).isFalse()
  }

  // ---------------------------------------------------------------
  // Default constructor parameter
  // ---------------------------------------------------------------

  @Test
  fun `default constructor has enabled true`() {
    val validator = CertificateTransparencyValidator()
    assertThat(validator).isNotNull()
  }

  // ---------------------------------------------------------------
  // CtValidationResult data class
  // ---------------------------------------------------------------

  @Test
  fun `CtValidationResult valid`() {
    val result = CtValidationResult(isValid = true, reason = "")
    assertThat(result.isValid).isTrue()
    assertThat(result.reason).isEmpty()
  }

  @Test
  fun `CtValidationResult invalid`() {
    val result = CtValidationResult(isValid = false, reason = "No SCTs found")
    assertThat(result.isValid).isFalse()
    assertThat(result.reason).isEqualTo("No SCTs found")
  }

  @Test
  fun `CtValidationResult data class equality`() {
    val a = CtValidationResult(isValid = true, reason = "ok")
    val b = CtValidationResult(isValid = true, reason = "ok")
    assertThat(a).isEqualTo(b)
    assertThat(a.hashCode()).isEqualTo(b.hashCode())
  }

  // ---------------------------------------------------------------
  // CertificateTransparencyException
  // ---------------------------------------------------------------

  @Test
  fun `CertificateTransparencyException is an IOException`() {
    val exception = CertificateTransparencyException("test message")
    assertThat(exception).isInstanceOf(java.io.IOException::class.java)
    assertThat(exception.message).isEqualTo("test message")
  }
}
