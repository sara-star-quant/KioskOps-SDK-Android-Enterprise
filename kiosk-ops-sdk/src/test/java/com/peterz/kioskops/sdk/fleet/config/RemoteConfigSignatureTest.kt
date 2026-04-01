/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet.config

import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.audit.AuditTrail
import com.peterz.kioskops.sdk.compliance.RetentionPolicy
import com.peterz.kioskops.sdk.crypto.NoopCryptoProvider
import com.peterz.kioskops.sdk.util.Clock
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
class RemoteConfigSignatureTest {

  private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

  private val keyPair = KeyPairGenerator.getInstance("EC").apply {
    initialize(ECGenParameterSpec("secp256r1"))
  }.generateKeyPair()

  private val publicKeyBase64: String = Base64.getEncoder().encodeToString(keyPair.public.encoded)

  private fun signContent(content: String): String {
    val signer = Signature.getInstance("SHA256withECDSA")
    signer.initSign(keyPair.private)
    signer.update(content.toByteArray(Charsets.UTF_8))
    return Base64.getEncoder().encodeToString(signer.sign())
  }

  private fun createManager(requireSigned: Boolean, publicKey: String? = publicKeyBase64): RemoteConfigManager {
    val policy = RemoteConfigPolicy(
      enabled = true,
      requireSignedConfig = requireSigned,
      configSigningPublicKey = publicKey,
      minimumConfigVersion = 0,
      maxRetainedVersions = 10,
    )
    val auditTrail = AuditTrail(ctx, { RetentionPolicy.maximalistDefaults() }, Clock.SYSTEM, NoopCryptoProvider)
    return RemoteConfigManager.create(
      context = ctx,
      policyProvider = { policy },
      auditTrail = auditTrail,
      clock = Clock.SYSTEM,
    )
  }

  @Test
  fun `accepts bundle with valid ECDSA signature`() = runBlocking {
    val manager = createManager(requireSigned = true)

    val bundle = Bundle().apply {
      putLong(RemoteConfigManager.KEY_CONFIG_VERSION, 1L)
    }

    // Compute what the manager will serialize, then sign it
    val serialized = serializeBundleForSigning(bundle)
    val signature = signContent(serialized)
    bundle.putString(RemoteConfigManager.KEY_CONFIG_SIGNATURE, signature)

    val result = manager.processConfigBundle(bundle, ConfigSource.MANAGED_CONFIG)
    assertThat(result).isInstanceOf(ConfigUpdateResult.Applied::class.java)
  }

  @Test
  fun `rejects bundle with invalid signature`() = runBlocking {
    val manager = createManager(requireSigned = true)

    val bundle = Bundle().apply {
      putLong(RemoteConfigManager.KEY_CONFIG_VERSION, 2L)
      putString(RemoteConfigManager.KEY_CONFIG_SIGNATURE, "aW52YWxpZC1zaWduYXR1cmU=")
    }

    val result = manager.processConfigBundle(bundle, ConfigSource.MANAGED_CONFIG)
    assertThat(result).isInstanceOf(ConfigUpdateResult.Rejected::class.java)
    val rejected = result as ConfigUpdateResult.Rejected
    assertThat(rejected.reason).isEqualTo(ConfigRejectionReason.SIGNATURE_INVALID)
  }

  @Test
  fun `rejects bundle with missing signature when required`() = runBlocking {
    val manager = createManager(requireSigned = true)

    val bundle = Bundle().apply {
      putLong(RemoteConfigManager.KEY_CONFIG_VERSION, 3L)
    }

    val result = manager.processConfigBundle(bundle, ConfigSource.MANAGED_CONFIG)
    assertThat(result).isInstanceOf(ConfigUpdateResult.Rejected::class.java)
    val rejected = result as ConfigUpdateResult.Rejected
    assertThat(rejected.reason).isEqualTo(ConfigRejectionReason.SIGNATURE_INVALID)
  }

  @Test
  fun `accepts bundle without signature when not required`() = runBlocking {
    val manager = createManager(requireSigned = false)

    val bundle = Bundle().apply {
      putLong(RemoteConfigManager.KEY_CONFIG_VERSION, 4L)
    }

    val result = manager.processConfigBundle(bundle, ConfigSource.MANAGED_CONFIG)
    assertThat(result).isInstanceOf(ConfigUpdateResult.Applied::class.java)
  }

  @Test
  fun `rejects bundle with wrong key`() = runBlocking {
    // Generate a different key pair
    val otherKeyPair = KeyPairGenerator.getInstance("EC").apply {
      initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    val manager = createManager(
      requireSigned = true,
      publicKey = Base64.getEncoder().encodeToString(otherKeyPair.public.encoded),
    )

    val bundle = Bundle().apply {
      putLong(RemoteConfigManager.KEY_CONFIG_VERSION, 5L)
    }

    val serialized = serializeBundleForSigning(bundle)
    val signature = signContent(serialized) // signed with original key, not otherKeyPair
    bundle.putString(RemoteConfigManager.KEY_CONFIG_SIGNATURE, signature)

    val result = manager.processConfigBundle(bundle, ConfigSource.MANAGED_CONFIG)
    assertThat(result).isInstanceOf(ConfigUpdateResult.Rejected::class.java)
  }

  /** Replicate the manager's serialization logic for signing (excludes signature key). */
  private fun serializeBundleForSigning(bundle: Bundle): String {
    val map = mutableMapOf<String, String>()
    for (key in bundle.keySet()) {
      if (key == RemoteConfigManager.KEY_CONFIG_SIGNATURE) continue
      @Suppress("DEPRECATION")
      val value = bundle.getString(key) ?: bundle.get(key)?.toString()
      if (value != null) {
        map[key] = value
      }
    }
    return kotlinx.serialization.json.Json {
      ignoreUnknownKeys = true
      explicitNulls = false
    }.encodeToString(kotlinx.serialization.serializer(), map)
  }

}
