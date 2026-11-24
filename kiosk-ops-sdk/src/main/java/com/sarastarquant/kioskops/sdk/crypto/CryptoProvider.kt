/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.crypto

interface CryptoProvider {
  val isEnabled: Boolean
  fun encrypt(plain: ByteArray): ByteArray
  fun decrypt(blob: ByteArray): ByteArray
}
