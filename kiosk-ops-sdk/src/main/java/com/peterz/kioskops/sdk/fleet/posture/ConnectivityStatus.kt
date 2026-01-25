/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet.posture

import kotlinx.serialization.Serializable

/**
 * Connectivity status snapshot for fleet operations.
 *
 * Privacy (GDPR): Contains no PII. No IP addresses, MAC addresses,
 * or network identifiers are collected.
 *
 * @property activeNetworkType WIFI, CELLULAR, ETHERNET, NONE
 * @property isNetworkValidated True if network has validated internet access
 * @property isMetered True if network is metered (data charges apply)
 * @property wifiSignalLevel WiFi signal: EXCELLENT, GOOD, FAIR, POOR, NONE
 * @property cellularType Cellular type: LTE, 5G, 3G, 2G, UNKNOWN
 * @property isVpnActive True if VPN is active
 * @property isAirplaneModeOn True if airplane mode is enabled
 */
@Serializable
data class ConnectivityStatus(
  val activeNetworkType: String,
  val isNetworkValidated: Boolean,
  val isMetered: Boolean,
  val wifiSignalLevel: String? = null,
  val cellularType: String? = null,
  val isVpnActive: Boolean = false,
  val isAirplaneModeOn: Boolean = false,
) {
  /**
   * Check if device has any network connectivity.
   */
  val isConnected: Boolean
    get() = activeNetworkType != NETWORK_NONE

  /**
   * Check if device has internet access.
   */
  val hasInternet: Boolean
    get() = isConnected && isNetworkValidated

  companion object {
    const val NETWORK_WIFI = "WIFI"
    const val NETWORK_CELLULAR = "CELLULAR"
    const val NETWORK_ETHERNET = "ETHERNET"
    const val NETWORK_NONE = "NONE"
    const val NETWORK_OTHER = "OTHER"

    const val WIFI_EXCELLENT = "EXCELLENT"
    const val WIFI_GOOD = "GOOD"
    const val WIFI_FAIR = "FAIR"
    const val WIFI_POOR = "POOR"
    const val WIFI_NONE = "NONE"

    const val CELLULAR_5G = "5G"
    const val CELLULAR_LTE = "LTE"
    const val CELLULAR_3G = "3G"
    const val CELLULAR_2G = "2G"
    const val CELLULAR_UNKNOWN = "UNKNOWN"
  }
}
