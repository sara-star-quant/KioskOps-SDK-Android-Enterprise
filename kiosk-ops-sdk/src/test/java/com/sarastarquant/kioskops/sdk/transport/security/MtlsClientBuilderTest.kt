/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.transport.security

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.X509TrustManager

@RunWith(RobolectricTestRunner::class)
class MtlsClientBuilderTest {

  // ---------------------------------------------------------------
  // Helper: generate a self-signed X509Certificate by invoking
  // keytool to produce a PKCS12 keystore, then extracting the
  // certificate and private key from it.
  // ---------------------------------------------------------------

  private fun generateSelfSignedCert(cn: String = "CN=Test"): Pair<X509Certificate, PrivateKey> {
    val password = "changeit".toCharArray()
    val alias = "testkey"
    val keystoreFile = File.createTempFile("test-keystore-", ".p12")
    keystoreFile.deleteOnExit()
    keystoreFile.delete()

    val process = ProcessBuilder(
      "keytool",
      "-genkeypair",
      "-alias", alias,
      "-keyalg", "RSA",
      "-keysize", "2048",
      "-validity", "365",
      "-dname", cn,
      "-storetype", "PKCS12",
      "-keystore", keystoreFile.absolutePath,
      "-storepass", String(password),
      "-keypass", String(password),
    ).redirectErrorStream(true).start()

    val exitCode = process.waitFor()
    check(exitCode == 0) {
      "keytool failed (exit $exitCode): ${process.inputStream.bufferedReader().readText()}"
    }

    val keyStore = KeyStore.getInstance("PKCS12")
    keystoreFile.inputStream().use { keyStore.load(it, password) }

    val cert = keyStore.getCertificate(alias) as X509Certificate
    val key = keyStore.getKey(alias, password) as PrivateKey

    keystoreFile.delete()
    return Pair(cert, key)
  }

