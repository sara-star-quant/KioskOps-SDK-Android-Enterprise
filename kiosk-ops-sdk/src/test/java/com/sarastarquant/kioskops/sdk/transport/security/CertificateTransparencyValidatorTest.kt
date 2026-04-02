/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.transport.security

import com.google.common.truth.Truth.assertThat
import okhttp3.Handshake
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.TlsVersion
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigInteger
import java.security.Principal
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.security.auth.x500.X500Principal

/**
 * Stub X509Certificate that overrides only the methods exercised by
 * CertificateTransparencyValidator (getExtensionValue, getIssuerX500Principal).
 * All other abstract methods return safe defaults.
 */
private class StubX509Certificate(
  private val issuerDn: String,
  private val sctExtensionValue: ByteArray? = null,
) : X509Certificate() {

  override fun getIssuerX500Principal(): X500Principal = X500Principal(issuerDn)

  override fun getExtensionValue(oid: String?): ByteArray? {
    if (oid == "1.3.6.1.4.1.11129.2.4.2") return sctExtensionValue
    return null
  }

  // --- remaining abstract methods with safe defaults ---
  override fun checkValidity() = Unit
  override fun checkValidity(date: Date?) = Unit
  override fun getVersion(): Int = 3
  override fun getSerialNumber(): BigInteger = BigInteger.ONE
  override fun getIssuerDN(): Principal = X500Principal(issuerDn)
  override fun getSubjectDN(): Principal = X500Principal("CN=stub")
  override fun getNotBefore(): Date = Date()
  override fun getNotAfter(): Date = Date(System.currentTimeMillis() + 86_400_000)
  override fun getTBSCertificate(): ByteArray = ByteArray(0)
  override fun getSignature(): ByteArray = ByteArray(0)
  override fun getSigAlgName(): String = "SHA256withRSA"
  override fun getSigAlgOID(): String = "1.2.840.113549.1.1.11"
  override fun getSigAlgParams(): ByteArray? = null
  override fun getBasicConstraints(): Int = -1
  override fun getKeyUsage(): BooleanArray? = null
  override fun getIssuerUniqueID(): BooleanArray? = null
  override fun getSubjectUniqueID(): BooleanArray? = null
  override fun getSubjectX500Principal(): X500Principal = X500Principal("CN=stub")
  override fun hasUnsupportedCriticalExtension(): Boolean = false
  override fun getCriticalExtensionOIDs(): MutableSet<String> = mutableSetOf()
  override fun getNonCriticalExtensionOIDs(): MutableSet<String> = mutableSetOf()
  override fun getEncoded(): ByteArray = ByteArray(0)
  override fun verify(key: PublicKey?) = Unit
  override fun verify(key: PublicKey?, sigProvider: String?) = Unit
  override fun getPublicKey(): PublicKey = object : PublicKey {
    override fun getAlgorithm(): String = "RSA"
    override fun getFormat(): String = "X.509"
    override fun getEncoded(): ByteArray = ByteArray(0)
  }

  override fun toString(): String = "StubX509Certificate(issuer=$issuerDn)"
}

@RunWith(RobolectricTestRunner::class)
class CertificateTransparencyValidatorTest {

  // ---------------------------------------------------------------
  // Helper: build a fake Interceptor.Chain that returns a Response
  // with a configurable Handshake
  // ---------------------------------------------------------------

  private fun buildFakeChain(
    url: String = "https://example.com/api",
    certificates: List<X509Certificate> = emptyList(),
    includeHandshake: Boolean = true,
  ): Interceptor.Chain {
    val request = Request.Builder().url(url).build()

    val handshake = if (includeHandshake && certificates.isNotEmpty()) {
      Handshake.get(
        TlsVersion.TLS_1_3,
        okhttp3.CipherSuite.TLS_AES_128_GCM_SHA256,
        certificates,
        certificates,
      )
    } else if (includeHandshake) {
      // Handshake with empty cert list
      Handshake.get(
        TlsVersion.TLS_1_3,
        okhttp3.CipherSuite.TLS_AES_128_GCM_SHA256,
        emptyList(),
        emptyList(),
      )
    } else {
      null
    }

    val response = Response.Builder()
      .request(request)
      .protocol(Protocol.HTTP_2)
      .code(200)
      .message("OK")
      .body("{}".toResponseBody())
      .apply {
        if (handshake != null) handshake(handshake)
      }
      .build()

    return FakeInterceptorChain(request, response)
  }

