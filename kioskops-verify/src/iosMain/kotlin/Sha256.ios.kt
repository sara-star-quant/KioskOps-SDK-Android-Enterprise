/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
internal actual object Sha256 {
  actual fun base64Url(bytes: ByteArray): String {
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    // An empty array has no element 0 to pin; hash a one-byte buffer with length 0.
    val input = if (bytes.isEmpty()) ByteArray(1) else bytes
    input.usePinned { src ->
      digest.usePinned { dst -> CC_SHA256(src.addressOf(0), bytes.size.convert(), dst.addressOf(0)) }
    }
    return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(digest.asByteArray())
  }
}
