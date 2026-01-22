package com.peterz.kioskops.sdk.telemetry

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.compliance.RetentionPolicy
import com.peterz.kioskops.sdk.compliance.TelemetryPolicy
import com.peterz.kioskops.sdk.crypto.SoftwareAesGcmCryptoProvider
import com.peterz.kioskops.sdk.util.Clock
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TelemetryRetentionTest {
  @Test
  fun purgeOldFiles_deletesOlderThanRetentionWindow() {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    val policy = TelemetryPolicy(
      enabled = true,
      includeDeviceId = false,
      regionTag = null,
      allowedKeys = setOf("k")
    )

    val retention = RetentionPolicy(
      retainSentEventsDays = 7,
      retainFailedEventsDays = 14,
      retainTelemetryDays = 1,
      retainAuditDays = 30,
      retainLogsDays = 7,
    )

    val clock = object : Clock {
      var now: Long = java.time.Instant.parse("2026-01-03T12:00:00Z").toEpochMilli()
      override fun nowMs(): Long = now
    }

    val store = EncryptedTelemetryStore(
      context = ctx,
      policyProvider = { policy },
      retentionProvider = { retention },
      clock = clock,
      crypto = SoftwareAesGcmCryptoProvider(),
    )

    store.emit("evt", mapOf("k" to "v"))
    val filesAfterWrite = store.listFiles()
    assertThat(filesAfterWrite).isNotEmpty()

    // Advance 2 days => previous day-start is older than 1-day window
    clock.now = java.time.Instant.parse("2026-01-05T12:00:00Z").toEpochMilli()
    store.purgeOldFiles()

    val filesAfterPurge = store.listFiles()
    assertThat(filesAfterPurge).isEmpty()
  }
}
