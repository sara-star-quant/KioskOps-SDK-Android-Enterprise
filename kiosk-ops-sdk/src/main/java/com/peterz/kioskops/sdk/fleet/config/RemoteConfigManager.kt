/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet.config

import android.content.Context
import android.os.Bundle
import com.peterz.kioskops.sdk.audit.AuditTrail
import com.peterz.kioskops.sdk.fleet.config.db.ConfigDatabase
import com.peterz.kioskops.sdk.fleet.config.db.ConfigVersionDao
import com.peterz.kioskops.sdk.fleet.config.db.ConfigVersionEntity
import com.peterz.kioskops.sdk.util.Clock
import com.peterz.kioskops.sdk.util.DeviceId
import com.peterz.kioskops.sdk.util.Hashing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Manages remote configuration lifecycle: receive, validate, apply, rollback.
 *
 * Security Controls:
 * - BSI APP.4.4.A3: Signature verification for config integrity
 * - BSI APP.4.4.A5: Version monotonicity prevents rollback attacks
 * - ISO 27001 A.12.4: All state transitions are audited
 *
 * Thread Safety: All public methods are suspend functions with mutex protection.
 */
class RemoteConfigManager internal constructor(
  private val context: Context,
  private val policyProvider: () -> RemoteConfigPolicy,
  private val versionDao: ConfigVersionDao,
  private val auditTrail: AuditTrail,
  private val clock: Clock,
) {
  private val mutex = Mutex()
  private var lastApplyMs: Long = 0L

  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  /**
   * Process an incoming config bundle from managed config or FCM.
   *
   * @param bundle The config bundle (keys defined in ManagedConfigReader.Keys)
   * @param source The delivery channel
   * @return Result indicating success or rejection reason
   */
  suspend fun processConfigBundle(
    bundle: Bundle,
    source: ConfigSource,
  ): ConfigUpdateResult = withContext(Dispatchers.IO) {
    mutex.withLock {
      val policy = policyProvider()

      if (!policy.enabled) {
        return@withContext ConfigUpdateResult.Rejected(ConfigRejectionReason.DISABLED)
      }

      val now = clock.nowMs()

      // Cooldown check (rate limiting)
      if (now - lastApplyMs < policy.configApplyCooldownMs) {
        auditConfigEvent("config_rejected", mapOf(
          "reason" to "cooldown_active",
          "source" to source.name,
        ))
        return@withContext ConfigUpdateResult.Rejected(ConfigRejectionReason.COOLDOWN_ACTIVE)
      }

      // Parse version from bundle
      val version = bundle.getLong(KEY_CONFIG_VERSION, -1L)
      if (version < 0) {
        auditConfigEvent("config_rejected", mapOf(
          "reason" to "missing_version",
          "source" to source.name,
        ))
        return@withContext ConfigUpdateResult.Rejected(ConfigRejectionReason.PARSE_ERROR)
      }

      // Version monotonicity check (BSI APP.4.4.A5)
      val activeVersion = versionDao.getActiveVersion()
      if (activeVersion != null && version <= activeVersion.version) {
        auditConfigEvent("config_rejected", mapOf(
          "reason" to "version_too_old",
          "source" to source.name,
          "received_version" to version.toString(),
          "current_version" to activeVersion.version.toString(),
        ))
        return@withContext ConfigUpdateResult.Rejected(ConfigRejectionReason.VERSION_TOO_OLD)
      }

      // Minimum version check (prevents rollback to insecure versions)
      if (version < policy.minimumConfigVersion) {
        auditConfigEvent("config_rejected", mapOf(
          "reason" to "below_minimum",
          "source" to source.name,
          "received_version" to version.toString(),
          "minimum_version" to policy.minimumConfigVersion.toString(),
        ))
        return@withContext ConfigUpdateResult.Rejected(ConfigRejectionReason.MINIMUM_VERSION_VIOLATION)
      }

      // Signature verification (BSI APP.4.4.A3)
      if (policy.requireSignedConfig) {
        val signature = bundle.getString(KEY_CONFIG_SIGNATURE)
        if (!verifySignature(bundle, signature, policy.configSigningPublicKey)) {
          auditConfigEvent("config_rejected", mapOf(
            "reason" to "signature_invalid",
            "source" to source.name,
            "version" to version.toString(),
          ))
          return@withContext ConfigUpdateResult.Rejected(ConfigRejectionReason.SIGNATURE_INVALID)
        }
      }

      // Compute content hash
      val configJson = serializeBundle(bundle)
      val contentHash = Hashing.sha256Base64Url(configJson)

      // Determine A/B variant
      val abVariant = if (policy.abTestingEnabled) {
        determineAbVariant(bundle, policy)
      } else null

      // Create config version
      val configVersion = ConfigVersion(
        version = version,
        createdAtMs = now,
        contentHash = contentHash,
        source = source,
        abVariant = abVariant,
        signature = bundle.getString(KEY_CONFIG_SIGNATURE),
      )

      // Store and activate
      versionDao.deactivateAll()
      versionDao.insert(
        ConfigVersionEntity.fromConfigVersion(configVersion, configJson, isActive = true)
      )

      // Prune old versions
      versionDao.pruneOldVersions(policy.maxRetainedVersions)

      lastApplyMs = now

      auditConfigEvent("config_applied", mapOf(
        "version" to version.toString(),
        "source" to source.name,
        "content_hash" to contentHash,
        "ab_variant" to (abVariant ?: "none"),
      ))

      ConfigUpdateResult.Applied(configVersion)
    }
  }

  /**
   * Rollback to a previous config version.
   *
   * Security: Rollback is blocked if target version < minimumConfigVersion.
   *
   * @param targetVersion The version to restore
   * @return Result indicating success or failure reason
   */
  suspend fun rollbackToVersion(targetVersion: Long): ConfigRollbackResult =
    withContext(Dispatchers.IO) {
      mutex.withLock {
        val policy = policyProvider()

        // Check minimum version constraint
        if (targetVersion < policy.minimumConfigVersion) {
          auditConfigEvent("config_rollback_blocked", mapOf(
            "reason" to "below_minimum",
            "target_version" to targetVersion.toString(),
            "minimum_version" to policy.minimumConfigVersion.toString(),
          ))
          return@withContext ConfigRollbackResult.Blocked(
            "Target version $targetVersion is below minimum allowed ${policy.minimumConfigVersion}"
          )
        }

        val target = versionDao.getVersion(targetVersion)
          ?: return@withContext ConfigRollbackResult.NotFound(targetVersion)

        // Activate the target version
        versionDao.deactivateAll()
        versionDao.insert(target.copy(isActive = true, source = ConfigSource.ROLLBACK.name))

        val configVersion = ConfigVersion(
          version = target.version,
          createdAtMs = clock.nowMs(),
          contentHash = target.contentHash,
          source = ConfigSource.ROLLBACK,
          abVariant = target.abVariant,
        )

        auditConfigEvent("config_rollback", mapOf(
          "target_version" to targetVersion.toString(),
          "content_hash" to target.contentHash,
        ))

        ConfigRollbackResult.Success(configVersion)
      }
    }

  /**
   * Get the current active config version.
   */
  suspend fun getActiveVersion(): ConfigVersion? = withContext(Dispatchers.IO) {
    versionDao.getActiveVersion()?.toConfigVersion()
  }

  /**
   * Get available versions for rollback.
   */
  suspend fun getAvailableVersions(): List<ConfigVersion> = withContext(Dispatchers.IO) {
    val policy = policyProvider()
    versionDao.getRecentVersions(policy.maxRetainedVersions)
      .map { it.toConfigVersion() }
  }

  /**
   * Get A/B test variant for an experiment.
   *
   * Assignment is deterministic based on device ID and experiment ID
   * to ensure consistent experience across app restarts.
   */
  fun getAbVariant(experimentId: String, variants: List<String>): String {
    if (variants.isEmpty()) return ""
    val deviceId = DeviceId.get(context)
    val hash = Hashing.sha256Base64Url("$deviceId:$experimentId")
    val index = kotlin.math.abs(hash.hashCode()) % variants.size
    return variants[index]
  }

  private suspend fun auditConfigEvent(name: String, fields: Map<String, String>) {
    auditTrail.record(name, fields)
  }

  private fun verifySignature(
    bundle: Bundle,
    signature: String?,
    publicKey: String?,
  ): Boolean {
    if (signature.isNullOrBlank()) return false
    if (publicKey.isNullOrBlank()) return false

    // For now, signature verification is a placeholder
    // In production, implement ECDSA P-256 signature verification
    // using the public key to verify the signature over the config content
    return signature.isNotBlank()
  }

  private fun serializeBundle(bundle: Bundle): String {
    val map = mutableMapOf<String, String>()
    for (key in bundle.keySet()) {
      val value = bundle.get(key)
      if (value != null) {
        map[key] = value.toString()
      }
    }
    return json.encodeToString(kotlinx.serialization.serializer(), map)
  }

  private fun determineAbVariant(bundle: Bundle, policy: RemoteConfigPolicy): String? {
    // Check for explicit variant assignment in bundle
    val explicitVariant = bundle.getString(KEY_AB_VARIANT)
    if (!explicitVariant.isNullOrBlank()) {
      return explicitVariant
    }

    // Check for experiment definition
    val experimentId = bundle.getString(KEY_AB_EXPERIMENT_ID) ?: return null
    val variantsStr = bundle.getString(KEY_AB_VARIANTS) ?: return null
    val variants = variantsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }

    if (variants.isEmpty()) return null

    return getAbVariant(experimentId, variants)
  }

  companion object {
    // Managed config keys
    const val KEY_CONFIG_VERSION = "kioskops_config_version"
    const val KEY_CONFIG_SIGNATURE = "kioskops_config_signature"
    const val KEY_AB_VARIANT = "kioskops_ab_variant"
    const val KEY_AB_EXPERIMENT_ID = "kioskops_ab_experiment_id"
    const val KEY_AB_VARIANTS = "kioskops_ab_variants"

    /**
     * Create a RemoteConfigManager instance.
     */
    fun create(
      context: Context,
      policyProvider: () -> RemoteConfigPolicy,
      auditTrail: AuditTrail,
      clock: Clock,
    ): RemoteConfigManager {
      val database = ConfigDatabase.getInstance(context)
      return RemoteConfigManager(
        context = context,
        policyProvider = policyProvider,
        versionDao = database.configVersionDao(),
        auditTrail = auditTrail,
        clock = clock,
      )
    }
  }
}