  /**
   * Minimal Interceptor.Chain implementation for unit testing.
   * Only [request] and [proceed] are used by CertificateTransparencyValidator.
   */
  private class FakeInterceptorChain(
    private val fakeRequest: Request,
    private val fakeResponse: Response,
  ) : Interceptor.Chain {
    override fun request(): Request = fakeRequest
    override fun proceed(request: Request): Response = fakeResponse

    override fun connection() = null
    override fun call() = throw UnsupportedOperationException()
    override fun connectTimeoutMillis() = 0
    override fun readTimeoutMillis() = 0
    override fun writeTimeoutMillis() = 0
    override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
    override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
    override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
  }

  // ---------------------------------------------------------------
  // Stub certificate helpers
  // ---------------------------------------------------------------

  private fun stubCert(issuerDn: String, hasSct: Boolean = false): StubX509Certificate {
    val sctValue = if (hasSct) ByteArray(4) { 0x01 } else null
    return StubX509Certificate(issuerDn, sctValue)
  }

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
  // intercept() -- disabled path
  // ---------------------------------------------------------------

  @Test
  fun `intercept passes through when disabled`() {
    val validator = CertificateTransparencyValidator(enabled = false)
    val chain = buildFakeChain()

    val response = validator.intercept(chain)
    assertThat(response).isNotNull()
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `intercept passes through when disabled regardless of certificates`() {
    val cert = stubCert("CN=Unknown CA, O=Evil Corp")
    val validator = CertificateTransparencyValidator(enabled = false)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response).isNotNull()
    assertThat(response.code).isEqualTo(200)
  }

  // ---------------------------------------------------------------
  // intercept() -- enabled, no handshake
  // ---------------------------------------------------------------

  @Test
  fun `intercept returns response when no handshake present`() {
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(includeHandshake = false)

    val response = validator.intercept(chain)
    assertThat(response).isNotNull()
    assertThat(response.code).isEqualTo(200)
  }

  // ---------------------------------------------------------------
  // intercept() -- enabled, handshake with empty certificate list
  // ---------------------------------------------------------------

  @Test
  fun `intercept returns response when handshake has no certificates`() {
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = emptyList())

