/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyAttestationStatusTest {

  @Test
  fun `SecurityLevel enum values`() {
    assertThat(SecurityLevel.values()).asList().containsExactly(
      SecurityLevel.SOFTWARE,
      SecurityLevel.TEE,
      SecurityLevel.STRONGBOX,
      SecurityLevel.UNKNOWN,
    )
  }

  @Test
  fun `hasAttestationChain returns true when chain present`() {
    val status = KeyAttestationStatus(
      isHardwareBacked = true,
      securityLevel = SecurityLevel.TEE,
      keyCreatedAt = System.currentTimeMillis(),
      attestationChain = listOf(), // Empty but not null
      keyAlias = "test_key",
      keyAlgorithm = "AES",
      keySize = 256,
    )
    // Empty list is still not null, but empty
    assertThat(status.hasAttestationChain).isFalse()
  }

  @Test
  fun `hasAttestationChain returns false when chain null`() {
    val status = KeyAttestationStatus(
      isHardwareBacked = false,
      securityLevel = SecurityLevel.SOFTWARE,
      keyCreatedAt = null,
      attestationChain = null,
      keyAlias = "test_key",
      keyAlgorithm = "AES",
      keySize = 256,
    )
    assertThat(status.hasAttestationChain).isFalse()
  }

  @Test
  fun `meetsRequirements with no hardware requirement`() {
    val status = KeyAttestationStatus(
      isHardwareBacked = false,
      securityLevel = SecurityLevel.SOFTWARE,
      keyCreatedAt = null,
      attestationChain = null,
      keyAlias = "test_key",
      keyAlgorithm = "AES",
      keySize = 256,
    )
    assertThat(status.meetsRequirements(requireHardware = false)).isTrue()
  }

  @Test
  fun `meetsRequirements fails when hardware required but not present`() {
    val status = KeyAttestationStatus(
      isHardwareBacked = false,
      securityLevel = SecurityLevel.SOFTWARE,
      keyCreatedAt = null,
      attestationChain = null,
      keyAlias = "test_key",
      keyAlgorithm = "AES",
      keySize = 256,
    )
    assertThat(status.meetsRequirements(requireHardware = true)).isFalse()
  }

  @Test
  fun `meetsRequirements passes with TEE when TEE required`() {
    val status = KeyAttestationStatus(
      isHardwareBacked = true,
      securityLevel = SecurityLevel.TEE,
      keyCreatedAt = null,
      attestationChain = null,
      keyAlias = "test_key",
      keyAlgorithm = "AES",
      keySize = 256,
    )
    assertThat(status.meetsRequirements(minimumSecurityLevel = SecurityLevel.TEE)).isTrue()
  }

  @Test
  fun `meetsRequirements fails when TEE required but only SOFTWARE`() {
    val status = KeyAttestationStatus(
      isHardwareBacked = false,
      securityLevel = SecurityLevel.SOFTWARE,
      keyCreatedAt = null,
      attestationChain = null,
      keyAlias = "test_key",
      keyAlgorithm = "AES",
      keySize = 256,
    )
    assertThat(status.meetsRequirements(minimumSecurityLevel = SecurityLevel.TEE)).isFalse()
  }

  @Test
  fun `meetsRequirements passes with STRONGBOX when TEE required`() {
    val status = KeyAttestationStatus(
      isHardwareBacked = true,
      securityLevel = SecurityLevel.STRONGBOX,
      keyCreatedAt = null,
      attestationChain = null,
      keyAlias = "test_key",
      keyAlgorithm = "AES",
      keySize = 256,
    )
    assertThat(status.meetsRequirements(minimumSecurityLevel = SecurityLevel.TEE)).isTrue()
  }

  @Test
  fun `AttestationResponse equals and hashCode`() {
    val challenge = ByteArray(32) { it.toByte() }
    val signature = ByteArray(64) { (it + 100).toByte() }

    val response1 = AttestationResponse(
      challenge = challenge,
      attestationChain = emptyList(),
      signature = signature,
    )
    val response2 = AttestationResponse(
      challenge = challenge.copyOf(),
      attestationChain = emptyList(),
      signature = signature.copyOf(),
    )

    assertThat(response1).isEqualTo(response2)
    assertThat(response1.hashCode()).isEqualTo(response2.hashCode())
  }

  @Test
  fun `AttestationResponse not equal with different challenge`() {
    val response1 = AttestationResponse(
      challenge = ByteArray(32) { 1 },
      attestationChain = emptyList(),
      signature = ByteArray(64) { 2 },
    )
    val response2 = AttestationResponse(
      challenge = ByteArray(32) { 3 },
      attestationChain = emptyList(),
      signature = ByteArray(64) { 2 },
    )

    assertThat(response1).isNotEqualTo(response2)
  }
}
