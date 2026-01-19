package com.peterz.kioskops.sdk.sync

/**
 * Network sync policy.
 *
 * Default is disabled to avoid any silent off-device transfer.
 * The host app must explicitly enable it.
 */
data class SyncPolicy(
  val enabled: Boolean,
  val endpointPath: String = "events/batch",
  val batchSize: Int = 50,
  /**
   * If true, periodic sync requires unmetered network (Wi‑Fi). Useful for large payloads.
   */
  val requireUnmeteredNetwork: Boolean = false,
  /**
   * Max attempts per event before we stop retrying automatically.
   */
  val maxAttemptsPerEvent: Int = 12,
) {
  companion object {
    fun disabledDefaults() = SyncPolicy(enabled = false)
  }
}
