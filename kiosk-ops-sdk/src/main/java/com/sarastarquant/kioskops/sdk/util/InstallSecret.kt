/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Per-install secret used for deterministic idempotency and similar local-only features.
 *
 * The secret is 32 random bytes generated once per install. It is wrapped with an Android
 * Keystore AES-GCM key (parity with [com.sarastarquant.kioskops.sdk.crypto.DatabaseEncryptionProvider])
 * so the raw value never sits in SharedPreferences in the clear; the wrapping key never
 * leaves the Keystore / TEE / StrongBox.
 *
 * Installs upgraded from releases prior to 1.2.0 that stored the secret base64-encoded in
 * plaintext are migrated on first access: the legacy value is wrapped in place and the
 * unwrapped entry is removed. Migration runs at most once per install and is
 * concurrent-safe via a monitor.
 *
 * Returned byte arrays are caller-owned. Callers should pass them to the consuming
 * operation and immediately zero the buffer; see [zeroize]. The secret itself never leaves
 * the device unless the host explicitly exports diagnostics (it is not included by
 * default).
 *
 * @since 1.2.0 (wrapping + migration)
 */
object InstallSecret {
  private const val PREFS = "kioskops_install_secret"
  private const val LEGACY_KEY = "secret_b64"
  private const val WRAPPED_KEY = "wrapped_v2"
  private const val WRAP_IV_KEY = "wrap_iv_v2"

  private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
  private const val WRAPPING_ALIAS = "kioskops_install_secret_wrap_v1"
  private const val GCM_TAG_BITS = 128
  private const val DEFAULT_SECRET_BYTES = 32

  private val lock = Any()

  // Test-only injection. When set, getOrCreate returns a copy of this value and does not
  // touch SharedPreferences or the Keystore. Robolectric has no AndroidKeyStore, so tests
  // that exercise deterministic idempotency need a Keystore-free path. Production code
  // never sets this; @VisibleForTesting keeps it out of public tooling.
  @androidx.annotation.VisibleForTesting
  @Volatile
  internal var testSecretOverride: ByteArray? = null

  /**
   * Return the per-install secret, creating it on first access or migrating a legacy
   * unwrapped secret if one is present. Callers should [zeroize] the returned array after
   * feeding it to the consuming operation.
   */
  fun getOrCreate(context: Context, bytes: Int = DEFAULT_SECRET_BYTES): ByteArray {
    require(bytes > 0) { "bytes must be positive" }
    testSecretOverride?.let { return it.copyOf() }
    return synchronized(lock) {
      val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      val wrappingKey = getOrCreateWrappingKey()

      val wrapped = prefs.getString(WRAPPED_KEY, null)
      val iv = prefs.getString(WRAP_IV_KEY, null)

      if (wrapped != null && iv != null) {
        unwrap(
          wrappingKey,
          Base64.getDecoder().decode(wrapped),
          Base64.getDecoder().decode(iv),
        )
      } else {
        createAndWrap(prefs, wrappingKey, bytes)
      }
    }
  }

  private fun createAndWrap(
    prefs: android.content.SharedPreferences,
    wrappingKey: SecretKey,
    bytes: Int,
  ): ByteArray {
    // Migrate legacy plaintext entry if present; otherwise generate fresh random secret.
    val legacy = prefs.getString(LEGACY_KEY, null)
    val plaintext = if (!legacy.isNullOrBlank()) {
      Base64.getDecoder().decode(legacy)
    } else {
      ByteArray(bytes).also { SecureRandom().nextBytes(it) }
    }

    val (newWrapped, newIv) = wrap(wrappingKey, plaintext)
    prefs.edit()
      .putString(WRAPPED_KEY, Base64.getEncoder().encodeToString(newWrapped))
      .putString(WRAP_IV_KEY, Base64.getEncoder().encodeToString(newIv))
      .remove(LEGACY_KEY)
      .apply()

    return plaintext
  }

  /**
   * Overwrite a sensitive byte array with zeros. Java does not guarantee the array isn't
   * swapped to disk or retained by the GC's copying collector, so this is defense-in-depth,
   * not a hard guarantee. Still worth doing for secrets held on the stack briefly.
   */
  fun zeroize(bytes: ByteArray) {
    bytes.fill(0)
  }

  private fun wrap(wrappingKey: SecretKey, plaintext: ByteArray): Pair<ByteArray, ByteArray> {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, wrappingKey)
    val ct = cipher.doFinal(plaintext)
    return ct to cipher.iv
  }

  private fun unwrap(wrappingKey: SecretKey, wrapped: ByteArray, iv: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, iv))
    return cipher.doFinal(wrapped)
  }

  private fun getOrCreateWrappingKey(): SecretKey {
    val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    (ks.getKey(WRAPPING_ALIAS, null) as? SecretKey)?.let { return it }

    val spec = KeyGenParameterSpec.Builder(
      WRAPPING_ALIAS,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
      .setKeySize(256)
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .build()

    val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
    gen.init(spec)
    return gen.generateKey()
  }
}
