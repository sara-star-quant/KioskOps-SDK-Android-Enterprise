package com.peterz.kioskops.sdk.audit

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.compliance.RetentionPolicy
import com.peterz.kioskops.sdk.crypto.SoftwareAesGcmCryptoProvider
import com.peterz.kioskops.sdk.util.Clock
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuditTrailRetentionTest {
  @Test
  fun purgeOldFiles_deletesOlderThanRetentionWindow() {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    val retention = RetentionPolicy(
      retainSentEventsDays = 7,
      retainFailedEventsDays = 14,
      retainTelemetryDays = 7,
      retainAuditDays = 1,
      retainLogsDays = 7,
    )

    val clock = object : Clock {
      var now: Long = java.time.Instant.parse("2026-01-03T12:00:00Z").toEpochMilli()
      override fun nowMs(): Long = now
    }

    val audit = AuditTrail(
      context = ctx,
      retentionProvider = { retention },
      clock = clock,
      crypto = SoftwareAesGcmCryptoProvider(),
    )

    audit.record("a")
    assertThat(audit.listFiles()).isNotEmpty()

    clock.now = java.time.Instant.parse("2026-01-05T12:00:00Z").toEpochMilli()
    audit.purgeOldFiles()

    assertThat(audit.listFiles()).isEmpty()
  }
}
