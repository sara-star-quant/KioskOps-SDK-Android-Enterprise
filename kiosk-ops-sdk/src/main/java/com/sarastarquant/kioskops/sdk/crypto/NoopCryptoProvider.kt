/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.crypto

object NoopCryptoProvider : CryptoProvider {
  override val isEnabled: Boolean = false
  override fun encrypt(plain: ByteArray): ByteArray = plain
  override fun decrypt(blob: ByteArray): ByteArray = blob
}