  /**
   * Generate a CA certificate and a leaf certificate signed by that CA.
   * Returns (leafCert, leafKey, caCert) -- the caCert serves as an
   * intermediate in the chain.
   */
  @Suppress("LongMethod")
  private fun generateSignedCertChain(): Triple<X509Certificate, PrivateKey, X509Certificate> {
    val password = "changeit".toCharArray()
    val caAlias = "ca"
    val leafAlias = "leaf"
    val keystoreFile = File.createTempFile("chain-keystore-", ".p12")
    val certFile = File.createTempFile("ca-cert-", ".pem")
    keystoreFile.deleteOnExit()
    certFile.deleteOnExit()
    keystoreFile.delete()

    // Step 1: Generate CA key pair
    val genCaProcess = ProcessBuilder(
      "keytool", "-genkeypair",
      "-alias", caAlias,
      "-keyalg", "RSA", "-keysize", "2048",
      "-validity", "365",
      "-dname", "CN=Test CA, O=Test, C=US",
      "-ext", "bc:c",
      "-storetype", "PKCS12",
      "-keystore", keystoreFile.absolutePath,
      "-storepass", String(password),
      "-keypass", String(password),
    ).redirectErrorStream(true).start()
    check(genCaProcess.waitFor() == 0) {
      "CA keygen failed: ${genCaProcess.inputStream.bufferedReader().readText()}"
    }

    // Step 2: Generate leaf key pair
    val genLeafProcess = ProcessBuilder(
      "keytool", "-genkeypair",
      "-alias", leafAlias,
      "-keyalg", "RSA", "-keysize", "2048",
      "-validity", "365",
      "-dname", "CN=Test Leaf, O=Test, C=US",
      "-storetype", "PKCS12",
      "-keystore", keystoreFile.absolutePath,
      "-storepass", String(password),
      "-keypass", String(password),
    ).redirectErrorStream(true).start()
    check(genLeafProcess.waitFor() == 0) {
      "Leaf keygen failed: ${genLeafProcess.inputStream.bufferedReader().readText()}"
    }

    // Step 3: Export CA cert
    val exportProcess = ProcessBuilder(
      "keytool", "-exportcert",
      "-alias", caAlias,
      "-keystore", keystoreFile.absolutePath,
      "-storepass", String(password),
      "-rfc",
      "-file", certFile.absolutePath,
    ).redirectErrorStream(true).start()
    check(exportProcess.waitFor() == 0) {
      "CA export failed: ${exportProcess.inputStream.bufferedReader().readText()}"
    }

    // Step 4: Create CSR for leaf
    val csrFile = File.createTempFile("leaf-csr-", ".pem")
    csrFile.deleteOnExit()
    val csrProcess = ProcessBuilder(
      "keytool", "-certreq",
      "-alias", leafAlias,
      "-keystore", keystoreFile.absolutePath,
      "-storepass", String(password),
      "-file", csrFile.absolutePath,
    ).redirectErrorStream(true).start()
    check(csrProcess.waitFor() == 0) {
      "CSR failed: ${csrProcess.inputStream.bufferedReader().readText()}"
    }

    // Step 5: Sign the leaf cert with CA
    val signedCertFile = File.createTempFile("signed-cert-", ".pem")
    signedCertFile.deleteOnExit()
    val signProcess = ProcessBuilder(
      "keytool", "-gencert",
      "-alias", caAlias,
      "-keystore", keystoreFile.absolutePath,
      "-storepass", String(password),
      "-infile", csrFile.absolutePath,
      "-outfile", signedCertFile.absolutePath,
      "-rfc",
      "-validity", "365",
    ).redirectErrorStream(true).start()
    check(signProcess.waitFor() == 0) {
      "Signing failed: ${signProcess.inputStream.bufferedReader().readText()}"
    }

    // Step 6: Import the CA cert as trusted, then import the signed leaf cert
    val importCaProcess = ProcessBuilder(
      "keytool", "-importcert",
      "-alias", "trustedca",
      "-keystore", keystoreFile.absolutePath,
      "-storepass", String(password),
      "-file", certFile.absolutePath,
      "-noprompt",
    ).redirectErrorStream(true).start()
    check(importCaProcess.waitFor() == 0) {
      "CA import failed: ${importCaProcess.inputStream.bufferedReader().readText()}"
    }

    val importLeafProcess = ProcessBuilder(
      "keytool", "-importcert",
      "-alias", leafAlias,
      "-keystore", keystoreFile.absolutePath,
      "-storepass", String(password),
      "-file", signedCertFile.absolutePath,
    ).redirectErrorStream(true).start()
    check(importLeafProcess.waitFor() == 0) {
      "Leaf import failed: ${importLeafProcess.inputStream.bufferedReader().readText()}"
    }

    // Load the results
    val keyStore = KeyStore.getInstance("PKCS12")
    keystoreFile.inputStream().use { keyStore.load(it, password) }

    val leafCert = keyStore.getCertificate(leafAlias) as X509Certificate
    val leafKey = keyStore.getKey(leafAlias, password) as PrivateKey
    val caCert = keyStore.getCertificate("trustedca") as X509Certificate

    // Cleanup
    keystoreFile.delete()
    certFile.delete()
    csrFile.delete()
    signedCertFile.delete()

    return Triple(leafCert, leafKey, caCert)
  }

  private fun createBaseClient(): OkHttpClient {
    return OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .build()
  }

  // ---------------------------------------------------------------
  // fromPolicy() tests
  // ---------------------------------------------------------------

  @Test
  fun `fromPolicy returns base client when no mtlsConfig`() {
    val baseClient = createBaseClient()
    val policy = TransportSecurityPolicy(mtlsConfig = null)

    val result = MtlsClientBuilder.fromPolicy(baseClient, policy)
    assertThat(result).isSameInstanceAs(baseClient)
  }

  @Test
  fun `fromPolicy returns enhanced client when mtlsConfig present with valid credentials`() {
    val baseClient = createBaseClient()
    val (cert, privateKey) = generateSelfSignedCert()

    val provider = ClientCertificateProvider {
      CertificateCredentials(
        certificate = cert,
        privateKey = privateKey,
      )
    }
    val policy = TransportSecurityPolicy(
      mtlsConfig = MtlsConfig(clientCertificateProvider = provider),
    )

    val result = MtlsClientBuilder.fromPolicy(baseClient, policy)
    assertThat(result).isNotSameInstanceAs(baseClient)
  }

