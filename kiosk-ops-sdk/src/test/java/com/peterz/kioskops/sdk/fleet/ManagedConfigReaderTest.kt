package com.peterz.kioskops.sdk.fleet

import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.peterz.kioskops.sdk.KioskOpsConfig
import com.peterz.kioskops.sdk.compliance.TelemetryPolicy
import org.junit.Test

class ManagedConfigReaderTest {

  @Test
  fun `applyBundle overrides known keys and leaves others intact`() {
    val defaults = KioskOpsConfig(
      baseUrl = "https://default",
      locationId = "L0",
      kioskEnabled = false,
      syncIntervalMinutes = 15,
      telemetryPolicy = TelemetryPolicy.maximalistDefaults().copy(regionTag = "EU")
    )

    val b = Bundle().apply {
      putString(ManagedConfigReader.Keys.BASE_URL, "https://mdm")
      putString(ManagedConfigReader.Keys.LOCATION_ID, "STORE-7")
      putBoolean(ManagedConfigReader.Keys.KIOSK_ENABLED, true)
      putInt(ManagedConfigReader.Keys.SYNC_INTERVAL_MINUTES, 10)
      putString(ManagedConfigReader.Keys.TELEMETRY_REGION_TAG, "APAC")
      putBoolean(ManagedConfigReader.Keys.TELEMETRY_INCLUDE_DEVICE_ID, true)
    }

    val cfg = ManagedConfigReader.applyBundle(b, defaults)

    assertThat(cfg.baseUrl).isEqualTo("https://mdm")
    assertThat(cfg.locationId).isEqualTo("STORE-7")
    assertThat(cfg.kioskEnabled).isTrue()
    assertThat(cfg.syncIntervalMinutes).isEqualTo(10)
    assertThat(cfg.telemetryPolicy.regionTag).isEqualTo("APAC")
    assertThat(cfg.telemetryPolicy.includeDeviceId).isTrue()
  }

  @Test
  fun `sync interval is clamped to minimum`() {
    val defaults = KioskOpsConfig(
      baseUrl = "https://default",
      locationId = "L0",
      kioskEnabled = false,
      syncIntervalMinutes = 15
    )

    val b = Bundle().apply {
      putInt(ManagedConfigReader.Keys.SYNC_INTERVAL_MINUTES, 1)
    }

    val cfg = ManagedConfigReader.applyBundle(b, defaults)
    assertThat(cfg.syncIntervalMinutes).isEqualTo(5)
  }
}
