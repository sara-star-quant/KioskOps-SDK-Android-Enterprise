package com.peterz.kioskops.sdk.util

import com.peterz.kioskops.sdk.compliance.IdempotencyConfig

object Idempotency {
  /**
   * Computes a deterministic idempotency key using HMAC-SHA256 with a per-install secret.
   *
   * We bucket by time (default 1 day) to limit the blast radius if an upstream system reuses
   * stable ids across unrelated periods.
   */
  fun compute(
    secret: ByteArray,
    type: String,
    stableEventId: String,
    nowMs: Long,
    cfg: IdempotencyConfig
  ): String {
    val bucket = if (cfg.bucketMs <= 0) 0L else (nowMs / cfg.bucketMs) * cfg.bucketMs
    val msg = "$type|$stableEventId|$bucket"
    return Hashing.hmacSha256Base64Url(secret, msg)
  }
}
