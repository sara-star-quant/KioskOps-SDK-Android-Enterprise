package com.peterz.kioskops.sdk.queue

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.KioskOpsConfig
import com.peterz.kioskops.sdk.compliance.SecurityPolicy
import com.peterz.kioskops.sdk.crypto.NoopCryptoProvider
import com.peterz.kioskops.sdk.logging.RingLog
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EnqueueGuardsTest {
  private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

  @Test fun rejectsOversizePayload() = runBlocking {
    val cfg = KioskOpsConfig(
      baseUrl = "https://example.invalid/",
      locationId = "L",
      kioskEnabled = false,
      securityPolicy = SecurityPolicy.maximalistDefaults().copy(maxEventPayloadBytes = 10)
    )
    val repo = QueueRepository(ctx, RingLog(ctx), NoopCryptoProvider)
    val res = repo.enqueue("T", "{\"long\":\"payload\"}", cfg)
    assertThat(res.isAccepted).isFalse()
    assertThat(res).isInstanceOf(EnqueueResult.Rejected.PayloadTooLarge::class.java)
  }

  @Test fun rejectsDenylistedKeyWhenRawNotAllowed() = runBlocking {
    val cfg = KioskOpsConfig(
      baseUrl = "https://example.invalid/",
      locationId = "L",
      kioskEnabled = false,
      securityPolicy = SecurityPolicy.maximalistDefaults().copy(denylistJsonKeys = setOf("email"), allowRawPayloadStorage = false)
    )
    val repo = QueueRepository(ctx, RingLog(ctx), NoopCryptoProvider)
    val res = repo.enqueue("T", "{\"email\":\"a@b.com\"}", cfg)
    assertThat(res.isAccepted).isFalse()
    assertThat(res).isInstanceOf(EnqueueResult.Rejected.DenylistedKey::class.java)
  }

  @Test fun allowsDenylistedKeyWhenRawAllowed() = runBlocking {
    val cfg = KioskOpsConfig(
      baseUrl = "https://example.invalid/",
      locationId = "L",
      kioskEnabled = false,
      securityPolicy = SecurityPolicy.maximalistDefaults().copy(denylistJsonKeys = setOf("email"), allowRawPayloadStorage = true, encryptQueuePayloads = false)
    )
    val repo = QueueRepository(ctx, RingLog(ctx), NoopCryptoProvider)
    val res = repo.enqueue("T", "{\"email\":\"a@b.com\"}", cfg)
    assertThat(res.isAccepted).isTrue()
  }
}
