package com.peterz.kioskops.sdk.transport

import com.peterz.kioskops.sdk.util.Clock
import com.peterz.kioskops.sdk.util.Hashing
import okhttp3.HttpUrl
import java.util.UUID

/**
 * HMAC-SHA256 request signer.
 *
 * Canonical string (v1):
 * METHOD \n
 * PATH_WITH_QUERY \n
 * UNIX_EPOCH_SECONDS \n
 * NONCE \n
 * BODY_SHA256_B64URL \n
 * CONTENT_TYPE
 */
class HmacRequestSigner(
  private val sharedSecret: ByteArray,
  private val keyId: String? = null,
  private val clock: Clock = Clock.SYSTEM,
  private val nonceProvider: NonceProvider = NonceProvider { UUID.randomUUID().toString() },
  private val headerPrefix: String = "X-KioskOps"
) : RequestSigner {

  override fun sign(method: String, url: HttpUrl, contentType: String, bodyBytes: ByteArray): Map<String, String> {
    val tsSeconds = (clock.nowMs() / 1000L).toString()
    val nonce = nonceProvider.nextNonce()

    val pathWithQuery = buildString {
      append(url.encodedPath)
      val q = url.encodedQuery
      if (!q.isNullOrBlank()) {
        append('?')
        append(q)
      }
    }

    val bodyDigest = Hashing.sha256Base64Url(bodyBytes)

    val canonical = buildString {
      append(method.uppercase())
      append('\n')
      append(pathWithQuery)
      append('\n')
      append(tsSeconds)
      append('\n')
      append(nonce)
      append('\n')
      append(bodyDigest)
      append('\n')
      append(contentType)
    }

    val sig = Hashing.hmacSha256Base64Url(sharedSecret, canonical)

    val out = LinkedHashMap<String, String>(6)
    out["$headerPrefix-Signature-Version"] = "1"
    out["$headerPrefix-Timestamp"] = tsSeconds
    out["$headerPrefix-Nonce"] = nonce
    if (!keyId.isNullOrBlank()) out["$headerPrefix-Key-Id"] = keyId
    out["$headerPrefix-Signature"] = "hmac-sha256:$sig"
    return out
  }
}
