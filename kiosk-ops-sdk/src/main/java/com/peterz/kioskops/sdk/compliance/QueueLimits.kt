package com.peterz.kioskops.sdk.compliance

/**
 * Local storage pressure controls.
 *
 * Kiosk devices can remain offline for long periods. Without limits, the queue can grow
 * unbounded and consume disk. These limits are enforced before writing payloads to disk.
 */
data class QueueLimits(
  /** Max number of non-sent events stored locally (any state except SENT). */
  val maxActiveEvents: Int,
  /** Max number of bytes for non-sent events stored locally. */
  val maxActiveBytes: Long,
  /** Strategy when limits are exceeded. */
  val overflowStrategy: OverflowStrategy,
) {
  companion object {
    fun maximalistDefaults() = QueueLimits(
      maxActiveEvents = 5000,
      maxActiveBytes = 50L * 1024 * 1024,
      overflowStrategy = OverflowStrategy.DROP_OLDEST
    )
  }
}

enum class OverflowStrategy {
  /** Delete oldest eligible (non-quarantined) events until the new event fits. */
  DROP_OLDEST,
  /** Reject the new event. */
  DROP_NEWEST,
  /** Reject the new event and treat it as a hard failure (caller should surface this). */
  BLOCK,
}
