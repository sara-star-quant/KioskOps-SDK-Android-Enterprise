/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.crypto

/**
 * Policy for encryption key rotation.
 *
 * Key rotation helps limit the exposure window if a key is compromised
 * and is required by many compliance frameworks (SOC 2, FedRAMP, etc.).
 *
 * @property maxKeyAgeDays Maximum age of a key before rotation is recommended.
 *           After this period, [VersionedCryptoProvider.shouldRotate] returns true.
 *           Set to 0 to disable age-based rotation checks.
 * @property autoRotateEnabled When true, the SDK will automatically rotate keys
 *           when they exceed [maxKeyAgeDays]. When false, rotation is manual via
 *           [VersionedCryptoProvider.rotateKey].
 * @property retainOldKeysForDays Number of days to retain old key versions for
 *           backward-compatible decryption. After this period, old keys may be
 *           deleted and data encrypted with them becomes unreadable.
 * @property maxKeyVersions Maximum number of key versions to retain. Oldest
 *           versions beyond this limit are deleted. Set to 0 for unlimited.
 */
data class KeyRotationPolicy(
  val maxKeyAgeDays: Int = 365,
  val autoRotateEnabled: Boolean = false,
  val retainOldKeysForDays: Int = 90,
  val maxKeyVersions: Int = 5,
) {
  init {
    require(maxKeyAgeDays >= 0) { "maxKeyAgeDays must be non-negative" }
    require(retainOldKeysForDays >= 0) { "retainOldKeysForDays must be non-negative" }
    require(maxKeyVersions >= 0) { "maxKeyVersions must be non-negative" }
  }

  companion object {
    /**
     * Default policy suitable for most enterprise deployments.
     * Annual rotation with 90-day backward compatibility.
     */
    fun default() = KeyRotationPolicy()

    /**
     * Strict policy for high-security environments.
     * Quarterly rotation with 30-day backward compatibility.
     */
    fun strict() = KeyRotationPolicy(
      maxKeyAgeDays = 90,
      autoRotateEnabled = true,
      retainOldKeysForDays = 30,
      maxKeyVersions = 4,
    )

    /**
     * Policy that disables rotation checks.
     * Use only for testing or specific compliance requirements.
     */
    fun disabled() = KeyRotationPolicy(
      maxKeyAgeDays = 0,
      autoRotateEnabled = false,
      retainOldKeysForDays = Int.MAX_VALUE,
      maxKeyVersions = 0,
    )
  }
}

/**
 * Result of a key rotation operation.
 */
sealed class RotationResult {
  /**
   * Key was successfully rotated.
   * @property newKeyVersion The version number of the new key.
   * @property previousKeyVersion The version of the key that was current before rotation.
   */
  data class Success(
    val newKeyVersion: Int,
    val previousKeyVersion: Int,
  ) : RotationResult()

  /**
   * Rotation was not needed (key is not old enough).
   * @property currentKeyVersion The current key version.
   * @property keyAgeDays The age of the current key in days.
   */
  data class NotNeeded(
    val currentKeyVersion: Int,
    val keyAgeDays: Int,
  ) : RotationResult()

  /**
   * Rotation failed due to an error.
   * @property reason Description of why rotation failed.
   * @property cause The underlying exception, if any.
   */
  data class Failed(
    val reason: String,
    val cause: Throwable? = null,
  ) : RotationResult()
}
