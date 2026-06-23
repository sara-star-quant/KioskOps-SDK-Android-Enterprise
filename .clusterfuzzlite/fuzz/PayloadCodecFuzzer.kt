package com.sarastarquant.kioskops.sdk.fuzz

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.sarastarquant.kioskops.sdk.crypto.CryptoProvider
import com.sarastarquant.kioskops.sdk.queue.PayloadCodec
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ClusterFuzzLite harness for PayloadCodec, the codec that frames queued event
 * payloads. decodeToJson parses attacker-influenced blobs and encoding tags, so it
 * is the highest-value untrusted-input surface in the SDK. This harness drives both
 * the decode-arbitrary-bytes path and the encode/decode round-trip with a real
 * software AES-GCM provider, and asserts the round-trip is lossless.
 *
 * OSS-Fuzz/ClusterFuzzLite entrypoint: static fuzzerTestOneInput(FuzzedDataProvider).
 */
object PayloadCodecFuzzer {

  // Fixed key: fuzzing explores codec framing, not key management.
  private val keyBytes = ByteArray(32) { it.toByte() }

  private val crypto = object : CryptoProvider {
    override val isEnabled = true

    override fun encrypt(plain: ByteArray): ByteArray {
      val iv = ByteArray(12) { 7 }
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
      return iv + cipher.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
      require(blob.size >= 12) { "blob too short for IV" }
      val iv = blob.copyOfRange(0, 12)
      val body = blob.copyOfRange(12, blob.size)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
      return cipher.doFinal(body)
    }
  }

  @JvmStatic
  fun fuzzerTestOneInput(data: FuzzedDataProvider) {
    // Path 1: decode an arbitrary blob under a fuzzer-chosen encoding tag.
    val encoding = when (data.consumeInt(0, 2)) {
      0 -> PayloadCodec.ENCODING_PLAIN_UTF8
      1 -> PayloadCodec.ENCODING_AESGCM_V1
      else -> data.consumeString(32)
    }
    val blob = data.consumeBytes(4096)
    try {
      PayloadCodec.decodeToJson(blob, encoding, crypto)
    } catch (_: IllegalArgumentException) {
      // Unknown encoding tag.
    } catch (_: IllegalStateException) {
      // Malformed blob framing.
    } catch (_: GeneralSecurityException) {
      // Bad ciphertext / tag for the AES-GCM path.
    } catch (_: IndexOutOfBoundsException) {
      // Truncated blob.
    }

    // Path 2: encode then decode must round-trip losslessly.
    val payload = data.consumeRemainingAsString()
    val encoded = PayloadCodec.encodeJson(payload, encrypt = true, crypto = crypto)
    val decoded = PayloadCodec.decodeToJson(encoded.blob, encoded.encoding, crypto)
    check(decoded == payload) { "round-trip mismatch: in=${payload.length} out=${decoded.length}" }
  }
}
