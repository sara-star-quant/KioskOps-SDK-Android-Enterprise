/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.benchmark

import com.sarastarquant.kioskops.sdk.crypto.CryptoProvider
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Software AES-256-GCM provider for benchmarking the encryption algorithm in
 * isolation. The shipped [com.sarastarquant.kioskops.sdk.crypto.AesGcmKeystoreCryptoProvider]
 * reloads the AndroidKeyStore on every call, so benchmarking it measures keystore
 * round-trips, not the cipher. This holds the key in memory to surface the
 * cipher cost itself; it mirrors the blob format the shipped provider uses.
 */
class SoftwareAesGcmProvider : CryptoProvider {
  override val isEnabled: Boolean = true
  private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

  override fun encrypt(plain: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val iv = cipher.iv
    val ct = cipher.doFinal(plain)
    val out = ByteArray(2 + iv.size + ct.size)
    out[0] = 1
    out[1] = iv.size.toByte()
    System.arraycopy(iv, 0, out, 2, iv.size)
    System.arraycopy(ct, 0, out, 2 + iv.size, ct.size)
    return out
  }

  override fun decrypt(blob: ByteArray): ByteArray {
    val ivLen = blob[1].toInt()
    val iv = blob.copyOfRange(2, 2 + ivLen)
    val ct = blob.copyOfRange(2 + ivLen, blob.size)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
    return cipher.doFinal(ct)
  }
}
