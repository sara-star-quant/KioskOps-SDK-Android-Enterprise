/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit.core

/** Checks a base64 signature over a signing payload; the platform key store supplies the implementation. */
public fun interface SignatureVerifier {
  public fun verify(payload: String, signature: String): Boolean
}
