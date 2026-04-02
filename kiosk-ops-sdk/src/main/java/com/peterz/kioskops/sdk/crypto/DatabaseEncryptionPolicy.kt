/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.crypto

/**
 * Policy for database-at-rest encryption via SQLCipher.
 *
 * When enabled, all Room databases (queue, audit, config) are encrypted using
 * SQLCipher with a key stored in Android Keystore. This provides an additional
 * encryption layer beyond Android full-disk encryption for federal/DoD deployments.
 *
 * Requires `net.zetetic:sqlcipher-android` on the runtime classpath.
 *
 * @since 0.8.0
 */
data class DatabaseEncryptionPolicy(
  val enabled: Boolean = false,
) {
  companion object {
    fun disabledDefaults() = DatabaseEncryptionPolicy(enabled = false)

    fun enabledDefaults() = DatabaseEncryptionPolicy(enabled = true)
  }
}
