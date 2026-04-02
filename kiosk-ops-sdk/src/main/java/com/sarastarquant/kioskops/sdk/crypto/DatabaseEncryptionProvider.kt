/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Provides SQLCipher [SupportSQLiteOpenHelper.Factory] for Room database encryption.
 *
 * Key is stored in Android Keystore and derived into a passphrase for SQLCipher.
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

  /**
   * Create a SQLCipher [SupportSQLiteOpenHelper.Factory] using a Keystore-backed key.
   *
   * @throws IllegalStateException if SQLCipher is not on the classpath.
   */
  fun createFactory(): SupportSQLiteOpenHelper.Factory {
    val passphrase = getOrCreatePassphrase()
    return createSqlCipherFactory(passphrase)
  }

  internal fun getOrCreatePassphrase(): ByteArray {
    val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
    keyStore.load(null)

    val key = if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
      keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
    } else {
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
      generator.generateKey()
    }

    return key.encoded ?: key.toString().toByteArray(Charsets.UTF_8)
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
