package com.sarastarquant.kioskops.sdk.crypto

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Test-only crypto provider (JVM) */
class SoftwareAesGcmCryptoProvider : CryptoProvider {
  override val isEnabled: Boolean = true
  private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

  override fun encrypt(plain: ByteArray): ByteArray = AesGcmBlob.seal(key, plain)

  override fun decrypt(blob: ByteArray): ByteArray = AesGcmBlob.open(key, blob)
}
