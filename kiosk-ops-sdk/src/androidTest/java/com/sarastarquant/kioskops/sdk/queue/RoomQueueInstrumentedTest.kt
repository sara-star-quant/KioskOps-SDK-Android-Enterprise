/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.queue

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.compliance.SecurityPolicy
import com.sarastarquant.kioskops.sdk.crypto.AesGcmKeystoreCryptoProvider
import com.sarastarquant.kioskops.sdk.logging.RingLog
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/**
 * Instrumented tests for [QueueRepository] backed by real SQLite and real AndroidKeyStore
 * encryption on a device/emulator.
 *
 * Uses the production database name ("kiosk_ops_queue.db") because [QueueRepository]
 * hardcodes it internally. The database is deleted before and after each test.
 */
@RunWith(AndroidJUnit4::class)
class RoomQueueInstrumentedTest {

  private lateinit var ctx: Context
  private lateinit var crypto: AesGcmKeystoreCryptoProvider
  private lateinit var logs: RingLog
  private lateinit var repo: QueueRepository
  private lateinit var cfg: KioskOpsConfig

  private val prodDbName = "kiosk_ops_queue.db"
  private val cryptoAlias = "test_queue_instrumented_key"

  @Before
  fun setUp() {
    ctx = InstrumentationRegistry.getInstrumentation().targetContext
    // Clean up from any previous run.
    ctx.deleteDatabase(prodDbName)
    deleteKeystoreEntry(cryptoAlias)

    crypto = AesGcmKeystoreCryptoProvider(alias = cryptoAlias)
    logs = RingLog(ctx)
    cfg = KioskOpsConfig(
      baseUrl = "https://example.invalid",
      locationId = "test-loc",
      kioskEnabled = true,
      securityPolicy = SecurityPolicy.maximalistDefaults(),
    )

    repo = createRepo()
  }

  @After
  fun tearDown() {
    ctx.deleteDatabase(prodDbName)
    deleteKeystoreEntry(cryptoAlias)
  }

  // ---------------------------------------------------------------------------
  // Enqueue and retrieve
  // ---------------------------------------------------------------------------

  @Test
  fun enqueueAndRetrieveEventFromRealDatabase() = runTest {
    val result = enqueueSimple("kiosk.opened", """{"screen":"home"}""")
    assertThat(result).isInstanceOf(EnqueueResult.Accepted::class.java)

    val batch = repo.nextBatch(nowMs = Long.MAX_VALUE, limit = 10)
    assertThat(batch).hasSize(1)
    assertThat(batch[0].type).isEqualTo("kiosk.opened")

    // The payload was encrypted at rest; decoding must yield the original JSON.
    val decoded = repo.decodePayloadJson(batch[0])
    assertThat(decoded).isEqualTo("""{"screen":"home"}""")
  }

  // ---------------------------------------------------------------------------
  // Queue depth
  // ---------------------------------------------------------------------------

  @Test
  fun queueDepthReflectsRealDatabaseState() = runTest {
    assertThat(repo.countActive()).isEqualTo(0)

    enqueueSimple("a", """{"k":"1"}""")
    assertThat(repo.countActive()).isEqualTo(1)

    enqueueSimple("b", """{"k":"2"}""")
    assertThat(repo.countActive()).isEqualTo(2)
  }

  // ---------------------------------------------------------------------------
  // Persistence across close/reopen
  // ---------------------------------------------------------------------------

  @Test
  fun eventSurvivesDatabaseCloseAndReopen() = runTest {
    enqueueSimple("survive.test", """{"v":42}""")
    assertThat(repo.countActive()).isEqualTo(1)

    // Create a second QueueRepository instance that opens its own database connection
    // to the same on-disk file, simulating a process restart.
    val repo2 = createRepo()
    val batch = repo2.nextBatch(nowMs = Long.MAX_VALUE, limit = 10)
    assertThat(batch).hasSize(1)
    assertThat(batch[0].type).isEqualTo("survive.test")

    val decoded = repo2.decodePayloadJson(batch[0])
    assertThat(decoded).isEqualTo("""{"v":42}""")
  }

  // ---------------------------------------------------------------------------
  // Concurrent enqueue
  // ---------------------------------------------------------------------------

  @Test
  fun concurrentEnqueueFromMultipleCoroutines() = runTest {
    val count = 10
    val jobs = (0 until count).map { i ->
      async {
        enqueueSimple("concurrent.$i", """{"i":$i}""")
      }
    }
    val results = jobs.awaitAll()
    assertThat(results).hasSize(count)
    results.forEach { r ->
      assertThat(r).isInstanceOf(EnqueueResult.Accepted::class.java)
    }

    val active = repo.countActive()
    assertThat(active).isEqualTo(count.toLong())
  }

  // ---------------------------------------------------------------------------
  // Database file on disk
  // ---------------------------------------------------------------------------

  @Test
  fun databaseFileExistsOnDiskAfterEnqueue() = runTest {
    enqueueSimple("disk.check", """{"ok":true}""")

    val dbFile = ctx.getDatabasePath(prodDbName)
    assertThat(dbFile.exists()).isTrue()
    assertThat(dbFile.length()).isGreaterThan(0L)
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private fun createRepo(): QueueRepository {
    return QueueRepository(
      context = ctx,
      logs = logs,
      crypto = crypto,
    )
  }

  private suspend fun enqueueSimple(type: String, payloadJson: String): EnqueueResult {
    return repo.enqueue(
      type = type,
      payloadJson = payloadJson,
      cfg = cfg,
    )
  }

  private fun deleteKeystoreEntry(keyAlias: String) {
    try {
      val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
      if (ks.containsAlias(keyAlias)) {
        ks.deleteEntry(keyAlias)
      }
    } catch (_: Exception) {
      // Best-effort cleanup.
    }
  }
}
