/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Provides SQLCipher [SupportSQLiteOpenHelper.Factory] for Room database encryption.
 *
 * The SQLCipher passphrase is a 256-bit random value generated once and encrypted
 * with an Android Keystore AES-256-GCM key. The wrapped passphrase is persisted in
 * SharedPreferences; the wrapping key never leaves the Keystore / TEE / StrongBox.
 *
 * For federal/DoD deployments where Android full-disk encryption alone is insufficient.
 *
 * Requires `net.zetetic:sqlcipher-android` on the classpath. If the dependency is
 * missing and encryption is enabled, [createFactory] throws [IllegalStateException].
 *
 * @since 0.8.0
 */
object DatabaseEncryptionProvider {

  private const val KEYSTORE_ALIAS = "kioskops_db_encryption_v1"
  private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
  internal const val PREFS_NAME = "kioskops_db_encryption"
  private const val KEY_WRAPPED_PASSPHRASE = "wrapped_passphrase_v2"
  private const val KEY_WRAP_IV = "wrap_iv_v2"
  private const val PASSPHRASE_BYTES = 32
  private const val GCM_TAG_BITS = 128

  /**
   * Create a SQLCipher [SupportSQLiteOpenHelper.Factory] using a Keystore-wrapped passphrase.
   *
   * @throws IllegalStateException if SQLCipher is not on the classpath.
   */
  fun createFactory(context: Context): SupportSQLiteOpenHelper.Factory {
    val passphrase = getOrCreatePassphrase(context)
    return createSqlCipherFactory(passphrase)
  }

  internal fun getOrCreatePassphrase(context: Context): ByteArray {
    val wrappingKey = getOrCreateWrappingKey()
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val wrappedB64 = prefs.getString(KEY_WRAPPED_PASSPHRASE, null)
    val ivB64 = prefs.getString(KEY_WRAP_IV, null)

    if (wrappedB64 != null && ivB64 != null) {
      return unwrapPassphrase(wrappingKey, Base64.getDecoder().decode(wrappedB64), Base64.getDecoder().decode(ivB64))
    }

    val passphrase = ByteArray(PASSPHRASE_BYTES)
    SecureRandom().nextBytes(passphrase)
    val (wrapped, iv) = wrapPassphrase(wrappingKey, passphrase)

    prefs.edit()
      .putString(KEY_WRAPPED_PASSPHRASE, Base64.getEncoder().encodeToString(wrapped))
      .putString(KEY_WRAP_IV, Base64.getEncoder().encodeToString(iv))
      .apply()

    return passphrase
  }

  private fun wrapPassphrase(wrappingKey: SecretKey, passphrase: ByteArray): Pair<ByteArray, ByteArray> {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, wrappingKey)
    val ciphertext = cipher.doFinal(passphrase)
    return ciphertext to cipher.iv
  }

  private fun unwrapPassphrase(wrappingKey: SecretKey, wrapped: ByteArray, iv: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, iv))
    return cipher.doFinal(wrapped)
  }

  private fun getOrCreateWrappingKey(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

    val spec = KeyGenParameterSpec.Builder(
      KEYSTORE_ALIAS,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
      .setKeySize(256)
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .build()

    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
    generator.init(spec)
    return generator.generateKey()
  }

  private fun createSqlCipherFactory(passphrase: ByteArray): SupportSQLiteOpenHelper.Factory {
    return try {
      val clazz = Class.forName("net.zetetic.database.sqlcipher.SupportOpenHelperFactory")
      val constructor = clazz.getConstructor(ByteArray::class.java)
      constructor.newInstance(passphrase) as SupportSQLiteOpenHelper.Factory
    } catch (e: ClassNotFoundException) {
      throw IllegalStateException(
        "Database encryption is enabled but SQLCipher is not on the classpath. " +
          "Add dependency: implementation(\"net.zetetic:sqlcipher-android:4.6.1\")",
        e,
      )
    }
  }
}
