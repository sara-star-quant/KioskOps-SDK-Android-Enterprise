package com.peterz.kioskops.sdk.diagnostics

import android.content.Context
import android.os.Build
import com.peterz.kioskops.sdk.KioskOpsConfig
import com.peterz.kioskops.sdk.audit.AuditTrail
import com.peterz.kioskops.sdk.fleet.DevicePosture
import com.peterz.kioskops.sdk.logging.LogExporter
import com.peterz.kioskops.sdk.logging.RingLog
import com.peterz.kioskops.sdk.telemetry.EncryptedTelemetryStore
import com.peterz.kioskops.sdk.util.Clock
import com.peterz.kioskops.sdk.util.DeviceId
import com.peterz.kioskops.sdk.queue.QuarantinedEventSummary
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DiagnosticsExporter(
  private val context: Context,
  private val cfgProvider: () -> KioskOpsConfig,
  private val logs: RingLog,
  private val logExporter: LogExporter,
  private val telemetry: EncryptedTelemetryStore,
  private val audit: AuditTrail,
  private val clock: Clock,
  private val sdkVersion: String,
  private val queueDepthProvider: suspend () -> Long,
  private val quarantinedSummaryProvider: suspend () -> List<QuarantinedEventSummary>,
  private val postureProvider: () -> DevicePosture,
  private val policyHashProvider: () -> String,
) {
  private val json = Json { explicitNulls = false; ignoreUnknownKeys = true }

  suspend fun export(): File {
    val cfg = cfgProvider()
    // Ensure retention is applied before exporting.
    telemetry.purgeOldFiles()
    audit.purgeOldFiles()

    val ts = clock.nowMs()
    val zipFile = File(context.cacheDir, "kioskops_diagnostics_${ts}.zip")

    val posture = postureProvider()
    val policyHash = policyHashProvider()
    val quarantined = quarantinedSummaryProvider()

    ZipOutputStream(BufferedOutputStream(zipFile.outputStream())).use { zos ->
      // Health snapshot
      val includeDeviceId = cfg.telemetryPolicy.includeDeviceId
      val snapshot = HealthSnapshot(
        ts = ts,
        sdkVersion = sdkVersion,
        appPackage = context.packageName,
        androidSdkInt = Build.VERSION.SDK_INT,
        deviceModel = Build.MODEL ?: "unknown",
        manufacturer = posture.manufacturer,
        securityPatch = posture.securityPatch,
        isDeviceOwner = posture.isDeviceOwner,
        isInLockTaskMode = posture.isLockTaskPermitted,
        policyHash = policyHash,
        queueDepth = queueDepthProvider(),
        quarantinedCount = quarantined.size.toLong(),
        locationId = cfg.locationId,
        regionTag = cfg.telemetryPolicy.regionTag,
        includeDeviceId = includeDeviceId,
        sdkDeviceId = if (includeDeviceId) DeviceId.get(context) else null,
      )

      // Note: telemetry and audit share the same encryption setting (encryptTelemetryAtRest).
      // This is intentional—they are both local observability stores with the same threat model.
      val observabilityEncrypted = cfg.securityPolicy.encryptTelemetryAtRest
      val manifest = mapOf(
        "createdAtEpochMs" to ts.toString(),
        "sdkVersion" to sdkVersion,
        "locationId" to cfg.locationId,
        "regionTag" to (cfg.telemetryPolicy.regionTag ?: ""),
        "policyHash" to policyHash,
        "observabilityEncryptedAtRest" to observabilityEncrypted.toString(),
        "queueEncryptedAtRest" to cfg.securityPolicy.encryptQueuePayloads.toString(),
        "logExportEncrypted" to cfg.securityPolicy.encryptExportedLogs.toString(),
        "bundleContainsEncryptedEntries" to cfg.securityPolicy.encryptDiagnosticsBundle.toString(),
      )

      writeEntry(zos, "manifest.json", json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
      writeEntry(zos, "health_snapshot.json", json.encodeToString(snapshot).toByteArray(Charsets.UTF_8))
      writeEntry(zos, "queue/quarantined_summaries.json", json.encodeToString(quarantined).toByteArray(Charsets.UTF_8))

      // Logs (encrypted export if enabled)
      val logFile = logExporter.export()
      addFile(zos, "logs/${logFile.name}", logFile)

      // Telemetry
      for (f in telemetry.listFiles()) {
        addFile(zos, "telemetry/${f.name}", f)
      }

      // Audit
      for (f in audit.listFiles()) {
        addFile(zos, "audit/${f.name}", f)
      }
    }

    logs.i("Diag", "Exported diagnostics: ${zipFile.name} (${zipFile.length()} bytes)")
    return zipFile
  }

  private fun writeEntry(zos: ZipOutputStream, name: String, bytes: ByteArray) {
    zos.putNextEntry(ZipEntry(name))
    zos.write(bytes)
    zos.closeEntry()
  }

  private fun addFile(zos: ZipOutputStream, entryName: String, file: File) {
    zos.putNextEntry(ZipEntry(entryName))
    FileInputStream(file).use { fis ->
      val buf = ByteArray(16 * 1024)
      while (true) {
        val r = fis.read(buf)
        if (r <= 0) break
        zos.write(buf, 0, r)
      }
    }
    zos.closeEntry()
  }
}
