package com.sarastarquant.kioskops.sample

import android.app.Application
import android.util.Log
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.KioskOpsErrorListener
import com.sarastarquant.kioskops.sdk.KioskOpsSdk
import com.sarastarquant.kioskops.sdk.fleet.DiagnosticsUploader
import com.sarastarquant.kioskops.sdk.sync.SyncPolicy
import com.sarastarquant.kioskops.sdk.transport.AuthProvider

class SampleApp : Application() {
  override fun onCreate() {
    super.onCreate()

    // Use a compliance preset for the target deployment profile.
    // Available presets: fedRampDefaults, gdprDefaults, cuiDefaults, cjisDefaults,
    // asdEssentialEightDefaults. Each preset configures encryption, validation,
    // PII handling, anomaly detection, and retention for the target framework.
    //
    // In real enterprise deployments, provide config via managed app restrictions (MDM/EMM).
    val cfg = KioskOpsConfig.cuiDefaults(
      baseUrl = "https://example.invalid/",
      locationId = "DEMO-LOC",
    ).copy(
      // Opt-in network sync. Default is disabled to avoid silent off-device transfer.
      syncPolicy = SyncPolicy.disabledDefaults(),
    )

    val sdk = KioskOpsSdk.init(
      context = this,
      configProvider = { cfg },
      // If you enable SyncPolicy(enabled=true), provide an AuthProvider for your ingest endpoint.
      authProvider = AuthProvider { builder ->
        builder.header("Authorization", "Bearer <token>")
      },
    )

    // v0.7.0: Error listener for non-fatal SDK operational errors.
    // Fires on sync failures, crypto errors, storage errors; not on expected rejections.
    sdk.setErrorListener(KioskOpsErrorListener { error ->
      Log.w("KioskOps", "SDK error: ${error.message}", error.cause)
    })

    // Register event schemas for validation.
    // cuiDefaults enables strict validation; unregistered event types are rejected.
    sdk.schemaRegistry.register("SCAN", """
      {
        "type": "object",
        "required": ["scan"],
        "properties": {
          "scan": {"type": "string", "minLength": 1}
        }
      }
    """.trimIndent())

    // Host-controlled diagnostics upload. The SDK never auto-uploads.
    sdk.setDiagnosticsUploader(
      DiagnosticsUploader { file, metadata ->
        Log.i("SampleUploader", "Would upload ${file.name} (${file.length()} bytes) meta=$metadata")
      }
    )
  }
}
