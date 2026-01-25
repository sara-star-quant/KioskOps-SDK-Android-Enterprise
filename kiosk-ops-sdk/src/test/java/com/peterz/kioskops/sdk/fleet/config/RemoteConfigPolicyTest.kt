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
 * Tests for RemoteConfigPolicy.
 *
 * Security (BSI APP.4.4.A5): Validates policy defaults ensure safe configuration.
 */
@RunWith(RobolectricTestRunner::class)
class RemoteConfigPolicyTest {

  @Test
  fun `default policy is disabled`() {
    val policy = RemoteConfigPolicy()
    assertThat(policy.enabled).isFalse()
    assertThat(policy.abTestingEnabled).isFalse()
    assertThat(policy.requireSignedConfig).isFalse()
  }

  @Test
  fun `disabledDefaults returns disabled policy`() {
    val policy = RemoteConfigPolicy.disabledDefaults()
    assertThat(policy.enabled).isFalse()
  }

  @Test
  fun `enterpriseDefaults enables remote config with signing`() {
    val policy = RemoteConfigPolicy.enterpriseDefaults()
    assertThat(policy.enabled).isTrue()
    assertThat(policy.requireSignedConfig).isTrue()
    assertThat(policy.minimumConfigVersion).isEqualTo(1L)
    assertThat(policy.maxRetainedVersions).isEqualTo(5)
    assertThat(policy.configApplyCooldownMs).isEqualTo(60_000L)
  }

  @Test
  fun `pilotDefaults enables AB testing`() {
    val policy = RemoteConfigPolicy.pilotDefaults()
    assertThat(policy.enabled).isTrue()
    assertThat(policy.requireSignedConfig).isFalse()
    assertThat(policy.abTestingEnabled).isTrue()
    assertThat(policy.maxRetainedVersions).isEqualTo(10)
  }

  @Test
  fun `default minimum version floor is zero`() {
    val policy = RemoteConfigPolicy()
    assertThat(policy.minimumConfigVersion).isEqualTo(0L)
  }

  @Test
  fun `custom minimum version floor`() {
    val policy = RemoteConfigPolicy(
      enabled = true,
      minimumConfigVersion = 100L,
    )
    assertThat(policy.minimumConfigVersion).isEqualTo(100L)
  }

  @Test
  fun `config signing with public key`() {
    val publicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE..."
    val policy = RemoteConfigPolicy(
      enabled = true,
      requireSignedConfig = true,
      configSigningPublicKey = publicKey,
    )
    assertThat(policy.requireSignedConfig).isTrue()
    assertThat(policy.configSigningPublicKey).isEqualTo(publicKey)
  }

  @Test
  fun `sticky variant assignment enabled by default`() {
    val policy = RemoteConfigPolicy(
      enabled = true,
      abTestingEnabled = true,
    )
    assertThat(policy.stickyVariantAssignment).isTrue()
  }

  @Test
  fun `can disable sticky variant assignment`() {
    val policy = RemoteConfigPolicy(
      enabled = true,
      abTestingEnabled = true,
      stickyVariantAssignment = false,
    )
    assertThat(policy.stickyVariantAssignment).isFalse()
  }

  @Test
  fun `cooldown defaults to 60 seconds`() {
    val policy = RemoteConfigPolicy()
    assertThat(policy.configApplyCooldownMs).isEqualTo(60_000L)
  }

  @Test
  fun `retained versions defaults to 5`() {
    val policy = RemoteConfigPolicy()
    assertThat(policy.maxRetainedVersions).isEqualTo(5)
  }

  @Test
  fun `custom retained versions`() {
    val policy = RemoteConfigPolicy(
      enabled = true,
      maxRetainedVersions = 20,
    )
    assertThat(policy.maxRetainedVersions).isEqualTo(20)
  }

  @Test
  fun `custom cooldown`() {
    val policy = RemoteConfigPolicy(
      enabled = true,
      configApplyCooldownMs = 120_000L,
    )
    assertThat(policy.configApplyCooldownMs).isEqualTo(120_000L)
  }
}
