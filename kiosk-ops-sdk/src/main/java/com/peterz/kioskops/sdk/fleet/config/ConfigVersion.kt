/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet.config

import kotlinx.serialization.Serializable

/**
 * Represents a versioned configuration snapshot.
 *
 * Audit (ISO 27001 A.12.4): All version transitions are logged
 * with source, hash, and timestamp for change tracking.
 *
 * @property version Monotonically increasing version number
 * @property createdAtMs Timestamp when this version was created
 * @property contentHash SHA-256 hash of config content (base64url)
 * @property source Delivery channel for this config
 * @property abVariant A/B test variant identifier (if applicable)
 * @property signature ECDSA P-256 signature (if signed config enabled)
 */
@Serializable
data class ConfigVersion(
  val version: Long,
  val createdAtMs: Long,
  val contentHash: String,
  val source: ConfigSource,
  val abVariant: String? = null,
  val signature: String? = null,
)

/**
 * Source of configuration delivery.
 */
@Serializable
enum class ConfigSource {
  /** Compiled into the application. */
  EMBEDDED,
  /** Received via Android managed configurations (EMM/MDM). */
  MANAGED_CONFIG,
  /** Received via Firebase Cloud Messaging. */
  FCM,
  /** Restored from local version history. */
  ROLLBACK,
}

/**
 * Result of a configuration update attempt.
 */
sealed class ConfigUpdateResult {
  /**
   * Config was successfully applied.
   */
  data class Applied(val version: ConfigVersion) : ConfigUpdateResult()

  /**
   * Config was rejected.
   */
  data class Rejected(val reason: ConfigRejectionReason) : ConfigUpdateResult()
}

/**
 * Reason for config rejection.
 *
 * Security: Each rejection reason maps to a specific security control.
 */
enum class ConfigRejectionReason {
  /** Remote config is disabled in policy. */
  DISABLED,
  /** Version is not newer than current. */
  VERSION_TOO_OLD,
  /** Signature verification failed (BSI APP.4.4.A3). */
  SIGNATURE_INVALID,
  /** Cooldown period has not elapsed. */
  COOLDOWN_ACTIVE,
  /** Version is below minimum allowed (BSI APP.4.4.A5). */
  MINIMUM_VERSION_VIOLATION,
  /** Config bundle could not be parsed. */
  PARSE_ERROR,
}

/**
 * Result of a configuration rollback attempt.
 */
sealed class ConfigRollbackResult {
  /**
   * Rollback was successful.
   */
  data class Success(val version: ConfigVersion) : ConfigRollbackResult()

  /**
   * Rollback was blocked by policy.
   */
  data class Blocked(val reason: String) : ConfigRollbackResult()

  /**
   * Requested version was not found in history.
   */
  data class NotFound(val requestedVersion: Long) : ConfigRollbackResult()
}
