package com.sarastarquant.kioskops.sdk.sync

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
   * If true, periodic sync requires unmetered network (Wi-Fi). Useful for large payloads.
   */
  val requireUnmeteredNetwork: Boolean = false,
  /**
   * Max attempts per event before we stop retrying automatically.
   */
  val maxAttemptsPerEvent: Int = 12,
  /**
   * OkHttp connect timeout in seconds. Applied to the SDK-built HTTP client; ignored when the
   * host supplies `okHttpClientOverride` to [com.sarastarquant.kioskops.sdk.KioskOpsSdk.init].
   * @since 1.2.0
   */
  val connectTimeoutSeconds: Long = 15,
  /**
   * OkHttp read timeout in seconds. A slow server trickling bytes can stall a worker for a
   * long time without [callTimeoutSeconds]; this caps inter-byte wait.
   * @since 1.2.0
   */
  val readTimeoutSeconds: Long = 30,
  /**
   * OkHttp write timeout in seconds.
   * @since 1.2.0
   */
  val writeTimeoutSeconds: Long = 30,
  /**
   * OkHttp end-to-end call timeout in seconds. Upper bound on a single batch send including
   * retries and redirects; prevents a hung server from pinning a sync worker until Android
   * kills it. Set to 0 to disable.
   * @since 1.2.0
   */
  val callTimeoutSeconds: Long = 60,
) {
  companion object {
    fun disabledDefaults() = SyncPolicy(enabled = false)
    fun enabledDefaults() = SyncPolicy(enabled = true)
  }
}
