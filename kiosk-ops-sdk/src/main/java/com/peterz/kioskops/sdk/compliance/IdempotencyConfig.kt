package com.peterz.kioskops.sdk.compliance

/**
 * Controls how idempotency keys are generated.
 */
data class IdempotencyConfig(
  /**
   * When true and a caller supplies a stableEventId, the SDK will compute an idempotency key
   * deterministically (HMAC) so re-enqueueing the same business event does not create duplicates.
   */
  val deterministicEnabled: Boolean,

  /**
   * Time-bucket size used when computing deterministic keys. Bucketed keys help avoid unintended
   * long-term collisions if an upstream system reuses stable ids.
   */
  val bucketMs: Long,
) {
  companion object {
    fun maximalistDefaults() = IdempotencyConfig(
      deterministicEnabled = true,
      bucketMs = 24L * 60 * 60 * 1000 // 1 day
    )
  }
}
