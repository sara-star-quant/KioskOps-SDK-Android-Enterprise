package com.sarastarquant.kioskops.sdk.diagnostics

import android.content.Context
import android.os.Build
import com.sarastarquant.kioskops.sdk.KioskOpsConfig
import com.sarastarquant.kioskops.sdk.audit.AuditTrail
import com.sarastarquant.kioskops.sdk.fleet.DevicePosture
import com.sarastarquant.kioskops.sdk.logging.LogExporter
import com.sarastarquant.kioskops.sdk.logging.RingLog
import com.sarastarquant.kioskops.sdk.telemetry.EncryptedTelemetryStore
import com.sarastarquant.kioskops.sdk.util.Clock
import com.sarastarquant.kioskops.sdk.util.DeviceId
import com.sarastarquant.kioskops.sdk.queue.QuarantinedEventSummary
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

    val maxBytes = cfg.diagnosticsSchedulePolicy.maxExportBytes
    val counting = CountingOutputStream(BufferedOutputStream(zipFile.outputStream()))
    val skipped = mutableListOf<String>()

    ZipOutputStream(counting).use { zos ->
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
        supportsHardwareAttestation = posture.supportsHardwareAttestation,
        keySecurityLevel = posture.keySecurityLevel.name,
        keysAreHardwareBacked = posture.keysAreHardwareBacked,
      )

      // Note: telemetry and audit share the same encryption setting (encryptTelemetryAtRest).
      // This is intentional - they are both local observability stores with the same threat model.
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

      // Critical small entries are never truncated.
      writeEntry(zos, "manifest.json", json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
      writeEntry(zos, "health_snapshot.json", json.encodeToString(snapshot).toByteArray(Charsets.UTF_8))
      writeEntry(zos, "queue/quarantined_summaries.json", json.encodeToString(quarantined).toByteArray(Charsets.UTF_8))

      // Logs (encrypted export if enabled)
      val logFile = logExporter.export()
      if (!addFileCapped(zos, "logs/${logFile.name}", logFile, counting, maxBytes)) {
        skipped.add("logs/${logFile.name}")
      }

      for (f in telemetry.listFiles()) {
        if (!addFileCapped(zos, "telemetry/${f.name}", f, counting, maxBytes)) {
          skipped.add("telemetry/${f.name}")
        }
      }

      for (f in audit.listFiles()) {
        if (!addFileCapped(zos, "audit/${f.name}", f, counting, maxBytes)) {
          skipped.add("audit/${f.name}")
        }
      }

      if (skipped.isNotEmpty()) {
        val marker = buildString {
          append("Diagnostics export truncated to stay within maxExportBytes=$maxBytes.\n")
          append("Omitted entries:\n")
          for (path in skipped) append("  - ").append(path).append('\n')
        }
        writeEntry(zos, "truncation.txt", marker.toByteArray(Charsets.UTF_8))
      }
    }

    if (skipped.isNotEmpty()) {
      logs.w("Diag", "Export truncated: ${skipped.size} entries omitted to stay within ${maxBytes}B")
    }
    logs.i("Diag", "Exported diagnostics: ${zipFile.name} (${zipFile.length()} bytes)")
    return zipFile
  }

  /**
   * Add a file to the ZIP only if doing so would keep `counting.bytesWritten` within
   * [maxBytes]. Returns `false` if the file was skipped. A cap of 0 disables the check.
   */
  private fun addFileCapped(
    zos: ZipOutputStream,
    entryName: String,
    file: File,
    counting: CountingOutputStream,
    maxBytes: Long,
  ): Boolean {
    if (maxBytes > 0 && counting.bytesWritten + file.length() > maxBytes) {
      return false
    }
    addFile(zos, entryName, file)
    return true
  }

  /**
   * Minimal byte-counting [java.io.OutputStream] wrapper. Tracks total bytes written to
   * the underlying stream so the exporter can decide when to stop appending entries.
   */
  private class CountingOutputStream(
    private val delegate: java.io.OutputStream,
  ) : java.io.OutputStream() {
    var bytesWritten: Long = 0L
      private set

    override fun write(b: Int) {
      delegate.write(b)
      bytesWritten++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      delegate.write(b, off, len)
      bytesWritten += len
    }

    override fun flush() = delegate.flush()
    override fun close() = delegate.close()
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
