package com.peterz.kioskops.sdk.transport

import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.util.Clock
import com.peterz.kioskops.sdk.util.Hashing
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class HmacRequestSignerTest {
  @Test
  fun `sign adds deterministic headers and expected signature`() {
    val clock = Clock { 1_700_000_000_000L } // epoch ms
    val nonceProvider = NonceProvider { "nonce-123" }
    val secret = "super-secret".toByteArray()

    val signer = HmacRequestSigner(
      sharedSecret = secret,
      keyId = "key-1",
      clock = clock,
      nonceProvider = nonceProvider,
      headerPrefix = "X-KioskOps"
    )

    val url = "https://api.example.tld/events/batch?x=1".toHttpUrl()
    val body = "{\"hello\":\"world\"}".toByteArray()
    val headers = signer.sign(
      method = "POST",
      url = url,
      contentType = "application/json",
      bodyBytes = body
    )

    assertThat(headers["X-KioskOps-Signature-Version"]).isEqualTo("1")
    assertThat(headers["X-KioskOps-Timestamp"]).isEqualTo("1700000000")
    assertThat(headers["X-KioskOps-Nonce"]).isEqualTo("nonce-123")
    assertThat(headers["X-KioskOps-Key-Id"]).isEqualTo("key-1")

    val bodyDigest = Hashing.sha256Base64Url(body)
    val canonical = "POST\n/events/batch?x=1\n1700000000\nnonce-123\n$bodyDigest\napplication/json"
    val expected = "hmac-sha256:" + Hashing.hmacSha256Base64Url(secret, canonical)
    assertThat(headers["X-KioskOps-Signature"]).isEqualTo(expected)
  }
}
