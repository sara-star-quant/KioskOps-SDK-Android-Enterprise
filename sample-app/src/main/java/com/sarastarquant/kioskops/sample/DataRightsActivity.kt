/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sample

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.sarastarquant.kioskops.sdk.KioskOpsSdk
import com.sarastarquant.kioskops.sdk.compliance.DataDeletionResult
import com.sarastarquant.kioskops.sdk.compliance.DataExportResult
import com.sarastarquant.kioskops.sdk.queue.EnqueueResult
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Demonstrates GDPR data rights operations: export, delete, and full wipe.
 *
 * Provides controls for enqueuing test events as a specific user, then exercising
 * the Art. 20 (portability) and Art. 17 (erasure) APIs.
 *
 * IMPORTANT: This is a sample app without authentication. In production, the host
 * application MUST verify the caller's identity before invoking data rights APIs.
 * The SDK does not enforce authorization; it deletes or exports whichever userId
 * is provided. Without host-side authentication, any user on a shared device could
 * access or erase another user's local data.
 *
 * Accessibility: follows the same WCAG 2.1 AA patterns as [MainActivity].
 */
class DataRightsActivity : Activity() {
  private val scope = MainScope()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_data_rights)

    val inputUserId = findViewById<EditText>(R.id.inputUserId)
    val btnEnqueueAsUser = findViewById<Button>(R.id.btnEnqueueAsUser)
    val btnExportUserData = findViewById<Button>(R.id.btnExportUserData)
    val btnDeleteUserData = findViewById<Button>(R.id.btnDeleteUserData)
    val btnWipeAllData = findViewById<Button>(R.id.btnWipeAllData)
    val resultText = findViewById<TextView>(R.id.resultText)

    val sdk = KioskOpsSdk.get()

    btnEnqueueAsUser.setOnClickListener {
      val userId = inputUserId.text.toString().trim()
      if (userId.isEmpty()) {
        resultText.text = "User ID is required."
        return@setOnClickListener
      }

      scope.launch {
        val types = listOf("SCAN", "SCAN", "SCAN")
        val payloads = listOf(
          "{\"scan\":\"test-event-1\"}",
          "{\"scan\":\"test-event-2\"}",
          "{\"scan\":\"test-event-3\"}",
        )
        var accepted = 0
        var rejected = 0

        for (i in types.indices) {
          val result = sdk.enqueueDetailed(
            type = types[i],
            payloadJson = payloads[i],
            userId = userId,
          )
          when (result) {
            is EnqueueResult.Accepted -> accepted++
            is EnqueueResult.PiiRedacted -> accepted++
            is EnqueueResult.Rejected -> rejected++
          }
        }

        resultText.text = "Enqueued 3 test events for user '$userId': accepted=$accepted, rejected=$rejected"
      }
    }

    btnExportUserData.setOnClickListener {
      val userId = inputUserId.text.toString().trim()
      if (userId.isEmpty()) {
        resultText.text = "User ID is required."
        return@setOnClickListener
      }

      scope.launch {
        val export = sdk.dataRights.exportUserData(userId)
        resultText.text = when (export) {
          is DataExportResult.Success -> buildString {
            appendLine("Export successful:")
            appendLine("File: ${export.exportFile.absolutePath}")
            appendLine("Queue events: ${export.queueEventCount}")
            appendLine("Audit events: ${export.auditEventCount}")
            appendLine("Telemetry files: ${export.telemetryFileCount}")
          }
          is DataExportResult.NoData -> "No data found for user '$userId'."
          is DataExportResult.Failed -> "Export failed: ${export.reason}"
        }
      }
    }

    btnDeleteUserData.setOnClickListener {
      val userId = inputUserId.text.toString().trim()
      if (userId.isEmpty()) {
        resultText.text = "User ID is required."
        return@setOnClickListener
      }

      scope.launch {
        val deletion = sdk.dataRights.deleteUserData(userId)
        resultText.text = when (deletion) {
          is DataDeletionResult.Success -> buildString {
            appendLine("Deletion successful:")
            appendLine("Queue events deleted: ${deletion.queueEventsDeleted}")
            appendLine("Audit events deleted: ${deletion.auditEventsDeleted}")
          }
          is DataDeletionResult.Failed -> "Deletion failed: ${deletion.reason}"
        }
      }
    }

    btnWipeAllData.setOnClickListener {
      AlertDialog.Builder(this)
        .setTitle("Confirm Wipe")
        .setMessage("This will delete ALL SDK data from this device. This cannot be undone.")
        .setPositiveButton("Wipe All") { _, _ ->
          scope.launch {
            val wipe = sdk.dataRights.wipeAllSdkData()
            resultText.text = when (wipe) {
              is DataDeletionResult.Success -> "All SDK data wiped successfully."
              is DataDeletionResult.Failed -> "Wipe failed: ${wipe.reason}"
            }
          }
        }
        .setNegativeButton("Cancel", null)
        .show()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    scope.cancel()
  }
}
