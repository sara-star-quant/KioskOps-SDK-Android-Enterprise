package com.peterz.kioskops.sdk.crypto

/**
 * Wraps a real crypto provider and turns it into a configuration-driven switch.
 *
 * This supports "progressive enhancement": deployments can disable encryption for non-sensitive pilots,
 * while still using the same code paths.
 */
class ConditionalCryptoProvider(
  private val enabledProvider: () -> Boolean,
  private val delegate: CryptoProvider,
) : CryptoProvider {

  override val isEnabled: Boolean
    get() = enabledProvider() && delegate.isEnabled

  override fun encrypt(plain: ByteArray): ByteArray {
    return if (isEnabled) delegate.encrypt(plain) else plain
  }

  override fun decrypt(blob: ByteArray): ByteArray {
    return if (isEnabled) delegate.decrypt(blob) else blob
  }
}
