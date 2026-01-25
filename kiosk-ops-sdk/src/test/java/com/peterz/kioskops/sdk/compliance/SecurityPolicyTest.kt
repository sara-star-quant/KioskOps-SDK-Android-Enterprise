/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.compliance

import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.crypto.KeyDerivationConfig
import com.peterz.kioskops.sdk.crypto.KeyRotationPolicy
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecurityPolicyTest {

  @Test
  fun `maximalistDefaults includes new v020 fields`() {
    val policy = SecurityPolicy.maximalistDefaults()

    // Existing fields
    assertThat(policy.encryptQueuePayloads).isTrue()
    assertThat(policy.encryptTelemetryAtRest).isTrue()
    assertThat(policy.encryptDiagnosticsBundle).isTrue()
    assertThat(policy.encryptExportedLogs).isTrue()
    assertThat(policy.maxEventPayloadBytes).isEqualTo(64 * 1024)
    assertThat(policy.allowRawPayloadStorage).isFalse()

    // New v0.2.0 fields
    assertThat(policy.keyRotationPolicy).isNotNull()
    assertThat(policy.keyDerivationConfig).isNotNull()
    assertThat(policy.useRoomBackedAudit).isTrue()
    assertThat(policy.signAuditEntries).isFalse()
  }

  @Test
  fun `highSecurityDefaults enables signing and stricter rotation`() {
    val policy = SecurityPolicy.highSecurityDefaults()

    assertThat(policy.signAuditEntries).isTrue()
    assertThat(policy.keyRotationPolicy.maxKeyAgeDays).isEqualTo(90)
    assertThat(policy.keyRotationPolicy.autoRotateEnabled).isTrue()
    assertThat(policy.keyDerivationConfig.iterationCount).isEqualTo(600_000)
    assertThat(policy.denylistJsonKeys).contains("credit_card")
  }

  @Test
  fun `custom policy with key rotation`() {
    val rotationPolicy = KeyRotationPolicy(
      maxKeyAgeDays = 180,
      autoRotateEnabled = true,
    )
    val policy = SecurityPolicy.maximalistDefaults().copy(
      keyRotationPolicy = rotationPolicy,
    )

    assertThat(policy.keyRotationPolicy.maxKeyAgeDays).isEqualTo(180)
    assertThat(policy.keyRotationPolicy.autoRotateEnabled).isTrue()
  }

  @Test
  fun `custom policy with key derivation`() {
    val derivationConfig = KeyDerivationConfig.highSecurity()
    val policy = SecurityPolicy.maximalistDefaults().copy(
      keyDerivationConfig = derivationConfig,
    )

    assertThat(policy.keyDerivationConfig.algorithm).isEqualTo("PBKDF2WithHmacSHA512")
    assertThat(policy.keyDerivationConfig.iterationCount).isEqualTo(600_000)
  }

  @Test
  fun `policy with signed audit entries`() {
    val policy = SecurityPolicy.maximalistDefaults().copy(
      signAuditEntries = true,
    )

    assertThat(policy.signAuditEntries).isTrue()
  }

  @Test
  fun `policy defaults enable room-backed audit`() {
    val policy = SecurityPolicy.maximalistDefaults()
    assertThat(policy.useRoomBackedAudit).isTrue()
  }

  @Test
  fun `policy can disable room-backed audit`() {
    val policy = SecurityPolicy.maximalistDefaults().copy(
      useRoomBackedAudit = false,
    )
    assertThat(policy.useRoomBackedAudit).isFalse()
  }
}
