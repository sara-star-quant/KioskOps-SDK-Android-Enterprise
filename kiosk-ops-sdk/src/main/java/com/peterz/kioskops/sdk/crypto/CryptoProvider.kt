package com.peterz.kioskops.sdk.crypto

interface CryptoProvider {
  val isEnabled: Boolean
  fun encrypt(plain: ByteArray): ByteArray
  fun decrypt(blob: ByteArray): ByteArray
}
