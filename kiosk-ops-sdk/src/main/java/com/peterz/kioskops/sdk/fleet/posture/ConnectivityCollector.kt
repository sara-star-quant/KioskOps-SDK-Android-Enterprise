/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.fleet.posture

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService

/**
 * Collects connectivity status without accessing sensitive network identifiers.
 *
 * Privacy (GDPR): No IP addresses, MAC addresses, SSIDs, or cell tower IDs
 * are collected. Only connection type and signal quality.
 *
 * Permissions: Requires ACCESS_NETWORK_STATE (normal permission).
 */
internal class ConnectivityCollector(private val context: Context) {

  /**
   * Collect current connectivity status.
   *
   * @return ConnectivityStatus or null if collection fails
   */
  fun collect(): ConnectivityStatus? = runCatching {
    val connectivityManager = context.getSystemService<ConnectivityManager>()
    val network = connectivityManager?.activeNetwork
    val caps = connectivityManager?.getNetworkCapabilities(network)

    val networkType = determineNetworkType(caps)

    ConnectivityStatus(
      activeNetworkType = networkType,
      isNetworkValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
      isMetered = connectivityManager?.isActiveNetworkMetered == true,
      wifiSignalLevel = if (networkType == ConnectivityStatus.NETWORK_WIFI) getWifiSignalLevel() else null,
      cellularType = if (networkType == ConnectivityStatus.NETWORK_CELLULAR) getCellularType() else null,
      isVpnActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
      isAirplaneModeOn = isAirplaneModeOn(),
    )
  }.getOrNull()

  private fun determineNetworkType(caps: NetworkCapabilities?): String {
    if (caps == null) return ConnectivityStatus.NETWORK_NONE

    return when {
      caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectivityStatus.NETWORK_WIFI
      caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectivityStatus.NETWORK_CELLULAR
      caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectivityStatus.NETWORK_ETHERNET
      else -> ConnectivityStatus.NETWORK_OTHER
    }
  }

  private fun getWifiSignalLevel(): String? = runCatching {
    val wifiManager = context.applicationContext.getSystemService<WifiManager>()
    val rssi = wifiManager?.connectionInfo?.rssi ?: return@runCatching null

    // Calculate signal level (0-4)
    @Suppress("DEPRECATION")
    val level = WifiManager.calculateSignalLevel(rssi, 5)

    when (level) {
      4 -> ConnectivityStatus.WIFI_EXCELLENT
      3 -> ConnectivityStatus.WIFI_GOOD
      2 -> ConnectivityStatus.WIFI_FAIR
      1 -> ConnectivityStatus.WIFI_POOR
      else -> ConnectivityStatus.WIFI_NONE
    }
  }.getOrNull()

  @SuppressLint("MissingPermission") // Gracefully returns null via runCatching if permission not granted
  private fun getCellularType(): String? = runCatching {
    val telephonyManager = context.getSystemService<TelephonyManager>()

    @Suppress("DEPRECATION")
    val networkType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      telephonyManager?.dataNetworkType
    } else {
      telephonyManager?.networkType
    } ?: return@runCatching null

    when (networkType) {
      TelephonyManager.NETWORK_TYPE_NR -> ConnectivityStatus.CELLULAR_5G
      TelephonyManager.NETWORK_TYPE_LTE -> ConnectivityStatus.CELLULAR_LTE
      TelephonyManager.NETWORK_TYPE_HSDPA,
      TelephonyManager.NETWORK_TYPE_HSUPA,
      TelephonyManager.NETWORK_TYPE_HSPA,
      TelephonyManager.NETWORK_TYPE_HSPAP,
      TelephonyManager.NETWORK_TYPE_UMTS -> ConnectivityStatus.CELLULAR_3G
      TelephonyManager.NETWORK_TYPE_EDGE,
      TelephonyManager.NETWORK_TYPE_GPRS,
      TelephonyManager.NETWORK_TYPE_CDMA -> ConnectivityStatus.CELLULAR_2G
      else -> ConnectivityStatus.CELLULAR_UNKNOWN
    }
  }.getOrNull()

  private fun isAirplaneModeOn(): Boolean = runCatching {
    Settings.Global.getInt(
      context.contentResolver,
      Settings.Global.AIRPLANE_MODE_ON,
      0
    ) != 0
  }.getOrDefault(false)
}
