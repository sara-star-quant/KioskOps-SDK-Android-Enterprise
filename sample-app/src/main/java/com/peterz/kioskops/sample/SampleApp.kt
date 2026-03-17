package com.peterz.kioskops.sample

import android.app.Application
import android.util.Log
import com.peterz.kioskops.sdk.KioskOpsConfig
import com.peterz.kioskops.sdk.KioskOpsSdk
import com.peterz.kioskops.sdk.compliance.RetentionPolicy
import com.peterz.kioskops.sdk.compliance.SecurityPolicy
import com.peterz.kioskops.sdk.compliance.TelemetryPolicy
import com.peterz.kioskops.sdk.fleet.DiagnosticsUploader
import com.peterz.kioskops.sdk.pii.PiiPolicy
import com.peterz.kioskops.sdk.sync.SyncPolicy
import com.peterz.kioskops.sdk.transport.AuthProvider
import com.peterz.kioskops.sdk.validation.ValidationPolicy

class SampleApp : Application() {
  override fun onCreate() {
    super.onCreate()

    // In real enterprise deployments, provide config via managed app restrictions (MDM/EMM).
    val cfg = KioskOpsConfig(
      baseUrl = "https://example.invalid/",
      locationId = "DEMO-LOC",
      kioskEnabled = false,
      // Opt-in network sync. Default is disabled to avoid silent off-device transfer.
      syncPolicy = SyncPolicy.disabledDefaults(),
      securityPolicy = SecurityPolicy.maximalistDefaults(),
      retentionPolicy = RetentionPolicy.maximalistDefaults(),
      telemetryPolicy = TelemetryPolicy.maximalistDefaults(),
      // v0.5.0: Enable validation and PII redaction
      validationPolicy = ValidationPolicy.permissiveDefaults(),
      piiPolicy = PiiPolicy.redactDefaults(),
    )

    val sdk = KioskOpsSdk.init(
      this,
      configProvider = { cfg },
      // If you enable SyncPolicy(enabled=true), provide an AuthProvider for your ingest endpoint.
      authProvider = AuthProvider { builder ->
        builder.header("Authorization", "Bearer <token>")
      }
    )

    // v0.5.0: Register event schemas for validation
    sdk.schemaRegistry.register("SCAN", """
      {
        "type": "object",
        "required": ["scan"],
        "properties": {
          "scan": {"type": "string", "minLength": 1}
        }
      }
    """.trimIndent())

    // Demonstrates host-controlled diagnostics upload wiring.
    sdk.setDiagnosticsUploader(
      DiagnosticsUploader { file, metadata ->
        Log.i("SampleUploader", "Would upload ${file.name} (${file.length()} bytes) meta=$metadata")
      }
    )
  }
}
