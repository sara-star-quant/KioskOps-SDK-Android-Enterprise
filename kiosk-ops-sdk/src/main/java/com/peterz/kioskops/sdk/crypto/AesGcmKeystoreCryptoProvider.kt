package com.peterz.kioskops.sdk.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM with a hardware-backed key when available.
 *
 * Blob format: [v:1 byte][ivLen:1 byte][iv][ciphertext]
 */
class AesGcmKeystoreCryptoProvider(
  private val alias: String = "kioskops_aes_gcm_v1",
) : CryptoProvider {

  override val isEnabled: Boolean = true

  private fun getOrCreateKey(): SecretKey {
    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val existing = ks.getKey(alias, null) as? SecretKey
    if (existing != null) return existing

    val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    val spec = KeyGenParameterSpec.Builder(
      alias,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .build()

    gen.init(spec)
    return gen.generateKey()
  }

  override fun encrypt(plain: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
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
    require(blob.isNotEmpty() && blob[0].toInt() == 1) { "Unknown crypto blob version" }
    val ivLen = blob[1].toInt()
    require(ivLen in 12..32) { "Invalid IV length" }

    val ivStart = 2
    val ctStart = ivStart + ivLen
    require(ctStart <= blob.size) { "Malformed crypto blob" }

    val iv = blob.copyOfRange(ivStart, ctStart)
    val ct = blob.copyOfRange(ctStart, blob.size)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
    return cipher.doFinal(ct)
  }
}
