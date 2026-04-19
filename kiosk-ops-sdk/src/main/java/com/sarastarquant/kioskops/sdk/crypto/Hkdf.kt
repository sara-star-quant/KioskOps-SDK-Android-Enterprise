/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val HMAC_SHA256 = "HmacSHA256"
private const val SHA256_LEN = 32
private const val MAX_HKDF_OUT_BLOCKS = 255

/**
 * HKDF-Extract + HKDF-Expand (RFC 5869) with HMAC-SHA256.
 *
 * Used where a deterministic key derivation is needed from arbitrary input keying material
 * (IKM) with domain separation via [info] and optional [salt]. Unlike PBKDF2, HKDF is
 * designed for key derivation from high-entropy keys (not passwords) and is standardised
 * with rigid input/output semantics, which makes it the correct primitive for reproducible
 * idempotency-key generation, subkey derivation, and similar internal flows.
 *
 * @param ikm Input keying material.
 * @param salt HKDF salt; empty salt is allowed and canonicalises to [SHA256_LEN] zero bytes.
 * @param info Context / label for domain separation.
 * @param outLen Output length in bytes; must satisfy `1 <= outLen <= 255 * HashLen = 8160`.
 * @return [outLen] derived bytes.
 *
 * @since 1.2.0
 */
internal fun hkdfSha256(
  ikm: ByteArray,
  salt: ByteArray,
  info: ByteArray,
  outLen: Int,
): ByteArray {
  require(outLen in 1..(MAX_HKDF_OUT_BLOCKS * SHA256_LEN)) { "HKDF outLen out of range" }

  val mac = Mac.getInstance(HMAC_SHA256)
  // Extract: PRK = HMAC(salt or zero, IKM). RFC 5869 allows an empty salt, canonicalised
  // to HashLen zero bytes.
  val extractSalt = if (salt.isEmpty()) ByteArray(SHA256_LEN) else salt
  mac.init(SecretKeySpec(extractSalt, HMAC_SHA256))
  val prk = mac.doFinal(ikm)

  // Expand: T = T(1) | T(2) | ... | T(n); T(i) = HMAC(PRK, T(i-1) | info | i).
  mac.init(SecretKeySpec(prk, HMAC_SHA256))
  val blocks = (outLen + SHA256_LEN - 1) / SHA256_LEN
  val out = ByteArray(blocks * SHA256_LEN)
  var prev = ByteArray(0)
  for (i in 1..blocks) {
    mac.reset()
    mac.update(prev)
    mac.update(info)
    mac.update(i.toByte())
    prev = mac.doFinal()
    prev.copyInto(out, (i - 1) * SHA256_LEN)
  }
  return out.copyOfRange(0, outLen)
}