  @Test
  fun `fromPolicy throws when provider returns null credentials`() {
    val baseClient = createBaseClient()
    val provider = ClientCertificateProvider { null }
    val policy = TransportSecurityPolicy(
      mtlsConfig = MtlsConfig(clientCertificateProvider = provider),
    )

    // mTLS must never silently downgrade to unauthenticated TLS.
    var threw = false
    try {
      MtlsClientBuilder.fromPolicy(baseClient, policy)
    } catch (_: MtlsConfigurationException) {
      threw = true
    }
    assertThat(threw).isTrue()
  }

  // ---------------------------------------------------------------
  // build() tests
  // ---------------------------------------------------------------

  @Test
  fun `build throws when credentials are null`() {
    val baseClient = createBaseClient()
    val provider = ClientCertificateProvider { null }
    val config = MtlsConfig(clientCertificateProvider = provider)

    var threw = false
    try {
      MtlsClientBuilder.build(baseClient, config)
    } catch (_: MtlsConfigurationException) {
      threw = true
    }
    assertThat(threw).isTrue()
  }

  @Test
  fun `build creates new client with SSL context when credentials valid`() {
    val baseClient = createBaseClient()
    val (cert, privateKey) = generateSelfSignedCert()

    val provider = ClientCertificateProvider {
      CertificateCredentials(
        certificate = cert,
        privateKey = privateKey,
      )
    }
    val config = MtlsConfig(clientCertificateProvider = provider)

    val result = MtlsClientBuilder.build(baseClient, config)
    assertThat(result).isNotSameInstanceAs(baseClient)
    assertThat(result.sslSocketFactory).isNotSameInstanceAs(baseClient.sslSocketFactory)
  }

  @Test
  fun `build throws on exception during SSL setup`() {
    val baseClient = createBaseClient()

    // Broken private key whose getEncoded() returns null triggers an exception
    // in PKCS12 KeyStore.setKeyEntry. The SDK must surface this rather than
    // silently downgrading to unauthenticated TLS.
    val (cert, _) = generateSelfSignedCert(cn = "CN=Test")
    val brokenKey = object : PrivateKey {
      override fun getAlgorithm(): String = "RSA"
      override fun getFormat(): String = "PKCS#8"
      override fun getEncoded(): ByteArray? = null
      override fun destroy() = Unit
      override fun isDestroyed(): Boolean = false
    }

    val provider = ClientCertificateProvider {
      CertificateCredentials(
        certificate = cert,
        privateKey = brokenKey,
      )
    }

    val config = MtlsConfig(clientCertificateProvider = provider)
    var threw = false
    try {
      MtlsClientBuilder.build(baseClient, config)
    } catch (_: MtlsConfigurationException) {
      threw = true
    }
    assertThat(threw).isTrue()
  }

  @Test
  fun `build preserves base client settings`() {
    val baseClient = OkHttpClient.Builder()
      .connectTimeout(42, TimeUnit.SECONDS)
      .readTimeout(33, TimeUnit.SECONDS)
      .writeTimeout(21, TimeUnit.SECONDS)
      .build()

    val (cert, privateKey) = generateSelfSignedCert()
    val provider = ClientCertificateProvider {
      CertificateCredentials(certificate = cert, privateKey = privateKey)
    }
    val config = MtlsConfig(clientCertificateProvider = provider)

    val result = MtlsClientBuilder.build(baseClient, config)
    assertThat(result).isNotSameInstanceAs(baseClient)
    assertThat(result.connectTimeoutMillis).isEqualTo(42_000)
    assertThat(result.readTimeoutMillis).isEqualTo(33_000)
    assertThat(result.writeTimeoutMillis).isEqualTo(21_000)
  }

  // ---------------------------------------------------------------
  // Certificate chain construction (leaf + intermediates)
  // ---------------------------------------------------------------

