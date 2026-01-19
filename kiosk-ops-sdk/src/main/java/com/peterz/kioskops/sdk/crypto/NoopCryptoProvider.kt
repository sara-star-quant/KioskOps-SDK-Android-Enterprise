package com.peterz.kioskops.sdk.crypto

object NoopCryptoProvider : CryptoProvider {
  override val isEnabled: Boolean = false
  override fun encrypt(plain: ByteArray): ByteArray = plain
  override fun decrypt(blob: ByteArray): ByteArray = blob
}
