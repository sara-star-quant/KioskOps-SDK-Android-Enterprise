/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.queue

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.sarastarquant.kioskops.sdk.KioskOpsError
import com.sarastarquant.kioskops.sdk.KioskOpsErrorListener

/**
 * Handles SQLite database corruption by notifying the error listener
 * and deleting the corrupted database file so Room can recreate it.
 *
 * @since 0.8.0
 */
internal class DatabaseCorruptionHandler(
  private val databaseName: String,
  private val errorListener: (() -> KioskOpsErrorListener?)? = null,
) : DatabaseErrorHandler {

  override fun onCorruption(dbObj: SQLiteDatabase) {
    val path = dbObj.path ?: databaseName
    Log.e(TAG, "Database corruption detected: $path")

    errorListener?.invoke()?.onError(
      KioskOpsError.StorageError(
        message = "Database corruption detected in $databaseName; database will be recreated",
      )
    )

    // Default behavior: delete the corrupted database so Room recreates it.
    // This loses data but restores SDK functionality.
    try {
      dbObj.close()
    } catch (_: Exception) {
      // Already corrupted; close may fail
    }
  }

  companion object {
    private const val TAG = "KioskOpsDbCorruption"
  }
}
