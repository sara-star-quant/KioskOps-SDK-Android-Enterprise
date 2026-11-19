/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class SigningKeyIdentityTest {

  private fun newKey() = KeyPairGenerator.getInstance("EC").apply {
    initialize(ECGenParameterSpec("secp256r1"))
  }.generateKeyPair()

  private fun sign(key: java.security.PrivateKey, payload: String): String {
    val s = Signature.getInstance("SHA256withECDSA")
    s.initSign(key)
    s.update(payload.toByteArray(Charsets.UTF_8))
    return Base64.getEncoder().encodeToString(s.sign())
  }

  @Test
  fun `key id is 16 bytes of the public key hash in unpadded base64url`() {
    val spki = newKey().public.encoded
    val id = signingKeyIdOf(spki)
    assertThat(id.matches(Regex("[A-Za-z0-9_-]{22}"))).isTrue()
    assertThat(signingKeyIdOf(spki)).isEqualTo(id)
  }

  @Test
  fun `different keys get different ids`() {
    assertThat(signingKeyIdOf(newKey().public.encoded)).isNotEqualTo(signingKeyIdOf(newKey().public.encoded))
  }

  @Test
  fun `verify accepts a valid signature and rejects a changed payload or key`() {
    val a = newKey()
    val b = newKey()
    val sig = sign(a.private, "payload")

    assertThat(verifyEcdsaSha256(a.public.encoded, "payload", sig)).isTrue()
    assertThat(verifyEcdsaSha256(a.public.encoded, "payloaD", sig)).isFalse()
    assertThat(verifyEcdsaSha256(b.public.encoded, "payload", sig)).isFalse()
  }

  @Test
  fun `verify returns false for garbage instead of throwing`() {
    assertThat(verifyEcdsaSha256(byteArrayOf(1, 2, 3), "payload", "not-base64!")).isFalse()
    assertThat(verifyEcdsaSha256(newKey().public.encoded, "payload", "AAAA")).isFalse()
  }

  @Test
  fun `a provider that implements only the original members gets null and false defaults`() {
    val provider = object : DeviceAttestationProvider {
      override fun signAuditEntry(payload: String): String? = null
      override fun verifySignature(payload: String, signature: String) = false
      override fun getAttestationChain(): List<X509Certificate>? = null
      override val isHardwareBacked = false
      override fun getAttestationBlob(): ByteArray? = null
    }
    assertThat(provider.signingKeyId()).isNull()
    assertThat(provider.signingPublicKey()).isNull()
    assertThat(provider.verifyWithPublicKey(byteArrayOf(1), "p", "s")).isFalse()
  }
}
