/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.compliance

import android.content.Context
import com.sarastarquant.kioskops.sdk.audit.AuditTrail
import com.sarastarquant.kioskops.sdk.audit.PersistentAuditTrail
import com.sarastarquant.kioskops.sdk.queue.QueueRepository
import com.sarastarquant.kioskops.sdk.telemetry.EncryptedTelemetryStore
import com.sarastarquant.kioskops.sdk.util.DeviceId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * GDPR and data rights management.
 *
 * Provides APIs for data export (Art. 20), erasure (Art. 17), and full device wipe.
 *
 * When a [DataRightsAuthorizer] is configured, all destructive operations require
 * authorization before proceeding. If [requireAuthorization] is true and no authorizer
 * is set, operations return [DataDeletionResult.Unauthorized] or [DataExportResult.Unauthorized].
 *
 * @since 0.5.0 Rewritten with QueueRepository and PersistentAuditTrail support.
 * @since 1.0.0 Authorization callback support.
 */
class DataRightsManager(
  private val context: Context,
  private val telemetry: EncryptedTelemetryStore,
  private val audit: AuditTrail,
  private val queue: QueueRepository? = null,
  private val persistentAudit: PersistentAuditTrail? = null,
  private val requireAuthorization: Boolean = false,
) {

  @Volatile
  private var authorizer: DataRightsAuthorizer? = null

  /**
   * Set the authorization callback for data rights operations.
   *
   * Pass null to remove the authorizer. If [requireAuthorization] is true
   * and no authorizer is configured, operations will be blocked.
   *
   * @since 1.0.0
   */
  fun setAuthorizer(authorizer: DataRightsAuthorizer?) {
    this.authorizer = authorizer
  }

  private suspend fun checkAuthorization(
    operation: DataRightsOperation,
    userId: String,
  ): Boolean {
    val auth = authorizer
    if (auth != null) {
      val allowed = auth.authorize(operation, userId)
      if (!allowed) {
        persistentAudit?.record(
          "data_rights_unauthorized",
          mapOf("operation" to operation.name, "userId" to userId),
        )
      }
      return allowed
    }
    val blocked = requireAuthorization
    if (blocked) {
      persistentAudit?.record(
        "data_rights_blocked_no_authorizer",
        mapOf("operation" to operation.name),
      )
    }
    return !blocked
  }

  private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

  /**
   * Export all local data for a specific user (GDPR Art. 20 portability).
   *
   * @param userId The user identifier to export data for.
   * @return Export result containing a zip file with all user data.
   * @since 0.5.0
   */
  suspend fun exportUserData(userId: String): DataExportResult {
    if (!checkAuthorization(DataRightsOperation.EXPORT, userId)) {
      return DataExportResult.Unauthorized(DataRightsOperation.EXPORT)
    }
    return try {
      val exportDir = File(context.cacheDir, "kioskops_data_exports")
      exportDir.mkdirs()
      val zipFile = File(exportDir, "user_export_${userId}_${System.currentTimeMillis()}.zip")

      var queueCount = 0
      var auditCount = 0
      val telemetryFiles = telemetry.listFiles()

      ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
        // Queue events
        queue?.let { q ->
          val events = q.getByUserId(userId)
          queueCount = events.size
          if (events.isNotEmpty()) {
            zos.putNextEntry(ZipEntry("queue_events.jsonl"))
            for (event in events) {
              val decoded = try { q.decodePayloadJson(event) } catch (_: Exception) { "[encrypted]" }
              val line = json.encodeToString(mapOf(
                "id" to event.id,
                "type" to event.type,
                "createdAt" to event.createdAtEpochMs.toString(),
                "state" to event.state,
                "payload" to decoded,
              )) + "\n"
              zos.write(line.toByteArray(Charsets.UTF_8))
            }
            zos.closeEntry()
          }
        }

        // Audit events
        persistentAudit?.let { pa ->
          val events = pa.getEventsByUserId(userId)
          auditCount = events.size
          if (events.isNotEmpty()) {
            zos.putNextEntry(ZipEntry("audit_events.jsonl"))
            for (event in events) {
              val line = json.encodeToString(mapOf(
                "id" to event.id,
                "ts" to event.ts.toString(),
                "name" to event.name,
                "fields" to event.fieldsJson,
              )) + "\n"
              zos.write(line.toByteArray(Charsets.UTF_8))
            }
            zos.closeEntry()
          }
        }

        // Telemetry files
        for (file in telemetryFiles) {
          zos.putNextEntry(ZipEntry("telemetry/${file.name}"))
          file.inputStream().use { it.copyTo(zos) }
          zos.closeEntry()
        }

        // Metadata
        zos.putNextEntry(ZipEntry("metadata.json"))
        val meta = json.encodeToString(mapOf(
          "userId" to userId,
          "exportedAt" to System.currentTimeMillis().toString(),
          "queueEventCount" to queueCount.toString(),
          "auditEventCount" to auditCount.toString(),
          "telemetryFileCount" to telemetryFiles.size.toString(),
        ))
        zos.write(meta.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
      }

      if (queueCount == 0 && auditCount == 0 && telemetryFiles.isEmpty()) {
        zipFile.delete()
        DataExportResult.NoData(userId)
      } else {
        DataExportResult.Success(zipFile, queueCount, auditCount, telemetryFiles.size)
      }
    } catch (e: Exception) {
      DataExportResult.Failed("Export failed: ${e.message}")
    }
  }

  /**
   * Export all local SDK data (not user-specific).
   *
   * @return Export result containing a zip file with all SDK data.
   * @since 0.5.0
   */
  suspend fun exportAllLocalData(): DataExportResult {
    return try {
      val exportDir = File(context.cacheDir, "kioskops_data_exports")
      exportDir.mkdirs()
      val zipFile = File(exportDir, "full_export_${System.currentTimeMillis()}.zip")

      val telemetryFiles = telemetry.listFiles()
      val auditFiles = audit.listFiles()

      ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
        for (file in telemetryFiles) {
          zos.putNextEntry(ZipEntry("telemetry/${file.name}"))
          file.inputStream().use { it.copyTo(zos) }
          zos.closeEntry()
        }
        for (file in auditFiles) {
          zos.putNextEntry(ZipEntry("audit/${file.name}"))
          file.inputStream().use { it.copyTo(zos) }
          zos.closeEntry()
        }
      }

      DataExportResult.Success(zipFile, 0, 0, telemetryFiles.size)
    } catch (e: Exception) {
      DataExportResult.Failed("Export failed: ${e.message}")
    }
  }

  /**
   * Delete all data for a specific user (GDPR Art. 17 erasure).
   *
   * The deletion itself is audit-logged (without userId to avoid re-creating data).
   *
   * @param userId The user identifier whose data should be deleted.
   * @return Deletion result.
   * @since 0.5.0
   */
  suspend fun deleteUserData(userId: String): DataDeletionResult {
    if (!checkAuthorization(DataRightsOperation.DELETE, userId)) {
      return DataDeletionResult.Unauthorized(DataRightsOperation.DELETE)
    }
    return try {
      val queueDeleted = queue?.deleteByUserId(userId) ?: 0
      val auditDeleted = persistentAudit?.deleteEventsByUserId(userId) ?: 0

      // Audit the deletion itself (without userId to avoid circular data creation)
      persistentAudit?.record(
        "user_data_deleted",
        mapOf(
          "queueDeleted" to queueDeleted.toString(),
          "auditDeleted" to auditDeleted.toString(),
        ),
      )

      DataDeletionResult.Success(queueDeleted, auditDeleted)
    } catch (e: Exception) {
      DataDeletionResult.Failed("Deletion failed: ${e.message}")
    }
  }

  /**
   * Wipe all SDK data from the device.
   *
   * Deletes data across all databases, files, and SharedPreferences.
   *
   * @return Deletion result.
   * @since 0.5.0
   */
  suspend fun wipeAllSdkData(): DataDeletionResult {
    if (!checkAuthorization(DataRightsOperation.WIPE, "")) {
      return DataDeletionResult.Unauthorized(DataRightsOperation.WIPE)
    }
    return try {
      // Delete database files
      context.deleteDatabase("kiosk_ops_queue.db")
      context.deleteDatabase("kioskops_audit.db")
      context.deleteDatabase("kioskops_config.db")

      // Delete telemetry and audit files
      val filesDir = context.filesDir
      listOf("kioskops_telemetry", "kioskops_audit", "kioskops_logs").forEach { dirName ->
        File(filesDir, dirName).deleteRecursively()
      }

      // Delete exported files
      listOf("kioskops_data_exports", "kioskops_audit_exports").forEach { dirName ->
        File(context.cacheDir, dirName).deleteRecursively()
      }

      // Clear SharedPreferences
      listOf("kioskops_policy", "kioskops_device_id", "kioskops_install_secret").forEach { prefName ->
        context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().apply()
      }

      DataDeletionResult.Success(queueEventsDeleted = -1, auditEventsDeleted = -1)
    } catch (e: Exception) {
      DataDeletionResult.Failed("Wipe failed: ${e.message}")
    }
  }

  fun resetSdkDeviceId(): String = DeviceId.reset(context)
}
