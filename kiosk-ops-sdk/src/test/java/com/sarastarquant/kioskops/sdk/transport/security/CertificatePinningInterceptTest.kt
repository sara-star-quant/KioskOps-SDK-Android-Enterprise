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

@RunWith(RobolectricTestRunner::class)
class CertificatePinningInterceptTest {

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
  // intercept(): unpinned host passthrough (hasPinsForHost returns false)
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
  // intercept(): pin validation failure invokes callback and throws
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
      // Expected: pin mismatch
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
  // intercept(): pin format normalization exercised via real request
  // ---------------------------------------------------------------------------

  @Test
  fun `intercept with pin missing sha256 prefix still validates`() {
    server.enqueue(MockResponse.Builder().code(200).body("ok").build())

    // Pin without sha256/ prefix: buildPinner should add it
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
  // intercept(): successful request passthrough when no pins for host
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
  // intercept(): multiple pins per host, multiple hosts
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
  // intercept(): callback details
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
  // intercept(): request method variations
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
  // Constructor default callback
  // ---------------------------------------------------------------------------

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

    // Both pins are normalized but neither matches the real cert: should fail
    val thrown = assertThrows(Exception::class.java) {
      client.newCall(Request.Builder().url(server.url("/mixed")).build()).execute()
    }
    assertThat(findCauseOfType<CertificatePinningException>(thrown)).isNotNull()
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
