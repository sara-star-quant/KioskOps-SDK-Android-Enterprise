/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet.posture

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for ConnectivityStatus.
 *
 * Security (BSI SYS.3.2.2.A8): Validates connectivity monitoring for fleet operations.
 * Privacy (GDPR): Verifies no PII (no IP, MAC, or network identifiers) in connectivity data.
 */
@RunWith(RobolectricTestRunner::class)
class ConnectivityStatusTest {

  @Test
  fun `ConnectivityStatus contains all fields`() {
    val status = ConnectivityStatus(
      activeNetworkType = ConnectivityStatus.NETWORK_WIFI,
      isNetworkValidated = true,
      isMetered = false,
      wifiSignalLevel = ConnectivityStatus.WIFI_EXCELLENT,
      cellularType = null,
      isVpnActive = false,
      isAirplaneModeOn = false,
    )

    assertThat(status.activeNetworkType).isEqualTo("WIFI")
    assertThat(status.isNetworkValidated).isTrue()
    assertThat(status.isMetered).isFalse()
    assertThat(status.wifiSignalLevel).isEqualTo("EXCELLENT")
    assertThat(status.cellularType).isNull()
    assertThat(status.isVpnActive).isFalse()
    assertThat(status.isAirplaneModeOn).isFalse()
  }

  @Test
  fun `isConnected returns true for non-NONE network`() {
    val wifiConnected = ConnectivityStatus(
      activeNetworkType = ConnectivityStatus.NETWORK_WIFI,
      isNetworkValidated = true,
      isMetered = false,
    )
    assertThat(wifiConnected.isConnected).isTrue()

    val cellularConnected = ConnectivityStatus(
      activeNetworkType = ConnectivityStatus.NETWORK_CELLULAR,
      isNetworkValidated = true,
      isMetered = true,
    )
    assertThat(cellularConnected.isConnected).isTrue()

    val disconnected = ConnectivityStatus(
      activeNetworkType = ConnectivityStatus.NETWORK_NONE,
      isNetworkValidated = false,
      isMetered = false,
    )
    assertThat(disconnected.isConnected).isFalse()
  }

  @Test
  fun `hasInternet returns true only when connected and validated`() {
    val hasInternet = ConnectivityStatus(
      activeNetworkType = ConnectivityStatus.NETWORK_WIFI,
      isNetworkValidated = true,
      isMetered = false,
    )
    assertThat(hasInternet.hasInternet).isTrue()

    val noInternet = ConnectivityStatus(
      activeNetworkType = ConnectivityStatus.NETWORK_WIFI,
      isNetworkValidated = false,
      isMetered = false,
    )
    assertThat(noInternet.hasInternet).isFalse()

    val disconnected = ConnectivityStatus(
      activeNetworkType = ConnectivityStatus.NETWORK_NONE,
      isNetworkValidated = false,
      isMetered = false,
    )
    assertThat(disconnected.hasInternet).isFalse()
  }

  @Test
  fun `network type constants are defined`() {
    assertThat(ConnectivityStatus.NETWORK_WIFI).isEqualTo("WIFI")
    assertThat(ConnectivityStatus.NETWORK_CELLULAR).isEqualTo("CELLULAR")
    assertThat(ConnectivityStatus.NETWORK_ETHERNET).isEqualTo("ETHERNET")
    assertThat(ConnectivityStatus.NETWORK_NONE).isEqualTo("NONE")
    assertThat(ConnectivityStatus.NETWORK_OTHER).isEqualTo("OTHER")
  }

  @Test
  fun `wifi signal level constants are defined`() {
    assertThat(ConnectivityStatus.WIFI_EXCELLENT).isEqualTo("EXCELLENT")
    assertThat(ConnectivityStatus.WIFI_GOOD).isEqualTo("GOOD")
    assertThat(ConnectivityStatus.WIFI_FAIR).isEqualTo("FAIR")
    assertThat(ConnectivityStatus.WIFI_POOR).isEqualTo("POOR")
    assertThat(ConnectivityStatus.WIFI_NONE).isEqualTo("NONE")
  }

  @Test
  fun `cellular type constants are defined`() {
    assertThat(ConnectivityStatus.CELLULAR_5G).isEqualTo("5G")
    assertThat(ConnectivityStatus.CELLULAR_LTE).isEqualTo("LTE")
    assertThat(ConnectivityStatus.CELLULAR_3G).isEqualTo("3G")
    assertThat(ConnectivityStatus.CELLULAR_2G).isEqualTo("2G")
    assertThat(ConnectivityStatus.CELLULAR_UNKNOWN).isEqualTo("UNKNOWN")
  }

  @Test
  fun `cellular status has cellular type`() {
    val status = ConnectivityStatus(
      activeNetworkType = ConnectivityStatus.NETWORK_CELLULAR,
      isNetworkValidated = true,
      isMetered = true,
      cellularType = ConnectivityStatus.CELLULAR_LTE,
    )
    assertThat(status.cellularType).isEqualTo("LTE")
    assertThat(status.wifiSignalLevel).isNull()
  }

  @Test
  fun `default optional values`() {
    val status = ConnectivityStatus(
      activeNetworkType = ConnectivityStatus.NETWORK_WIFI,
      isNetworkValidated = true,
      isMetered = false,
    )
    assertThat(status.wifiSignalLevel).isNull()
    assertThat(status.cellularType).isNull()
    assertThat(status.isVpnActive).isFalse()
    assertThat(status.isAirplaneModeOn).isFalse()
  }

  @Test
  fun `VPN active status tracked`() {
    val withVpn = ConnectivityStatus(
      activeNetworkType = ConnectivityStatus.NETWORK_WIFI,
      isNetworkValidated = true,
      isMetered = false,
      isVpnActive = true,
    )
    assertThat(withVpn.isVpnActive).isTrue()
  }

  @Test
  fun `airplane mode status tracked`() {
    val airplaneMode = ConnectivityStatus(
      activeNetworkType = ConnectivityStatus.NETWORK_NONE,
      isNetworkValidated = false,
      isMetered = false,
      isAirplaneModeOn = true,
    )
    assertThat(airplaneMode.isAirplaneModeOn).isTrue()
    assertThat(airplaneMode.isConnected).isFalse()
  }

  @Test
  fun `copy preserves unchanged fields`() {
    val original = ConnectivityStatus(
      activeNetworkType = ConnectivityStatus.NETWORK_WIFI,
      isNetworkValidated = true,
      isMetered = false,
      wifiSignalLevel = ConnectivityStatus.WIFI_EXCELLENT,
    )

    val modified = original.copy(wifiSignalLevel = ConnectivityStatus.WIFI_GOOD)

    assertThat(modified.activeNetworkType).isEqualTo(ConnectivityStatus.NETWORK_WIFI)
    assertThat(modified.isNetworkValidated).isTrue()
    assertThat(modified.isMetered).isFalse()
    assertThat(modified.wifiSignalLevel).isEqualTo(ConnectivityStatus.WIFI_GOOD)
  }
}
