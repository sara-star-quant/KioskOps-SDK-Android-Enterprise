/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit.core

/** SHA-256 over bytes, rendered as unpadded base64url. The platform provider does the hashing. */
internal expect object Sha256 {
  fun base64Url(bytes: ByteArray): String
}
