package com.sarastarquant.kioskops.sdk.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

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

  override fun encrypt(plain: ByteArray): ByteArray = AesGcmBlob.seal(getOrCreateKey(), plain)

  override fun decrypt(blob: ByteArray): ByteArray = AesGcmBlob.open(getOrCreateKey(), blob)
}
