/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.queue

import androidx.annotation.RestrictTo
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper

/**
 * Wraps a [SupportSQLiteOpenHelper.Factory] so the Room/SQLCipher-managed callback's
 * `onCorruption` hook fires an application-level callback before Room's default
 * recreate-the-database behavior runs. Used to surface
 * [com.sarastarquant.kioskops.sdk.KioskOpsError.StorageError] to the host app
 * and append an audit entry when a Room-managed database file is corrupt.
 *
 * The delegate factory's own corruption handling (delete + recreate) still runs
 * after the callback, so the SDK recovers instead of repeatedly crashing.
 *
 * @since 1.2.0
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
internal class CorruptionHandlingOpenHelperFactory(
  private val delegate: SupportSQLiteOpenHelper.Factory,
  private val onCorruption: (SupportSQLiteDatabase) -> Unit,
) : SupportSQLiteOpenHelper.Factory {

  override fun create(
    configuration: SupportSQLiteOpenHelper.Configuration,
  ): SupportSQLiteOpenHelper {
    val originalCallback = configuration.callback
    val wrapped = object : SupportSQLiteOpenHelper.Callback(originalCallback.version) {
      override fun onConfigure(db: SupportSQLiteDatabase) = originalCallback.onConfigure(db)
      override fun onCreate(db: SupportSQLiteDatabase) = originalCallback.onCreate(db)
      override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
        originalCallback.onUpgrade(db, oldVersion, newVersion)
      override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
        originalCallback.onDowngrade(db, oldVersion, newVersion)
      override fun onOpen(db: SupportSQLiteDatabase) = originalCallback.onOpen(db)

      override fun onCorruption(db: SupportSQLiteDatabase) {
        try {
          onCorruption.invoke(db)
        } catch (_: Throwable) {
          // Callback must not block recovery.
        }
        originalCallback.onCorruption(db)
      }
    }

    val newConfig = SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
      .name(configuration.name)
      .callback(wrapped)
      .build()
    return delegate.create(newConfig)
  }
}
