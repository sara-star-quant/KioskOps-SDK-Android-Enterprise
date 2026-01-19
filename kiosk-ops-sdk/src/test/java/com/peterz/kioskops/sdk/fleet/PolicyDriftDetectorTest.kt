package com.peterz.kioskops.sdk.fleet

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.KioskOpsConfig
import org.junit.Test

class PolicyDriftDetectorTest {

  @Test
  fun `first check stores hash and does not report drift`() {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    val detector = PolicyDriftDetector(ctx)

    val cfg = KioskOpsConfig(
      baseUrl = "https://example",
      locationId = "L1",
      kioskEnabled = true,
      syncIntervalMinutes = 15,
      adminExitPin = "1234"
    )

    val r = detector.checkAndStore(cfg, nowMs = 1000L)

    assertThat(r.drifted).isFalse()
    assertThat(r.previousHash).isNull()
    assertThat(r.currentHash).isNotEmpty()
    assertThat(r.firstSeenAtMs).isEqualTo(1000L)
  }

  @Test
  fun `changing a non-secret field reports drift`() {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    val detector = PolicyDriftDetector(ctx)

    val cfg1 = KioskOpsConfig(
      baseUrl = "https://example",
      locationId = "L1",
      kioskEnabled = true,
      syncIntervalMinutes = 15,
      adminExitPin = "1234"
    )
    val cfg2 = cfg1.copy(syncIntervalMinutes = 30)

    val r1 = detector.checkAndStore(cfg1, nowMs = 1000L)
    val r2 = detector.checkAndStore(cfg2, nowMs = 2000L)

    assertThat(r2.drifted).isTrue()
    assertThat(r2.previousHash).isEqualTo(r1.currentHash)
    assertThat(r2.currentHash).isNotEqualTo(r1.currentHash)
  }

  @Test
  fun `changing adminExitPin does not affect drift hash`() {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    val detector = PolicyDriftDetector(ctx)

    val cfg1 = KioskOpsConfig(
      baseUrl = "https://example",
      locationId = "L1",
      kioskEnabled = true,
      syncIntervalMinutes = 15,
      adminExitPin = "1234"
    )
    val cfg2 = cfg1.copy(adminExitPin = "9999")

    val r1 = detector.checkAndStore(cfg1, nowMs = 1000L)
    val r2 = detector.checkAndStore(cfg2, nowMs = 2000L)

    // no drift: sanitized projection must not include secrets
    assertThat(r2.drifted).isFalse()
    assertThat(r2.currentHash).isEqualTo(r1.currentHash)
  }
}
