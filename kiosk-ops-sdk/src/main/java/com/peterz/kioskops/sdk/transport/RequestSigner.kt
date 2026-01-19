package com.peterz.kioskops.sdk.transport

import okhttp3.HttpUrl

/**
 * Request signing is an optional security layer on top of TLS.
 *
 * Design goals:
 * - deterministic canonical string
 * - no payload leakage into headers (only a SHA-256 digest)
 * - explicit host opt-in (SDK never signs/ships secrets unless provided)
 */
interface RequestSigner {
  /**
   * @return headers to be attached to the request (e.g. signature, timestamp, nonce)
   */
  fun sign(
    method: String,
    url: HttpUrl,
    contentType: String,
    bodyBytes: ByteArray
  ): Map<String, String>
}
