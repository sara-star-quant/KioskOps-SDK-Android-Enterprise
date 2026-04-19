/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.fleet.config

import android.content.Context
import android.os.Bundle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.audit.AuditTrail
import com.sarastarquant.kioskops.sdk.compliance.RetentionPolicy
import com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider
import com.sarastarquant.kioskops.sdk.fleet.config.db.ConfigDatabase
import com.sarastarquant.kioskops.sdk.util.Clock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(RemoteConfigPolicy.PilotConfig::class)
@RunWith(RobolectricTestRunner::class)
class RemoteConfigManagerTest {

  private lateinit var ctx: Context
  private lateinit var db: ConfigDatabase
  private lateinit var manager: RemoteConfigManager
  private lateinit var audit: AuditTrail
  private var policy = RemoteConfigPolicy.pilotDefaults()

  private var nowMs = 1_700_000_000_000L
  private val clock = object : Clock {
    override fun nowMs(): Long = nowMs
  }

  @Before
  fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(ctx, ConfigDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    ConfigDatabase.setInstance(db)
    audit = AuditTrail(
      context = ctx,
      retentionProvider = { RetentionPolicy.maximalistDefaults() },
      clock = clock,
      crypto = NoopCryptoProvider,
    )
    manager = RemoteConfigManager(
      context = ctx,
      policyProvider = { policy },
      versionDao = db.configVersionDao(),
      auditTrail = audit,
      clock = clock,
    )
  }

  @After
  fun tearDown() {
    db.close()
    ConfigDatabase.setInstance(null)
  }

  private fun configBundle(version: Long, extraKeys: Map<String, String> = emptyMap()): Bundle {
    return Bundle().apply {
      putLong("kioskops_config_version", version)
      putString("baseUrl", "https://api.example.com")
      extraKeys.forEach { (k, v) -> putString(k, v) }
    }
  }

  @Test
  fun `processConfigBundle succeeds for valid version`() = runTest {
    val result = manager.processConfigBundle(configBundle(1L), ConfigSource.MANAGED_CONFIG)
    assertThat(result).isInstanceOf(ConfigUpdateResult.Applied::class.java)
    val applied = result as ConfigUpdateResult.Applied
    assertThat(applied.version.version).isEqualTo(1L)
    assertThat(applied.version.source).isEqualTo(ConfigSource.MANAGED_CONFIG)
  }

  @Test
  fun `processConfigBundle rejects disabled policy`() = runTest {
    policy = RemoteConfigPolicy.disabledDefaults()
    val result = manager.processConfigBundle(configBundle(1L), ConfigSource.MANAGED_CONFIG)
    assertThat(result).isInstanceOf(ConfigUpdateResult.Rejected::class.java)
    assertThat((result as ConfigUpdateResult.Rejected).reason).isEqualTo(ConfigRejectionReason.DISABLED)
  }

  @Test
  fun `processConfigBundle rejects version not higher than current`() = runTest {
    manager.processConfigBundle(configBundle(5L), ConfigSource.MANAGED_CONFIG)

    nowMs += 120_000 // past cooldown
    val result = manager.processConfigBundle(configBundle(3L), ConfigSource.MANAGED_CONFIG)
    assertThat(result).isInstanceOf(ConfigUpdateResult.Rejected::class.java)
    assertThat((result as ConfigUpdateResult.Rejected).reason).isEqualTo(ConfigRejectionReason.VERSION_TOO_OLD)
  }

  @Test
  fun `processConfigBundle rejects equal version`() = runTest {
    manager.processConfigBundle(configBundle(5L), ConfigSource.MANAGED_CONFIG)

    nowMs += 120_000
    val result = manager.processConfigBundle(configBundle(5L), ConfigSource.MANAGED_CONFIG)
    assertThat(result).isInstanceOf(ConfigUpdateResult.Rejected::class.java)
  }

  @Test
  fun `processConfigBundle enforces cooldown`() = runTest {
    manager.processConfigBundle(configBundle(1L), ConfigSource.MANAGED_CONFIG)

    // Try again immediately (within cooldown)
    val result = manager.processConfigBundle(configBundle(2L), ConfigSource.MANAGED_CONFIG)
    assertThat(result).isInstanceOf(ConfigUpdateResult.Rejected::class.java)
    assertThat((result as ConfigUpdateResult.Rejected).reason).isEqualTo(ConfigRejectionReason.COOLDOWN_ACTIVE)
  }

