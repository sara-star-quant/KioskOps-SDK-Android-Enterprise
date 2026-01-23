package com.peterz.kioskops.sdk.queue

import com.peterz.kioskops.sdk.crypto.CryptoProvider

object PayloadCodec {
  const val ENCODING_PLAIN_UTF8 = "plain_utf8"
  const val ENCODING_AESGCM_V1 = "aesgcm_v1"

  fun encodeJson(payloadJson: String, encrypt: Boolean, crypto: CryptoProvider): Encoded {
    val bytes = payloadJson.toByteArray(Charsets.UTF_8)
    return if (encrypt && crypto.isEnabled) {
      Encoded(blob = crypto.encrypt(bytes), encoding = ENCODING_AESGCM_V1)
    } else {
      Encoded(blob = bytes, encoding = ENCODING_PLAIN_UTF8)
    }
  }

  fun decodeToJson(blob: ByteArray, encoding: String, crypto: CryptoProvider): String {
    val plain = when (encoding) {
      ENCODING_PLAIN_UTF8 -> blob
      ENCODING_AESGCM_V1 -> crypto.decrypt(blob)
      else -> throw IllegalArgumentException("Unknown encoding: $encoding")
    }
    return String(plain, Charsets.UTF_8)
  }

  /**
   * Encoded payload with explicit equals/hashCode for ByteArray content comparison.
   */
  data class Encoded(val blob: ByteArray, val encoding: String) {
    override fun equals(other: Any?): Boolean =
      other is Encoded && blob.contentEquals(other.blob) && encoding == other.encoding

    override fun hashCode(): Int = blob.contentHashCode() * 31 + encoding.hashCode()
  }
}
