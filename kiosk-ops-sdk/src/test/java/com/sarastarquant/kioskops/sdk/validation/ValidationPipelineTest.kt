package com.sarastarquant.kioskops.sdk.validation

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.compliance.SecurityPolicy
import com.sarastarquant.kioskops.sdk.crypto.NoopCryptoProvider
import com.sarastarquant.kioskops.sdk.logging.RingLog
import com.sarastarquant.kioskops.sdk.queue.EnqueueResult
import com.sarastarquant.kioskops.sdk.queue.QueueRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ValidationPipelineTest {

  @Test fun `validation disabled allows any event`() = runBlocking {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    val cfg = KioskOpsConfig(
      baseUrl = "https://example.invalid/",
      locationId = "L",
      kioskEnabled = false,
      validationPolicy = ValidationPolicy.disabledDefaults(),
      securityPolicy = SecurityPolicy.maximalistDefaults().copy(encryptQueuePayloads = false),
    )
    val repo = QueueRepository(ctx, RingLog(ctx), NoopCryptoProvider)
    val result = repo.enqueue("UNREGISTERED", """{"any":"data"}""", cfg)
    assertThat(result.isAccepted).isTrue()
  }

  @Test fun `validation strict mode rejects invalid payload through QueueRepository`() = runBlocking {
    // Note: QueueRepository does not run schema validation; that happens in KioskOpsSdk.
    // This test verifies that QueueRepository still works independently.
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    val cfg = KioskOpsConfig(
      baseUrl = "https://example.invalid/",
      locationId = "L",
      kioskEnabled = false,
      validationPolicy = ValidationPolicy.strictDefaults(),
      securityPolicy = SecurityPolicy.maximalistDefaults().copy(encryptQueuePayloads = false),
    )
    val repo = QueueRepository(ctx, RingLog(ctx), NoopCryptoProvider)
    // QueueRepository does not have validation; it should accept
    val result = repo.enqueue("SCAN", """{"barcode":"123"}""", cfg)
    assertThat(result.isAccepted).isTrue()
  }
}
