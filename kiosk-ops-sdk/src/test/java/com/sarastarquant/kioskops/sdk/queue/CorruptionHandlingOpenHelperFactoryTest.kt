/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.queue

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CorruptionHandlingOpenHelperFactoryTest {

  private fun stubDb(): SupportSQLiteDatabase = java.lang.reflect.Proxy.newProxyInstance(
    SupportSQLiteDatabase::class.java.classLoader,
    arrayOf(SupportSQLiteDatabase::class.java),
  ) { _, _, _ -> null } as SupportSQLiteDatabase

  private class FakeOpenHelper : SupportSQLiteOpenHelper {
    override val databaseName: String? = null
    override fun close() = Unit
    override val readableDatabase: SupportSQLiteDatabase
      get() = error("not used")
    override val writableDatabase: SupportSQLiteDatabase
      get() = error("not used")
    override fun setWriteAheadLoggingEnabled(enabled: Boolean) = Unit
  }

  private class CapturingDelegate : SupportSQLiteOpenHelper.Factory {
    var captured: SupportSQLiteOpenHelper.Callback? = null
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
      captured = configuration.callback
      return FakeOpenHelper()
    }
  }

  private class RecordingCallback : SupportSQLiteOpenHelper.Callback(1) {
    var corruptionCalls = 0
    override fun onCreate(db: SupportSQLiteDatabase) = Unit
    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    override fun onCorruption(db: SupportSQLiteDatabase) { corruptionCalls++ }
  }

  @Test
  fun `wrapping callback invokes corruption hook before delegate callback`() {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    val original = RecordingCallback()
    val capture = CapturingDelegate()

    var hookCalled = 0
    val factory = CorruptionHandlingOpenHelperFactory(
      delegate = capture,
      onCorruption = { hookCalled++ },
    )

    factory.create(
      SupportSQLiteOpenHelper.Configuration.builder(ctx)
        .name("x.db")
        .callback(original)
        .build()
    )

    capture.captured!!.onCorruption(stubDb())

    assertThat(hookCalled).isEqualTo(1)
    assertThat(original.corruptionCalls).isEqualTo(1)
  }

  @Test
  fun `hook exceptions do not prevent delegate corruption recovery`() {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    val original = RecordingCallback()
    val capture = CapturingDelegate()

    val factory = CorruptionHandlingOpenHelperFactory(
      delegate = capture,
      onCorruption = { error("host app listener blew up") },
    )

    factory.create(
      SupportSQLiteOpenHelper.Configuration.builder(ctx)
        .name("y.db")
        .callback(original)
        .build()
    )

    capture.captured!!.onCorruption(stubDb())
    assertThat(original.corruptionCalls).isEqualTo(1)
  }
}
