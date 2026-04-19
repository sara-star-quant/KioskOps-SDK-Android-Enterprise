/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.fleet.config

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
  /**
   * Opt-in marker for configuration profiles that are explicitly not production-ready.
   * Currently gates [RemoteConfigPolicy.pilotDefaults], which disables signature
   * verification and lowers minimum-version enforcement. Call sites must add
   * `@OptIn(PilotConfig::class)` or propagate `@PilotConfig` to acknowledge they are
   * choosing a lower-security posture. See KDoc on [pilotDefaults] for details.
   *
   * @since 1.2.0
   */
  @RequiresOptIn(
    message = "This configuration disables signature verification and lowers version " +
      "enforcement; it is intended for staging, pilots, and integration testing. Do not " +
      "use in production. Acknowledge with @OptIn(PilotConfig::class).",
    level = RequiresOptIn.Level.WARNING,
  )
  @Retention(AnnotationRetention.BINARY)
  @Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
  annotation class PilotConfig

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
     *
     * **NOT FOR PRODUCTION.** Disables `requireSignedConfig` and accepts any
     * `minimumConfigVersion`, which means a malicious managed-config push can install a
     * policy that relaxes PII handling, disables encryption, or redirects baseUrl. The
     * returned policy is still safer than disabling remote config entirely (A/B testing
     * + version monotonicity still apply) but is inappropriate for any deployment that
     * handles real user data. Gated by [PilotConfig] since 1.2.0; callers must add
     * `@OptIn(RemoteConfigPolicy.PilotConfig::class)` to acknowledge.
     */
    @PilotConfig
    fun pilotDefaults() = RemoteConfigPolicy(
      enabled = true,
      minimumConfigVersion = 0L,
      requireSignedConfig = false,
      abTestingEnabled = true,
      maxRetainedVersions = 10,
    )
  }
}
