/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit

import java.security.cert.X509Certificate

/**
 * Interface for signing audit entries with device attestation.
 *
 * Implementations should use hardware-backed keys when available
 * to provide the strongest security guarantees.
 *
 * The signature proves:
 * 1. The audit entry was created on a specific device
 * 2. The device has hardware-backed key protection
 * 3. The entry hasn't been modified since signing
 */
interface DeviceAttestationProvider {

  /**
   * Sign an audit entry payload.
   *
   * @param payload The audit entry content to sign (typically JSON).
   * @return Base64-encoded signature, or null if signing fails.
   */
  fun signAuditEntry(payload: String): String?

  /**
   * Verify a signature over an audit entry.
   *
   * @param payload The original audit entry content.
   * @param signature The Base64-encoded signature to verify.
   * @return True if the signature is valid.
   */
  fun verifySignature(payload: String, signature: String): Boolean

  /**
   * Get the attestation certificate chain.
   *
   * The chain provides proof that the signing key is hardware-backed.
   * It can be verified against Google's root certificates for
   * Android Key Attestation.
   *
   * @return List of certificates from leaf to root, or null if unavailable.
   */
  fun getAttestationChain(): List<X509Certificate>?

  /**
   * Check if this provider uses hardware-backed keys.
   */
  val isHardwareBacked: Boolean

  /**
   * Get the serialized attestation blob for storage.
   *
   * This includes the certificate chain in a format suitable for
   * storage and later verification.
   *
   * @return Serialized attestation data, or null if unavailable.
   */
  fun getAttestationBlob(): ByteArray?

  /**
   * Stable identifier of the current signing key, or null when the provider cannot name its key.
   * The audit trail records it so a replaced key shows up as a chain event.
   *
   * @since 1.6.1
   */
  fun signingKeyId(): String? = null

  /**
   * X.509 SubjectPublicKeyInfo of the current signing key, or null when unavailable. The audit trail
   * stores it when the key changes so older signatures stay verifiable.
   *
   * @since 1.6.1
   */
  fun signingPublicKey(): ByteArray? = null

  /**
   * Verify [signature] over [payload] with an explicit [publicKey] (X.509 SubjectPublicKeyInfo)
   * instead of the current key. Return false when the provider cannot.
   *
   * @since 1.6.1
   */
  fun verifyWithPublicKey(publicKey: ByteArray, payload: String, signature: String): Boolean = false
}
