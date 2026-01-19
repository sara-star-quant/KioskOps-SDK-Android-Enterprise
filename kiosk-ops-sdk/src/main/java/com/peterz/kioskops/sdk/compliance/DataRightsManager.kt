package com.peterz.kioskops.sdk.compliance

import android.content.Context
import com.peterz.kioskops.sdk.audit.AuditTrail
import com.peterz.kioskops.sdk.telemetry.EncryptedTelemetryStore
import com.peterz.kioskops.sdk.util.DeviceId
import java.io.File

/**
 * Minimal data-rights hooks for SDK-local data.
 *
 * Note: Your host app may store additional data outside the SDK. Those are outside this scope.
 */
class DataRightsManager(
  private val context: Context,
  private val telemetry: EncryptedTelemetryStore,
  private val audit: AuditTrail,
) {
  fun exportLocalFiles(): List<File> = buildList {
    addAll(telemetry.listFiles())
    addAll(audit.listFiles())
  }

  fun resetSdkDeviceId(): String = DeviceId.reset(context)
}