  @Test
  fun `build handles empty certificate chain in credentials`() {
    val baseClient = createBaseClient()
    val (cert, privateKey) = generateSelfSignedCert()

    val provider = ClientCertificateProvider {
      CertificateCredentials(
        certificate = cert,
        privateKey = privateKey,
        certificateChain = emptyList(),
      )
    }
    val config = MtlsConfig(clientCertificateProvider = provider)

    val result = MtlsClientBuilder.build(baseClient, config)
    assertThat(result).isNotSameInstanceAs(baseClient)
  }

  @Test
  fun `build handles credentials with properly signed intermediate certificates`() {
    val baseClient = createBaseClient()
    val (leafCert, leafKey, caCert) = generateSignedCertChain()

    val provider = ClientCertificateProvider {
      CertificateCredentials(
        certificate = leafCert,
        privateKey = leafKey,
        certificateChain = listOf(caCert),
      )
    }
    val config = MtlsConfig(clientCertificateProvider = provider)

    val result = MtlsClientBuilder.build(baseClient, config)
    assertThat(result).isNotSameInstanceAs(baseClient)
    assertThat(result.sslSocketFactory).isNotNull()
  }

  @Test
  fun `build throws when certificate chain is invalid`() {
    // Independent certs that do not form a valid chain cause
    // PKCS12 KeyStore.setKeyEntry to throw. The SDK must propagate the failure
    // so operators see the misconfiguration instead of running without mTLS.
    val baseClient = createBaseClient()
    val (leafCert, leafKey) = generateSelfSignedCert(cn = "CN=Leaf")
    val (unrelatedCert, _) = generateSelfSignedCert(cn = "CN=Unrelated")

    val provider = ClientCertificateProvider {
      CertificateCredentials(
        certificate = leafCert,
        privateKey = leafKey,
        certificateChain = listOf(unrelatedCert),
      )
    }
    val config = MtlsConfig(clientCertificateProvider = provider)

    var threw = false
    try {
      MtlsClientBuilder.build(baseClient, config)
    } catch (_: MtlsConfigurationException) {
      threw = true
    }
    assertThat(threw).isTrue()
  }

  // ---------------------------------------------------------------
  // getDefaultTrustManager() -- tested via reflection
  // ---------------------------------------------------------------

  @Test
  fun `getDefaultTrustManager returns X509TrustManager`() {
    val method = MtlsClientBuilder::class.java.getDeclaredMethod("getDefaultTrustManager")
    method.isAccessible = true

    val trustManager = method.invoke(MtlsClientBuilder)
    assertThat(trustManager).isInstanceOf(X509TrustManager::class.java)
  }

  @Test
  fun `getDefaultTrustManager accepted issuers is not null`() {
    val method = MtlsClientBuilder::class.java.getDeclaredMethod("getDefaultTrustManager")
    method.isAccessible = true

    val trustManager = method.invoke(MtlsClientBuilder) as X509TrustManager
    assertThat(trustManager.acceptedIssuers).isNotNull()
  }

  // ---------------------------------------------------------------
  // createSslContext() -- tested via reflection
  // ---------------------------------------------------------------

  @Test
  fun `createSslContext produces valid SSLContext with self-signed cert`() {
    val (cert, privateKey) = generateSelfSignedCert()
    val credentials = CertificateCredentials(
      certificate = cert,
      privateKey = privateKey,
      certificateChain = emptyList(),
    )

    val method = MtlsClientBuilder::class.java.getDeclaredMethod(
      "createSslContext",
      CertificateCredentials::class.java,
    )
    method.isAccessible = true

    val sslContext = method.invoke(MtlsClientBuilder, credentials)
        as javax.net.ssl.SSLContext
    assertThat(sslContext).isNotNull()
    assertThat(sslContext.protocol).isEqualTo("TLS")
    assertThat(sslContext.socketFactory).isNotNull()
  }

