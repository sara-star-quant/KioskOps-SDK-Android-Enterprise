/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk

/**
 * Callback for non-fatal SDK operational errors.
 *
 * The SDK continues operating after these errors. Implementations must be thread-safe
 * as callbacks may fire from background threads.
 *
 * @since 0.7.0
 */
fun interface KioskOpsErrorListener {
  fun onError(error: KioskOpsError)
}

/**
 * Categorized SDK error reported to [KioskOpsErrorListener].
 *
 * @since 0.7.0
 */
sealed class KioskOpsError {
  abstract val message: String
  abstract val cause: Throwable?

  data class EnqueueFailed(
    override val message: String,
    override val cause: Throwable? = null,
  ) : KioskOpsError()

  data class SyncFailed(
    override val message: String,
    val httpStatus: Int? = null,
    override val cause: Throwable? = null,
  ) : KioskOpsError()

  data class CryptoError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : KioskOpsError()

  data class StorageError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : KioskOpsError()

  data class ConfigError(
    override val message: String,
    override val cause: Throwable? = null,
  ) : KioskOpsError()
}
