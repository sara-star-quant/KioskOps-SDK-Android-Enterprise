package com.sarastarquant.kioskops.sdk.queue

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.compliance.SecurityPolicy
import com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider
import com.sarastarquant.kioskops.sdk.logging.RingLog
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EnqueueGuardsTest {
  private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

  private val cfg = KioskOpsConfig(
    baseUrl = "https://example.invalid/",
    locationId = "L",
    kioskEnabled = false,
    securityPolicy = SecurityPolicy.maximalistDefaults(),
  )

  @Test fun rejectsOversizePayload() = runBlocking {
    val smallCfg = cfg.copy(
      securityPolicy = SecurityPolicy.maximalistDefaults().copy(maxEventPayloadBytes = 10)
    )
    val repo = QueueRepository(ctx, RingLog(ctx), NoopCryptoProvider)
    val res = repo.enqueue("T", "{\"long\":\"payload\"}", smallCfg)
    assertThat(res.isAccepted).isFalse()
    assertThat(res).isInstanceOf(EnqueueResult.Rejected.PayloadTooLarge::class.java)
  }

  @Test fun duplicateIdempotencyKeyReturnsDuplicateIdempotency() = runBlocking {
    val repo = QueueRepository(ctx, RingLog(ctx), NoopCryptoProvider)
    val first = repo.enqueue("T", "{\"x\":1}", cfg, idempotencyKeyOverride = "same-key")
    assertThat(first.isAccepted).isTrue()

    val second = repo.enqueue("T", "{\"x\":2}", cfg, idempotencyKeyOverride = "same-key")
    assertThat(second.isAccepted).isFalse()
    assertThat(second).isInstanceOf(EnqueueResult.Rejected.DuplicateIdempotency::class.java)
  }

  @Test fun unknownErrorReturnsUnknown() {
    // Verify the Unknown type exists and is a Rejected subtype
    val unknown = EnqueueResult.Rejected.Unknown("test error")
    assertThat(unknown).isInstanceOf(EnqueueResult.Rejected::class.java)
    assertThat(unknown.reason).isEqualTo("test error")
    assertThat(unknown.isAccepted).isFalse()
  }
}