  @Test
  fun `processConfigBundle succeeds after cooldown expires`() = runTest {
    manager.processConfigBundle(configBundle(1L), ConfigSource.MANAGED_CONFIG)

    nowMs += policy.configApplyCooldownMs + 1
    val result = manager.processConfigBundle(configBundle(2L), ConfigSource.MANAGED_CONFIG)
    assertThat(result).isInstanceOf(ConfigUpdateResult.Applied::class.java)
  }

  @Test
  fun `processConfigBundle rejects version below minimum`() = runTest {
    policy = RemoteConfigPolicy(enabled = true, minimumConfigVersion = 10L)
    val result = manager.processConfigBundle(configBundle(5L), ConfigSource.MANAGED_CONFIG)
    assertThat(result).isInstanceOf(ConfigUpdateResult.Rejected::class.java)
    assertThat((result as ConfigUpdateResult.Rejected).reason).isEqualTo(ConfigRejectionReason.MINIMUM_VERSION_VIOLATION)
  }

  @Test
  fun `rollbackToVersion succeeds for stored version`() = runTest {
    manager.processConfigBundle(configBundle(1L), ConfigSource.MANAGED_CONFIG)
    nowMs += 120_000
    manager.processConfigBundle(configBundle(2L), ConfigSource.MANAGED_CONFIG)

    val result = manager.rollbackToVersion(1L)
    assertThat(result).isInstanceOf(ConfigRollbackResult.Success::class.java)
    assertThat((result as ConfigRollbackResult.Success).version.version).isEqualTo(1L)
  }

  @Test
  fun `rollbackToVersion blocked below minimum`() = runTest {
    policy = RemoteConfigPolicy(enabled = true, minimumConfigVersion = 5L)
    val result = manager.rollbackToVersion(3L)
    assertThat(result).isInstanceOf(ConfigRollbackResult.Blocked::class.java)
  }

  @Test
  fun `rollbackToVersion returns NotFound for unknown version`() = runTest {
    val result = manager.rollbackToVersion(999L)
    assertThat(result).isInstanceOf(ConfigRollbackResult.NotFound::class.java)
  }

  @Test
  fun `getActiveVersion returns null initially`() = runTest {
    assertThat(manager.getActiveVersion()).isNull()
  }

  @Test
  fun `getActiveVersion returns latest applied version`() = runTest {
    manager.processConfigBundle(configBundle(1L), ConfigSource.FCM)
    nowMs += 120_000
    manager.processConfigBundle(configBundle(2L), ConfigSource.MANAGED_CONFIG)

    val active = manager.getActiveVersion()
    assertThat(active).isNotNull()
    assertThat(active!!.version).isEqualTo(2L)
  }

  @Test
  fun `getAbVariant is deterministic for same input`() {
    val variants = listOf("control", "treatment_a", "treatment_b")
    val variant1 = manager.getAbVariant("experiment-1", variants)
    val variant2 = manager.getAbVariant("experiment-1", variants)
    assertThat(variant1).isEqualTo(variant2)
    assertThat(variant1).isIn(variants)
  }

  @Test
  fun `getAbVariant returns empty for empty variants`() {
    assertThat(manager.getAbVariant("exp", emptyList())).isEmpty()
  }

  @Test
  fun `processConfigBundle rejects missing version`() = runTest {
    val bundle = Bundle().apply { putString("key", "value") }
    val result = manager.processConfigBundle(bundle, ConfigSource.MANAGED_CONFIG)
    assertThat(result).isInstanceOf(ConfigUpdateResult.Rejected::class.java)
    assertThat((result as ConfigUpdateResult.Rejected).reason).isEqualTo(ConfigRejectionReason.PARSE_ERROR)
  }

  @Test
  fun `configUpdateFlow accepts bursts beyond buffer without hanging`() = runTest {
    // Burst more events than the 16-slot buffer can hold. With the default SUSPEND overflow
    // policy and a slow or absent collector, the emitter stalls; with DROP_OLDEST it rolls.
    // Advance the clock between bundles so each one is eligible past the apply cooldown.
    val burst = 50
    val interval = policy.configApplyCooldownMs + 1
    repeat(burst) { i ->
      nowMs += interval
      manager.processConfigBundle(
        configBundle(i.toLong() + 10L, mapOf("k" to "$i")),
        ConfigSource.MANAGED_CONFIG,
      )
    }

    // Manager processed every bundle without hanging; the latest won.
    val latest = db.configVersionDao().getActiveVersion()
    assertThat(latest).isNotNull()
    assertThat(latest!!.version).isEqualTo(10L + burst - 1)
  }
}
