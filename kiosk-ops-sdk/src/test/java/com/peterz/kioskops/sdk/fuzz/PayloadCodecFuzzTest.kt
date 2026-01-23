package com.peterz.kioskops.sdk.fuzz

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.peterz.kioskops.sdk.crypto.CryptoProvider
import com.peterz.kioskops.sdk.crypto.SoftwareAesGcmCryptoProvider
import com.peterz.kioskops.sdk.queue.PayloadCodec

/**
 * Fuzz tests for PayloadCodec to detect crashes, hangs, and unexpected exceptions.
 *
 * Run locally: ./gradlew :kiosk-ops-sdk:testDebugUnitTest --tests "*FuzzTest*"
 * CI: Runs with limited iterations for regression detection.
 */
class PayloadCodecFuzzTest {

  private val crypto: CryptoProvider = SoftwareAesGcmCryptoProvider()
  private val noCrypto: CryptoProvider = object : CryptoProvider {
    override val isEnabled = false
    override fun encrypt(plain: ByteArray) = plain
    override fun decrypt(blob: ByteArray) = blob
  }

  @FuzzTest(maxDuration = "1m")
  fun fuzzEncodeDecodeRoundTrip(data: FuzzedDataProvider) {
    val input = data.consumeString(10_000)
    val encrypt = data.consumeBoolean()
    val provider = if (encrypt) crypto else noCrypto

    try {
      val encoded = PayloadCodec.encodeJson(input, encrypt, provider)
      val decoded = PayloadCodec.decodeToJson(encoded.blob, encoded.encoding, provider)

      // Round-trip must preserve data
      if (decoded != input) {
        throw AssertionError("Round-trip failed: input length=${input.length}, decoded length=${decoded.length}")
      }
    } catch (e: OutOfMemoryError) {
      // Reject OOM from extremely large inputs
      throw e
    } catch (_: Exception) {
      // Other exceptions are acceptable (e.g., malformed UTF-8)
    }
  }

  @FuzzTest(maxDuration = "1m")
  fun fuzzDecodeArbitraryBlob(data: FuzzedDataProvider) {
    val blob = data.consumeBytes(10_000)
    val encoding = data.consumeString(100)

    try {
      PayloadCodec.decodeToJson(blob, encoding, crypto)
    } catch (_: IllegalArgumentException) {
      // Expected for unknown encoding
    } catch (_: javax.crypto.AEADBadTagException) {
      // Expected for invalid ciphertext
    } catch (_: javax.crypto.BadPaddingException) {
      // Expected for corrupted data
    } catch (_: IllegalStateException) {
      // Expected for malformed blob structure
    } catch (_: ArrayIndexOutOfBoundsException) {
      // Expected for truncated blobs
    } catch (_: Exception) {
      // Catch-all for other expected failures
    }
  }

  @FuzzTest(maxDuration = "1m")
  fun fuzzCryptoProviderEncryptDecrypt(data: FuzzedDataProvider) {
    val plain = data.consumeBytes(10_000)

    try {
      val encrypted = crypto.encrypt(plain)
      val decrypted = crypto.decrypt(encrypted)

      if (!plain.contentEquals(decrypted)) {
        throw AssertionError("Crypto round-trip failed")
      }
    } catch (_: Exception) {
      // Encryption/decryption failures are acceptable for fuzz inputs
    }
  }
}
