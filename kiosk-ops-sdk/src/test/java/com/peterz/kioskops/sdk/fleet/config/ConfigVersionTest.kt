/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for ConfigVersion, ConfigUpdateResult, and ConfigRollbackResult.
 *
 * Security (BSI APP.4.4.A5): Validates version monotonicity support.
 */
@RunWith(RobolectricTestRunner::class)
class ConfigVersionTest {

  @Test
  fun `ConfigVersion contains all fields`() {
    val version = ConfigVersion(
      version = 5L,
      createdAtMs = 1700000000000L,
      contentHash = "abc123",
      source = ConfigSource.MANAGED_CONFIG,
      abVariant = "variant-a",
      signature = "sig123",
    )
    assertThat(version.version).isEqualTo(5L)
    assertThat(version.createdAtMs).isEqualTo(1700000000000L)
    assertThat(version.contentHash).isEqualTo("abc123")
    assertThat(version.source).isEqualTo(ConfigSource.MANAGED_CONFIG)
    assertThat(version.abVariant).isEqualTo("variant-a")
    assertThat(version.signature).isEqualTo("sig123")
  }

  @Test
  fun `ConfigVersion optional fields default to null`() {
    val version = ConfigVersion(
      version = 1L,
      createdAtMs = 1700000000000L,
      contentHash = "abc123",
      source = ConfigSource.EMBEDDED,
    )
    assertThat(version.abVariant).isNull()
    assertThat(version.signature).isNull()
  }

  @Test
  fun `ConfigSource has all expected values`() {
    val sources = ConfigSource.values()
    assertThat(sources).hasLength(4)
    assertThat(sources).asList().containsExactly(
      ConfigSource.EMBEDDED,
      ConfigSource.MANAGED_CONFIG,
      ConfigSource.FCM,
      ConfigSource.ROLLBACK,
    )
  }

  @Test
  fun `ConfigUpdateResult Applied contains version`() {
    val version = ConfigVersion(
      version = 10L,
      createdAtMs = 1700000000000L,
      contentHash = "hash123",
      source = ConfigSource.MANAGED_CONFIG,
    )
    val result = ConfigUpdateResult.Applied(version = version)
    assertThat(result.version).isEqualTo(version)
    assertThat(result.version.version).isEqualTo(10L)
  }

  @Test
  fun `ConfigUpdateResult Rejected contains reason`() {
    val result = ConfigUpdateResult.Rejected(
      reason = ConfigRejectionReason.VERSION_TOO_OLD,
    )
    assertThat(result.reason).isEqualTo(ConfigRejectionReason.VERSION_TOO_OLD)
  }

  @Test
  fun `ConfigRejectionReason has all expected values`() {
    val reasons = ConfigRejectionReason.values()
    assertThat(reasons).hasLength(6)
    assertThat(reasons).asList().containsExactly(
      ConfigRejectionReason.DISABLED,
      ConfigRejectionReason.VERSION_TOO_OLD,
      ConfigRejectionReason.SIGNATURE_INVALID,
      ConfigRejectionReason.COOLDOWN_ACTIVE,
      ConfigRejectionReason.MINIMUM_VERSION_VIOLATION,
      ConfigRejectionReason.PARSE_ERROR,
    )
  }

  @Test
  fun `ConfigRollbackResult Success contains version`() {
    val version = ConfigVersion(
      version = 5L,
      createdAtMs = 1700000000000L,
      contentHash = "hash123",
      source = ConfigSource.ROLLBACK,
    )
    val result = ConfigRollbackResult.Success(version = version)
    assertThat(result.version).isEqualTo(version)
  }

  @Test
  fun `ConfigRollbackResult Blocked contains reason`() {
    val result = ConfigRollbackResult.Blocked(reason = "Version below minimum")
    assertThat(result.reason).isEqualTo("Version below minimum")
  }

  @Test
  fun `ConfigRollbackResult NotFound contains requested version`() {
    val result = ConfigRollbackResult.NotFound(requestedVersion = 3L)
    assertThat(result.requestedVersion).isEqualTo(3L)
  }

  @Test
  fun `ConfigVersion copy preserves fields`() {
    val original = ConfigVersion(
      version = 5L,
      createdAtMs = 1700000000000L,
      contentHash = "abc123",
      source = ConfigSource.MANAGED_CONFIG,
    )
    val modified = original.copy(version = 6L)
    assertThat(modified.version).isEqualTo(6L)
    assertThat(modified.createdAtMs).isEqualTo(1700000000000L)
    assertThat(modified.contentHash).isEqualTo("abc123")
    assertThat(modified.source).isEqualTo(ConfigSource.MANAGED_CONFIG)
  }
}
