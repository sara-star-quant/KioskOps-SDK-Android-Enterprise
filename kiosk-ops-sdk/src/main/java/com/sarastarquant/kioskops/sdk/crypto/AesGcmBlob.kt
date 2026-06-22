/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.crypto

import androidx.annotation.VisibleForTesting
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM transform and blob framing shared by the crypto providers.
 *
 * Blob format: [version:1][ivLen:1][iv][ciphertext||tag]. The transform is
 * AES/GCM/NoPadding with a 128-bit authentication tag and no associated data.
 * Centralizing it here keeps the keystore-backed and software providers on one
 * byte format and gives the known-answer tests a single, deterministic seam.
 */
internal object AesGcmBlob {

  const val VERSION: Int = 1
  const val TAG_BITS: Int = 128
  const val IV_LEN_BYTES: Int = 12

  private const val TRANSFORM = "AES/GCM/NoPadding"

  /** Encrypt [plain] under [key], generating a fresh random IV. */
  fun seal(key: SecretKey, plain: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORM)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    return frame(cipher.iv, cipher.doFinal(plain))
  }

  /** Decrypt a [blob] produced by [seal] / [sealWithIv]. */
  fun open(key: SecretKey, blob: ByteArray): ByteArray {
    require(blob.size >= 2 && blob[0].toInt() == VERSION) { "Unknown crypto blob version" }
    val ivLen = blob[1].toInt()
    require(ivLen in 12..32) { "Invalid IV length" }
    val ctStart = 2 + ivLen
    require(ctStart <= blob.size) { "Malformed crypto blob" }

    val iv = blob.copyOfRange(2, ctStart)
    val ct = blob.copyOfRange(ctStart, blob.size)
    val cipher = Cipher.getInstance(TRANSFORM)
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
    return cipher.doFinal(ct)
  }

  /**
   * Encrypt with a caller-supplied IV.
   *
   * Reusing an IV under the same key breaks GCM, so this is exposed only for
   * known-answer tests that pin a published (key, IV, plaintext) vector. Never
   * call it from production code.
   */
  @VisibleForTesting
  fun sealWithIv(key: SecretKey, iv: ByteArray, plain: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORM)
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
    return frame(iv, cipher.doFinal(plain))
  }

  private fun frame(iv: ByteArray, ct: ByteArray): ByteArray {
    val out = ByteArray(2 + iv.size + ct.size)
    out[0] = VERSION.toByte()
    out[1] = iv.size.toByte()
    System.arraycopy(iv, 0, out, 2, iv.size)
    System.arraycopy(ct, 0, out, 2 + iv.size, ct.size)
    return out
  }
}
