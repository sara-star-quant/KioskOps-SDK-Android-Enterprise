package com.peterz.kioskops.sdk.fleet

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import com.peterz.kioskops.sdk.KioskOpsConfig
import com.peterz.kioskops.sdk.compliance.TelemetryPolicy

/**
 * Reads Android "managed configurations" (app restrictions) typically provided by EMM/MDM.
 *
 * This lets you run pilots without re-building the app per site.
 *
 * Keys are intentionally minimal and stable.
 */
object ManagedConfigReader {

  object Keys {
    const val BASE_URL = "kioskops_baseUrl"
    const val LOCATION_ID = "kioskops_locationId"
    const val KIOSK_ENABLED = "kioskops_kioskEnabled"
    const val SYNC_INTERVAL_MINUTES = "kioskops_syncIntervalMinutes"

    const val TELEMETRY_REGION_TAG = "kioskops_regionTag"
    const val TELEMETRY_INCLUDE_DEVICE_ID = "kioskops_includeDeviceId"
  }

  fun read(context: Context, defaults: KioskOpsConfig): KioskOpsConfig {
    val rm: RestrictionsManager? = context.getSystemService(RestrictionsManager::class.java)
    val bundle = rm?.applicationRestrictions ?: Bundle.EMPTY
    return applyBundle(bundle, defaults)
  }

  internal fun applyBundle(bundle: Bundle, defaults: KioskOpsConfig): KioskOpsConfig {
    val baseUrl = bundle.getString(Keys.BASE_URL)?.takeIf { it.isNotBlank() } ?: defaults.baseUrl
    val locationId = bundle.getString(Keys.LOCATION_ID)?.takeIf { it.isNotBlank() } ?: defaults.locationId
    val kioskEnabled = if (bundle.containsKey(Keys.KIOSK_ENABLED)) bundle.getBoolean(Keys.KIOSK_ENABLED) else defaults.kioskEnabled
    val syncInterval = if (bundle.containsKey(Keys.SYNC_INTERVAL_MINUTES)) {
      bundle.getInt(Keys.SYNC_INTERVAL_MINUTES).toLong().coerceAtLeast(5L)
    } else defaults.syncIntervalMinutes

    val regionTag = bundle.getString(Keys.TELEMETRY_REGION_TAG)?.takeIf { it.isNotBlank() } ?: defaults.telemetryPolicy.regionTag
    val includeDeviceId = if (bundle.containsKey(Keys.TELEMETRY_INCLUDE_DEVICE_ID)) bundle.getBoolean(Keys.TELEMETRY_INCLUDE_DEVICE_ID) else defaults.telemetryPolicy.includeDeviceId

    val telemetry = defaults.telemetryPolicy.copy(
      regionTag = regionTag,
      includeDeviceId = includeDeviceId
    )

    return defaults.copy(
      baseUrl = baseUrl,
      locationId = locationId,
      kioskEnabled = kioskEnabled,
      syncIntervalMinutes = syncInterval,
      telemetryPolicy = telemetry
    )
  }
}
