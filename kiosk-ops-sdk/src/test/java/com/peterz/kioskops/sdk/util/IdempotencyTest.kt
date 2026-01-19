package com.peterz.kioskops.sdk.util

import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.compliance.IdempotencyConfig
import org.junit.Test

class IdempotencyTest {
  @Test
  fun `deterministic idempotency is stable within bucket and changes across buckets`() {
    val secret = ByteArray(32) { 1 }
    val cfg = IdempotencyConfig.maximalistDefaults().copy(bucketMs = 24L * 60L * 60L * 1000L)

    val now = 1_700_000_000_000L
    val k1 = Idempotency.compute(secret, type = "T", stableEventId = "ORDER-123", nowMs = now, cfg = cfg)
    val k2 = Idempotency.compute(secret, type = "T", stableEventId = "ORDER-123", nowMs = now + 10_000L, cfg = cfg)
    assertThat(k1).isEqualTo(k2)

    val nextDay = now + cfg.bucketMs
    val k3 = Idempotency.compute(secret, type = "T", stableEventId = "ORDER-123", nowMs = nextDay, cfg = cfg)
    assertThat(k3).isNotEqualTo(k1)
  }
}
