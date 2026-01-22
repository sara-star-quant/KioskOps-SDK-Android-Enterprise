package com.peterz.kioskops.sdk.transport

import com.peterz.kioskops.sdk.KioskOpsConfig

/** Result classification for sync retries. */
sealed class TransportResult<out T> {
  data class Success<T>(val value: T, val httpStatus: Int? = null) : TransportResult<T>()
  data class TransientFailure(val message: String, val httpStatus: Int? = null, val cause: Throwable? = null) : TransportResult<Nothing>()
  data class PermanentFailure(val message: String, val httpStatus: Int? = null, val cause: Throwable? = null) : TransportResult<Nothing>()
}

interface Transport {
  /**
   * Send a batch of events.
   *
   * Server MUST dedupe by idempotencyKey.
   */
  suspend fun sendBatch(cfg: KioskOpsConfig, request: BatchSendRequest): TransportResult<BatchSendResponse>
}