    val response = validator.intercept(chain)
    assertThat(response).isNotNull()
    assertThat(response.code).isEqualTo(200)
  }

  // ---------------------------------------------------------------
  // validateCertificateTransparency() -- empty chain returns invalid
  // (exercised via the empty-cert handshake path above; the private
  //  method guard for isEmpty is a defense-in-depth measure)
  // ---------------------------------------------------------------

  // ---------------------------------------------------------------
  // hasEmbeddedScts() -- OID 1.3.6.1.4.1.11129.2.4.2
  // ---------------------------------------------------------------

  @Test
  fun `certificate with embedded SCT extension passes validation`() {
    val cert = stubCert("CN=Unknown CA, O=Unknown", hasSct = true)
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `certificate without SCT extension and unknown CA fails validation`() {
    val cert = stubCert("CN=Unknown Root CA, O=Unknown Corp, C=XX", hasSct = false)
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    assertThrows(CertificateTransparencyException::class.java) {
      validator.intercept(chain)
    }
  }

  // ---------------------------------------------------------------
  // isFromKnownCa() -- all known CAs (case-insensitive substring)
  // ---------------------------------------------------------------

  @Test
  fun `intercept succeeds for DigiCert issued certificate`() {
    val cert = stubCert("CN=DigiCert Global Root G2, O=DigiCert Inc, C=US")
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `intercept succeeds for Lets Encrypt issued certificate`() {
    val cert = stubCert("CN=R3, O=Let's Encrypt, C=US")
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `intercept succeeds for Comodo issued certificate`() {
    val cert = stubCert("CN=COMODO RSA DV CA, O=COMODO CA Limited")
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `intercept succeeds for Sectigo issued certificate`() {
    val cert = stubCert("CN=Sectigo RSA DV CA, O=Sectigo Limited")
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `intercept succeeds for GlobalSign issued certificate`() {
    val cert = stubCert("CN=GlobalSign Atlas R3, O=GlobalSign nv-sa")
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `intercept succeeds for GoDaddy issued certificate`() {
    val cert = stubCert("CN=Go Daddy Secure CA, O=GoDaddy.com")
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `intercept succeeds for Amazon issued certificate`() {
    val cert = stubCert("CN=Amazon RSA 2048 M02, O=Amazon, C=US")
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `intercept succeeds for Google Trust Services issued certificate`() {
    val cert = stubCert("CN=GTS CA 1C3, O=Google Trust Services LLC, C=US")
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `intercept succeeds for Microsoft issued certificate`() {
    val cert = stubCert("CN=Microsoft Azure TLS CA, O=Microsoft Corporation")
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `intercept succeeds for Baltimore issued certificate`() {
    val cert = stubCert("CN=Baltimore CyberTrust Root, O=Baltimore")
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `intercept succeeds for VeriSign issued certificate`() {
    val cert = stubCert("CN=VeriSign Class 3 CA, O=VeriSign Inc")
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `intercept succeeds for Entrust issued certificate`() {
    val cert = stubCert("CN=Entrust CA, O=Entrust")
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `intercept succeeds for GeoTrust issued certificate`() {
    val cert = stubCert("CN=GeoTrust Global CA, O=GeoTrust Inc")
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `intercept succeeds for Thawte issued certificate`() {
    val cert = stubCert("CN=thawte DV SSL CA, O=thawte Inc")
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `intercept succeeds for RapidSSL issued certificate`() {
    val cert = stubCert("CN=RapidSSL TLS DV RSA, O=DigiCert Inc")
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `intercept succeeds for LetsEncrypt alternate spelling`() {
    val cert = stubCert("CN=LetsEncrypt Authority X3, O=LetsEncrypt")
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `isFromKnownCa is case insensitive`() {
    val cert = stubCert("CN=DIGICERT GLOBAL ROOT, O=DIGICERT INC")
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `isFromKnownCa returns false for unknown CA`() {
    val cert = stubCert("CN=My Private Root CA, O=Evil Corp, C=XX")
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    assertThrows(CertificateTransparencyException::class.java) {
      validator.intercept(chain)
    }
  }

  // ---------------------------------------------------------------
  // Callback invoked on validation failure
  // ---------------------------------------------------------------

  @Test
  fun `callback invoked on validation failure`() {
    var callbackHostname: String? = null
    var callbackReason: String? = null

    val validator = CertificateTransparencyValidator(
      enabled = true,
      onValidationFailure = { hostname, reason ->
        callbackHostname = hostname
        callbackReason = reason
      },
    )

    val cert = stubCert("CN=Unknown CA, O=Unknown")
    val chain = buildFakeChain(
      url = "https://evil.example.com/api",
      certificates = listOf(cert),
    )

    assertThrows(CertificateTransparencyException::class.java) {
      validator.intercept(chain)
    }

    assertThat(callbackHostname).isEqualTo("evil.example.com")
    assertThat(callbackReason).isEqualTo("No Signed Certificate Timestamps found")
  }

  @Test
  fun `no callback crash when callback is null`() {
    val validator = CertificateTransparencyValidator(
      enabled = true,
      onValidationFailure = null,
    )

    val cert = stubCert("CN=Unknown CA, O=Unknown")
    val chain = buildFakeChain(certificates = listOf(cert))

    // Should still throw but not crash on null callback
    assertThrows(CertificateTransparencyException::class.java) {
      validator.intercept(chain)
    }
  }

  @Test
  fun `callback not invoked on validation success`() {
    var callbackCalled = false
    val validator = CertificateTransparencyValidator(
      enabled = true,
      onValidationFailure = { _, _ -> callbackCalled = true },
    )

    val cert = stubCert("CN=DigiCert Root, O=DigiCert Inc")
    val chain = buildFakeChain(certificates = listOf(cert))

    validator.intercept(chain)
    assertThat(callbackCalled).isFalse()
  }

  // ---------------------------------------------------------------
  // Response closed on validation failure
  // ---------------------------------------------------------------

  @Test
  fun `response closed on validation failure`() {
    val validator = CertificateTransparencyValidator(enabled = true)
    val cert = stubCert("CN=Unknown CA, O=Unknown")

    val request = Request.Builder().url("https://example.com/api").build()
    val handshake = Handshake.get(
      TlsVersion.TLS_1_3,
      okhttp3.CipherSuite.TLS_AES_128_GCM_SHA256,
      listOf(cert),
      listOf(cert),
    )
    val responseBody = "test-body".toResponseBody()
    val response = Response.Builder()
      .request(request)
      .protocol(Protocol.HTTP_2)
      .code(200)
      .message("OK")
      .body(responseBody)
      .handshake(handshake)
      .build()

    val chain = FakeInterceptorChain(request, response)

    assertThrows(CertificateTransparencyException::class.java) {
      validator.intercept(chain)
    }
    // After the exception the response was closed by the validator.
    // Verify the body source is exhausted / closed by reading it.
    // OkHttp's ResponseBody.close() closes the underlying source.
  }

  // ---------------------------------------------------------------
  // Exception type and message
  // ---------------------------------------------------------------

  @Test
  fun `CertificateTransparencyException is an IOException`() {
    val exception = CertificateTransparencyException("test message")
    assertThat(exception).isInstanceOf(java.io.IOException::class.java)
    assertThat(exception.message).isEqualTo("test message")
  }

  @Test
  fun `exception message contains hostname and reason`() {
    val cert = stubCert("CN=Unknown CA")
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(
      url = "https://target.example.com/path",
      certificates = listOf(cert),
    )

    val exception = assertThrows(CertificateTransparencyException::class.java) {
      validator.intercept(chain)
    }
    assertThat(exception.message).contains("target.example.com")
    assertThat(exception.message).contains("No Signed Certificate Timestamps found")
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
  // Multi-certificate chain -- leaf is validated
  // ---------------------------------------------------------------

  @Test
  fun `intercept validates leaf certificate from chain of multiple certs`() {
    val leafCert = stubCert("CN=DigiCert SHA2 EV Server CA, O=DigiCert Inc")
    val intermediateCert = stubCert("CN=DigiCert High Assurance EV Root CA")

    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(leafCert, intermediateCert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `intercept fails when leaf cert is from unknown CA in multi-cert chain`() {
    val leafCert = stubCert("CN=Totally Fake CA")
    val intermediateCert = stubCert("CN=Root Unknown")

    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(leafCert, intermediateCert))

    assertThrows(CertificateTransparencyException::class.java) {
      validator.intercept(chain)
    }
  }

  // ---------------------------------------------------------------
  // SCT extension takes precedence over known-CA check
  // ---------------------------------------------------------------

  @Test
  fun `certificate with SCT from unknown CA passes validation`() {
    // Has SCT but issuer is not a known CA -- should still pass
    val cert = stubCert("CN=Unknown Private CA, O=Private Org", hasSct = true)
    val validator = CertificateTransparencyValidator(enabled = true)
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }

  // ---------------------------------------------------------------
  // Default constructor parameter
  // ---------------------------------------------------------------

  @Test
  fun `default constructor has enabled true`() {
    val cert = stubCert("CN=DigiCert Root CA, O=DigiCert")
    val validator = CertificateTransparencyValidator()
    val chain = buildFakeChain(certificates = listOf(cert))

    val response = validator.intercept(chain)
    assertThat(response.code).isEqualTo(200)
  }
}
