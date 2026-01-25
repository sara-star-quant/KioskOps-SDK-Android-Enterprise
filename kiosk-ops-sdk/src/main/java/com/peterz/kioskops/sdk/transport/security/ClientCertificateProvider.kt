/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.transport.security

import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * Provider interface for client certificates in mutual TLS authentication.
 *
 * Implementations may retrieve certificates from:
 * - Android Keystore (hardware-backed)
 * - File system (PKCS#12 bundles)
 * - Managed configuration (EMM/MDM provisioned)
 * - Remote certificate services
 *
 * The provider is called during TLS handshake, so implementations
 * should cache credentials where appropriate for performance.
 */
fun interface ClientCertificateProvider {
  /**
   * Returns the client certificate and private key for mTLS authentication.
   *
   * @return Certificate credentials, or null if no certificate is available.
   *         Returning null will cause mTLS authentication to fail.
   */
  fun getCertificateAndKey(): CertificateCredentials?
}

/**
 * Client certificate credentials for mTLS.
 *
 * @property certificate The X.509 client certificate to present during handshake.
 * @property privateKey The private key corresponding to the certificate's public key.
 *           Must be compatible with the certificate's key algorithm (RSA, EC, etc.).
 * @property certificateChain Optional intermediate certificates to send with the
 *           client certificate. The chain should be ordered from leaf to root.
 */
data class CertificateCredentials(
  val certificate: X509Certificate,
  val privateKey: PrivateKey,
  val certificateChain: List<X509Certificate> = emptyList(),
)
