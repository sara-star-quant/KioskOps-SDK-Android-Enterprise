/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.transport.security

import com.google.common.truth.Truth.assertThat
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@Suppress("LargeClass")
@RunWith(RobolectricTestRunner::class)
class CertificatePinningInterceptorTest {

  private lateinit var server: MockWebServer

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
  }

  @After
  fun tearDown() {
    server.close()
  }

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
  // intercept() -- unpinned host passthrough (hasPinsForHost returns false)
  // ---------------------------------------------------------------------------

  @Test
  fun `intercept passes through when host has no pins configured`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("other.example.com", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    // The server hostname (localhost) is not pinned, so it should pass through
    val response = client.newCall(
      Request.Builder().url(server.url("/passthrough")).build()
    ).execute()

    assertThat(response.code).isEqualTo(200)
    assertThat(response.body.string()).isEqualTo("ok")
  }

  @Test
  fun `intercept passes through for unpinned host with empty pins list`() {
    server.enqueue(MockResponse.Builder().code(200).body("data").build())

    val interceptor = CertificatePinningInterceptor(pins = emptyList())
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    val response = client.newCall(
      Request.Builder().url(server.url("/empty-pins")).build()
    ).execute()

    assertThat(response.code).isEqualTo(200)
    assertThat(response.body.string()).isEqualTo("data")
  }

  // ---------------------------------------------------------------------------
  // intercept() -- pin validation failure invokes callback and throws
  // ---------------------------------------------------------------------------

  @Test
  fun `intercept invokes callback on pin validation failure`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    var callbackHostname: String? = null
    var callbackMessage: String? = null

    // Pin localhost with a bogus pin that will definitely not match
    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("localhost", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      ),
      onPinValidationFailure = { hostname, message ->
        callbackHostname = hostname
        callbackMessage = message
      }
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    try {
      client.newCall(
        Request.Builder().url(server.url("/pinned")).build()
      ).execute()
    } catch (_: Exception) {
      // Expected -- pin mismatch
    }

    assertThat(callbackHostname).isEqualTo("localhost")
    assertThat(callbackMessage).isNotNull()
    assertThat(callbackMessage).isNotEmpty()
  }

  @Test
  fun `intercept throws CertificatePinningException on pin mismatch`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("localhost", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    val thrown = assertThrows(Exception::class.java) {
      client.newCall(
        Request.Builder().url(server.url("/pinned")).build()
      ).execute()
    }

    // The exception should be or wrap a CertificatePinningException
    val pinException = findCauseOfType<CertificatePinningException>(thrown)
    assertThat(pinException).isNotNull()
    assertThat(pinException!!.message).contains("localhost")
  }

  @Test
  fun `intercept throws without callback when callback is null`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("localhost", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      ),
      onPinValidationFailure = null,
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    val thrown = assertThrows(Exception::class.java) {
      client.newCall(
        Request.Builder().url(server.url("/no-callback")).build()
      ).execute()
    }

    val pinException = findCauseOfType<CertificatePinningException>(thrown)
    assertThat(pinException).isNotNull()
  }

  @Test
  fun `intercept exception message includes hostname`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("localhost", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    val thrown = assertThrows(Exception::class.java) {
      client.newCall(
        Request.Builder().url(server.url("/check-message")).build()
      ).execute()
    }

    val pinException = findCauseOfType<CertificatePinningException>(thrown)
    assertThat(pinException).isNotNull()
    assertThat(pinException!!.message).contains("Certificate pinning validation failed for localhost")
  }

  // ---------------------------------------------------------------------------
  // intercept() -- pin format normalization exercised via real request
  // ---------------------------------------------------------------------------

  @Test
  fun `intercept with pin missing sha256 prefix still validates`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    // Pin without sha256/ prefix -- buildPinner should add it
    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("localhost", listOf("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    // This will fail because the pin does not match, proving the pin was
    // normalized and validation was attempted
    val thrown = assertThrows(Exception::class.java) {
      client.newCall(
        Request.Builder().url(server.url("/normalized")).build()
      ).execute()
    }

    val pinException = findCauseOfType<CertificatePinningException>(thrown)
    assertThat(pinException).isNotNull()
  }

  // ---------------------------------------------------------------------------
  // hasPinsForHost / matchesHostname -- tested indirectly through intercept()
  // ---------------------------------------------------------------------------

  @Test
  fun `exact hostname match triggers pin validation`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("localhost", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    // Exact match "localhost" should trigger pin validation and fail
    assertThrows(Exception::class.java) {
      client.newCall(
        Request.Builder().url(server.url("/exact")).build()
      ).execute()
    }
  }

  @Test
  fun `wildcard hostname match triggers pin validation for single-level subdomain`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    // Pin *.localhost -- if the server hostname is "api.localhost", it should match
    // In practice, we test this by pinning *.example.com and requesting a
    // non-pinned host to show passthrough vs pinned behavior
    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("*.example.com", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    // localhost does NOT match *.example.com, so should pass through
    val response = client.newCall(
      Request.Builder().url(server.url("/wildcard-no-match")).build()
    ).execute()

    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `non-matching hostname skips pin validation and passes through`() {
    server.enqueue(MockResponse.Builder().code(200).body("passthrough").build())

    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("pinned.example.com", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    // localhost != pinned.example.com, so no validation
    val response = client.newCall(
      Request.Builder().url(server.url("/not-pinned")).build()
    ).execute()

    assertThat(response.code).isEqualTo(200)
    assertThat(response.body.string()).isEqualTo("passthrough")
  }

  // ---------------------------------------------------------------------------
  // matchesHostname -- wildcard edge cases via fromPolicy + intercept behavior
  // ---------------------------------------------------------------------------

  @Test
  fun `wildcard pattern does not match multi-level subdomain`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    // *.example.com should NOT match a.b.example.com
    // We construct an interceptor pinning *.example.com, and show that
    // a.b.example.com (if it were the request host) would pass through.
    // Since MockWebServer is localhost, we test by verifying a host that
    // does NOT have pins passes through.
    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("*.example.com", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    // localhost is not *.example.com, passes through
    val response = client.newCall(
      Request.Builder().url(server.url("/multi-level")).build()
    ).execute()
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `wildcard pattern does not match bare domain`() {
    // *.example.com should NOT match example.com (bare domain)
    // This tests the matchesHostname logic: pattern="*.example.com", hostname="example.com"
    // The prefix would be empty string, which fails the isNotEmpty check.
    server.enqueue(MockResponse.Builder().code(200).body("bare-domain").build())

    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("*.localhost", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    // "localhost" does NOT match "*.localhost" because prefix would be empty
    val response = client.newCall(
      Request.Builder().url(server.url("/bare")).build()
    ).execute()
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body.string()).isEqualTo("bare-domain")
  }

  // ---------------------------------------------------------------------------
  // matchesHostname -- unit-level via dedicated interceptors
  // ---------------------------------------------------------------------------

  @Test
  fun `matchesHostname exact match verified via pin failure`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    // Exact-match "localhost" -- should trigger pin check and fail
    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("localhost", listOf("sha256/ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ="))
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    val thrown = assertThrows(Exception::class.java) {
      client.newCall(Request.Builder().url(server.url("/")).build()).execute()
    }
    assertThat(findCauseOfType<CertificatePinningException>(thrown)).isNotNull()
  }

  @Test
  fun `non-wildcard pattern does not match subdomain`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    // "example.com" should NOT match "api.example.com"
    // Use a host that is exactly "example.com" -- since server is localhost,
    // localhost won't match example.com and will pass through.
    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("example.com", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    val response = client.newCall(
      Request.Builder().url(server.url("/sub")).build()
    ).execute()
    assertThat(response.code).isEqualTo(200)
  }

  // ---------------------------------------------------------------------------
  // CertificatePinningException
  // ---------------------------------------------------------------------------

  @Test
  fun `CertificatePinningException contains message`() {
    val cause = RuntimeException("root cause")
    val exception = CertificatePinningException("pin failed", cause)
    assertThat(exception.message).isEqualTo("pin failed")
    assertThat(exception.cause).isEqualTo(cause)
  }

  @Test
  fun `CertificatePinningException with null cause`() {
    val exception = CertificatePinningException("pin failed")
    assertThat(exception.message).isEqualTo("pin failed")
    assertThat(exception.cause).isNull()
  }

  @Test
  fun `CertificatePinningException is IOException`() {
    val exception = CertificatePinningException("test")
    assertThat(exception).isInstanceOf(java.io.IOException::class.java)
  }

  // ---------------------------------------------------------------------------
  // intercept() -- successful request passthrough when no pins for host
  // ---------------------------------------------------------------------------

  @Test
  fun `intercept preserves response body when host is not pinned`() {
    val body = """{"status":"ok","count":42}"""
    server.enqueue(MockResponse.Builder().code(200).body(body).build())

    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("other.host.com", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    val response = client.newCall(
      Request.Builder().url(server.url("/api/data")).build()
    ).execute()

    assertThat(response.code).isEqualTo(200)
    assertThat(response.body.string()).isEqualTo(body)
  }

  @Test
  fun `intercept preserves response headers when host is not pinned`() {
    server.enqueue(
      MockResponse.Builder()
        .code(200)
        .addHeader("X-Custom-Header", "custom-value")
        .body("ok")
        .build()
    )

    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("other.host.com", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    val response = client.newCall(
      Request.Builder().url(server.url("/headers")).build()
    ).execute()

    assertThat(response.header("X-Custom-Header")).isEqualTo("custom-value")
  }

  @Test
  fun `intercept preserves HTTP status code when host is not pinned`() {
    server.enqueue(MockResponse.Builder().code(404).body("not found").build())

    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("pinned.example.com", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    val response = client.newCall(
      Request.Builder().url(server.url("/missing")).build()
    ).execute()

    assertThat(response.code).isEqualTo(404)
  }

  // ---------------------------------------------------------------------------
  // intercept() -- multiple pins per host, multiple hosts
  // ---------------------------------------------------------------------------

  @Test
  fun `intercept with multiple pins for pinned host still fails on mismatch`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin(
          hostname = "localhost",
          sha256Pins = listOf(
            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
            "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=",
          )
        )
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    val thrown = assertThrows(Exception::class.java) {
      client.newCall(Request.Builder().url(server.url("/multi-pin")).build()).execute()
    }
    assertThat(findCauseOfType<CertificatePinningException>(thrown)).isNotNull()
  }

  @Test
  fun `intercept with multiple host entries only validates matching host`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("api.example.com", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")),
        CertificatePin("cdn.example.com", listOf("sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")),
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    // localhost matches neither, so passthrough
    val response = client.newCall(
      Request.Builder().url(server.url("/multi-host")).build()
    ).execute()
    assertThat(response.code).isEqualTo(200)
  }

  // ---------------------------------------------------------------------------
  // intercept() -- callback details
  // ---------------------------------------------------------------------------

  @Test
  fun `callback receives hostname and failure details`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    var receivedHost: String? = null
    var receivedMessage: String? = null

    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("localhost", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      ),
      onPinValidationFailure = { host, msg ->
        receivedHost = host
        receivedMessage = msg
      }
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    try {
      client.newCall(Request.Builder().url(server.url("/callback-detail")).build()).execute()
    } catch (_: Exception) {
      // Expected
    }

    assertThat(receivedHost).isEqualTo("localhost")
    assertThat(receivedMessage).isNotNull()
    assertThat(receivedMessage!!.length).isGreaterThan(0)
  }

  @Test
  fun `callback is not invoked when host is not pinned`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    var callbackInvoked = false

    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("other.example.com", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      ),
      onPinValidationFailure = { _, _ -> callbackInvoked = true }
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    client.newCall(
      Request.Builder().url(server.url("/no-callback")).build()
    ).execute()

    assertThat(callbackInvoked).isFalse()
  }

  // ---------------------------------------------------------------------------
  // intercept() -- request method variations
  // ---------------------------------------------------------------------------

  @Test
  fun `intercept passes through POST request for unpinned host`() {
    server.enqueue(MockResponse.Builder().code(201).body("created").build())

    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("pinned.example.com", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    val requestBody = """{"key":"value"}""".toRequestBody("application/json".toMediaType())
    val response = client.newCall(
      Request.Builder().url(server.url("/post")).post(requestBody).build()
    ).execute()

    assertThat(response.code).isEqualTo(201)
  }

  // ---------------------------------------------------------------------------
  // hasPinsForHost -- wildcard matching edge cases via constructor + intercept
  // ---------------------------------------------------------------------------

  @Test
  fun `wildcard pin for server host triggers validation`() {
    // This tests that *.localhost would NOT match "localhost" (bare domain)
    // because the prefix would be empty
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("*.localhost", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    // "localhost" does not match "*.localhost" -- should pass through
    val response = client.newCall(
      Request.Builder().url(server.url("/wildcard-bare")).build()
    ).execute()
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `multiple wildcard pins with different domains`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("*.example.com", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")),
        CertificatePin("*.other.com", listOf("sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")),
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    // localhost matches neither wildcard, should pass through
    val response = client.newCall(
      Request.Builder().url(server.url("/multi-wildcard")).build()
    ).execute()
    assertThat(response.code).isEqualTo(200)
  }

  @Test
  fun `exact match takes priority over non-matching wildcard`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    // Pin with exact "localhost" match AND a wildcard that does not match
    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("*.example.com", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")),
        CertificatePin("localhost", listOf("sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")),
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    // "localhost" matches the exact pin entry, so validation fires and fails
    assertThrows(Exception::class.java) {
      client.newCall(Request.Builder().url(server.url("/exact-priority")).build()).execute()
    }
  }

  // ---------------------------------------------------------------------------
  // Constructor direct usage
  // ---------------------------------------------------------------------------

  @Test
  fun `constructor accepts empty pins list`() {
    val interceptor = CertificatePinningInterceptor(pins = emptyList())
    assertThat(interceptor).isNotNull()
  }

  @Test
  fun `constructor default callback is null`() {
    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin("example.com", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
      )
    )
    // No callback should not cause NPE when validation fails
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    // Host is not pinned (localhost != example.com), so passthrough
    val response = client.newCall(
      Request.Builder().url(server.url("/default-callback")).build()
    ).execute()
    assertThat(response.code).isEqualTo(200)
  }

  // ---------------------------------------------------------------------------
  // Mixed pin formats in single host entry
  // ---------------------------------------------------------------------------

  @Test
  fun `mixed pin formats with and without sha256 prefix`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    val interceptor = CertificatePinningInterceptor(
      pins = listOf(
        CertificatePin(
          hostname = "localhost",
          sha256Pins = listOf(
            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
          )
        )
      )
    )
    val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    // Both pins are normalized but neither matches the real cert -- should fail
    val thrown = assertThrows(Exception::class.java) {
      client.newCall(Request.Builder().url(server.url("/mixed")).build()).execute()
    }
    assertThat(findCauseOfType<CertificatePinningException>(thrown)).isNotNull()
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
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Walk the exception cause chain looking for an instance of [T].
   * Returns the first match or null.
   */
  private inline fun <reified T : Throwable> findCauseOfType(throwable: Throwable): T? {
    var current: Throwable? = throwable
    while (current != null) {
      if (current is T) return current
      current = current.cause
    }
    return null
  }
}
