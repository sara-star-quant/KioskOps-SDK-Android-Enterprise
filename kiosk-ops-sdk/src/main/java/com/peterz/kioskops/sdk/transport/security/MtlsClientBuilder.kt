/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.transport.security

import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Builder for OkHttpClient with mutual TLS (mTLS) support.
 *
 * Configures the HTTP client to present a client certificate during
 * TLS handshake, enabling two-way authentication where both the
 * client and server verify each other's identity.
 *
 * Usage:
 * ```kotlin
 * val client = MtlsClientBuilder.build(
 *   baseClient = existingClient,
 *   mtlsConfig = MtlsConfig(provider),
 * )
 * ```
 */
object MtlsClientBuilder {

  /**
   * Build an OkHttpClient configured for mutual TLS.
   *
   * @param baseClient The base OkHttpClient to configure. The returned client
   *        will inherit all settings from the base client.
   * @param mtlsConfig The mTLS configuration with client certificate provider.
   * @return A new OkHttpClient configured for mTLS, or the base client if
   *         no valid credentials are available.
   */
  fun build(
    baseClient: OkHttpClient,
    mtlsConfig: MtlsConfig,
  ): OkHttpClient {
    val credentials = mtlsConfig.clientCertificateProvider.getCertificateAndKey()
      ?: return baseClient // No credentials available, return unmodified client

    return try {
      val sslContext = createSslContext(credentials)
      val trustManager = getDefaultTrustManager()

      baseClient.newBuilder()
        .sslSocketFactory(sslContext.socketFactory, trustManager)
        .build()
    } catch (e: Exception) {
      // If mTLS setup fails, return the base client
      // The caller should handle authentication failures at the HTTP level
      baseClient
    }
  }

  private fun createSslContext(credentials: CertificateCredentials): SSLContext {
    // Create a KeyStore containing the client certificate and key
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
      load(null, null)

      // Build the certificate chain (leaf + intermediates)
      val chain = arrayOf(credentials.certificate) +
        credentials.certificateChain.toTypedArray()

      setKeyEntry(
        "client",
        credentials.privateKey,
        charArrayOf(), // Empty password for in-memory keystore
        chain,
      )
    }

    // Initialize KeyManagerFactory with the client certificate
    val keyManagerFactory = KeyManagerFactory.getInstance(
      KeyManagerFactory.getDefaultAlgorithm()
    ).apply {
      init(keyStore, charArrayOf())
    }

    // Create SSLContext with client key manager and default trust manager
    return SSLContext.getInstance("TLS").apply {
      init(
        keyManagerFactory.keyManagers,
        null, // Use default trust managers
        SecureRandom(),
      )
    }
  }

  private fun getDefaultTrustManager(): X509TrustManager {
    val trustManagerFactory = TrustManagerFactory.getInstance(
      TrustManagerFactory.getDefaultAlgorithm()
    ).apply {
      init(null as KeyStore?) // Use default trust store
    }

    return trustManagerFactory.trustManagers
      .filterIsInstance<X509TrustManager>()
      .first()
  }

  /**
   * Create an mTLS-enabled OkHttpClient from a TransportSecurityPolicy.
   *
   * @param baseClient The base client to configure.
   * @param policy The transport security policy.
   * @return A new client configured for mTLS if policy has mTLS config,
   *         otherwise returns the base client.
   */
  fun fromPolicy(
    baseClient: OkHttpClient,
    policy: TransportSecurityPolicy,
  ): OkHttpClient {
    val mtlsConfig = policy.mtlsConfig ?: return baseClient
    return build(baseClient, mtlsConfig)
  }
}