  @Test
  fun `createSslContext with valid intermediate chain`() {
    val (leafCert, leafKey, caCert) = generateSignedCertChain()
    val credentials = CertificateCredentials(
      certificate = leafCert,
      privateKey = leafKey,
      certificateChain = listOf(caCert),
    )

    val method = MtlsClientBuilder::class.java.getDeclaredMethod(
      "createSslContext",
      CertificateCredentials::class.java,
    )
    method.isAccessible = true

    val sslContext = method.invoke(MtlsClientBuilder, credentials)
        as javax.net.ssl.SSLContext
    assertThat(sslContext).isNotNull()
    assertThat(sslContext.socketFactory).isNotNull()
  }

  // ---------------------------------------------------------------
  // CertificateCredentials data class
  // ---------------------------------------------------------------

  @Test
  fun `CertificateCredentials default chain is empty`() {
    val (cert, privateKey) = generateSelfSignedCert()
    val credentials = CertificateCredentials(
      certificate = cert,
      privateKey = privateKey,
    )
    assertThat(credentials.certificateChain).isEmpty()
  }

  @Test
  fun `CertificateCredentials preserves all properties`() {
    val (cert, privateKey) = generateSelfSignedCert(cn = "CN=Test")
    val (intermediate, _) = generateSelfSignedCert(cn = "CN=Intermediate")

    val credentials = CertificateCredentials(
      certificate = cert,
      privateKey = privateKey,
      certificateChain = listOf(intermediate),
    )
    assertThat(credentials.certificate).isSameInstanceAs(cert)
    assertThat(credentials.privateKey).isSameInstanceAs(privateKey)
    assertThat(credentials.certificateChain).hasSize(1)
    assertThat(credentials.certificateChain[0]).isSameInstanceAs(intermediate)
  }

  @Test
  fun `CertificateCredentials data class equality`() {
    val (cert, privateKey) = generateSelfSignedCert()
    val a = CertificateCredentials(cert, privateKey, emptyList())
    val b = CertificateCredentials(cert, privateKey, emptyList())
    assertThat(a).isEqualTo(b)
    assertThat(a.hashCode()).isEqualTo(b.hashCode())
  }

  // ---------------------------------------------------------------
  // MtlsConfig data class
  // ---------------------------------------------------------------

  @Test
  fun `MtlsConfig holds provider reference`() {
    val provider = ClientCertificateProvider { null }
    val config = MtlsConfig(clientCertificateProvider = provider)
    assertThat(config.clientCertificateProvider).isSameInstanceAs(provider)
  }

  // ---------------------------------------------------------------
  // Edge case: multiple builds from same base client
  // ---------------------------------------------------------------

  @Test
  fun `multiple builds from same base client produce independent clients`() {
    val baseClient = createBaseClient()
    val (certA, keyA) = generateSelfSignedCert(cn = "CN=Client A")
    val (certB, keyB) = generateSelfSignedCert(cn = "CN=Client B")

    val configA = MtlsConfig(
      clientCertificateProvider = ClientCertificateProvider {
        CertificateCredentials(certificate = certA, privateKey = keyA)
      },
    )
    val configB = MtlsConfig(
      clientCertificateProvider = ClientCertificateProvider {
        CertificateCredentials(certificate = certB, privateKey = keyB)
      },
    )

    val clientA = MtlsClientBuilder.build(baseClient, configA)
    val clientB = MtlsClientBuilder.build(baseClient, configB)

    assertThat(clientA).isNotSameInstanceAs(clientB)
    assertThat(clientA).isNotSameInstanceAs(baseClient)
    assertThat(clientB).isNotSameInstanceAs(baseClient)
  }

  // ---------------------------------------------------------------
  // Edge case: base client with interceptors preserved
  // ---------------------------------------------------------------

  @Test
  fun `build preserves base client interceptors`() {
    val interceptor = okhttp3.Interceptor { chain -> chain.proceed(chain.request()) }
    val baseClient = OkHttpClient.Builder()
      .addInterceptor(interceptor)
      .build()

    val (cert, privateKey) = generateSelfSignedCert()
    val provider = ClientCertificateProvider {
      CertificateCredentials(certificate = cert, privateKey = privateKey)
    }
    val config = MtlsConfig(clientCertificateProvider = provider)

    val result = MtlsClientBuilder.build(baseClient, config)
    assertThat(result.interceptors).contains(interceptor)
  }
}
