/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet.config

/**
 * Policy for remote configuration updates.
 *
 * Security Controls (BSI IT-Grundschutz APP.4.4.A3):
 * - Config updates are validated against version constraints
 * - Optional signature verification prevents unauthorized changes
 * - Cooldown prevents rapid config cycling attacks
 *
 * @property enabled Enable remote config updates via managed config or FCM
 * @property minimumConfigVersion Minimum version to accept (prevents rollback attacks)
 * @property requireSignedConfig Require ECDSA P-256 signed config bundles
 * @property configSigningPublicKey Public key for signature verification (base64 DER)
 * @property abTestingEnabled Enable A/B testing variant assignment
 * @property stickyVariantAssignment Persist variant assignment across config updates
 * @property maxRetainedVersions Maximum config versions to retain for rollback
 * @property configApplyCooldownMs Minimum interval between config applies (ms)
 */
data class RemoteConfigPolicy(
  val enabled: Boolean = false,
  val minimumConfigVersion: Long = 0L,
  val requireSignedConfig: Boolean = false,
  val configSigningPublicKey: String? = null,
  val abTestingEnabled: Boolean = false,
  val stickyVariantAssignment: Boolean = true,
  val maxRetainedVersions: Int = 5,
  val configApplyCooldownMs: Long = 60_000L,
) {
  companion object {
    /**
     * Disabled defaults - remote config is opt-in.
     */
    fun disabledDefaults() = RemoteConfigPolicy(enabled = false)

    /**
     * Enterprise defaults with signature verification enabled.
     *
     * Suitable for SOC 2 / ISO 27001 compliant deployments.
     */
    fun enterpriseDefaults() = RemoteConfigPolicy(
      enabled = true,
      minimumConfigVersion = 1L,
      requireSignedConfig = true,
      maxRetainedVersions = 5,
      configApplyCooldownMs = 60_000L,
    )

    /**
     * Pilot defaults for testing without signature verification.
     */
    fun pilotDefaults() = RemoteConfigPolicy(
      enabled = true,
      minimumConfigVersion = 0L,
      requireSignedConfig = false,
      abTestingEnabled = true,
      maxRetainedVersions = 10,
    )
  }
}
