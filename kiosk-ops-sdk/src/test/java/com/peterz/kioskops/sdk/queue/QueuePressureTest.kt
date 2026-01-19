package com.peterz.kioskops.sdk.queue

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.KioskOpsConfig
import com.peterz.kioskops.sdk.compliance.QueueLimits
import com.peterz.kioskops.sdk.compliance.OverflowStrategy
import com.peterz.kioskops.sdk.compliance.SecurityPolicy
import com.peterz.kioskops.sdk.crypto.NoopCryptoProvider
import com.peterz.kioskops.sdk.logging.RingLog
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueuePressureTest {
  private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

  @Test
  fun `DROP_OLDEST enforces maxActiveEvents and reports droppedOldest`() = runBlocking {
    ctx.deleteDatabase("kiosk_ops_queue.db")

    val cfg = KioskOpsConfig(
      baseUrl = "https://example.invalid/",
      locationId = "L",
      kioskEnabled = false,
      securityPolicy = SecurityPolicy.maximalistDefaults().copy(encryptQueuePayloads = false, maxEventPayloadBytes = 10_000),
      queueLimits = QueueLimits(maxActiveEvents = 3, maxActiveBytes = 1024 * 1024, overflowStrategy = OverflowStrategy.DROP_OLDEST)
    )

    val repo = QueueRepository(ctx, RingLog(ctx), NoopCryptoProvider)

    assertThat(repo.enqueue("T", "{\"i\":1}", cfg).isAccepted).isTrue()
    assertThat(repo.enqueue("T", "{\"i\":2}", cfg).isAccepted).isTrue()
    assertThat(repo.enqueue("T", "{\"i\":3}", cfg).isAccepted).isTrue()

    val fourth = repo.enqueue("T", "{\"i\":4}", cfg)
    assertThat(fourth).isInstanceOf(EnqueueResult.Accepted::class.java)
    val accepted = fourth as EnqueueResult.Accepted
    assertThat(accepted.droppedOldest).isAtLeast(1)

    assertThat(repo.countActive()).isEqualTo(3)
  }
}
