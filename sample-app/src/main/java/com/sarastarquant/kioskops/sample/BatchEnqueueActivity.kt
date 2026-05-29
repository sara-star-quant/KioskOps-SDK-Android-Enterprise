/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sample

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.sarastarquant.kioskops.sdk.KioskOpsErrorListener
import com.sarastarquant.kioskops.sdk.KioskOpsSdk
import com.sarastarquant.kioskops.sdk.queue.EnqueueResult
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Demonstrates batch enqueue and overflow behavior.
 *
 * The SDK is configured with CUI defaults (maxActiveEvents=5000, DROP_OLDEST overflow).
 * This activity rapidly enqueues events and tracks accept/reject/dropped counts to
 * illustrate how the queue handles volume under pressure.
 *
 * Accessibility: follows the same WCAG 2.1 AA patterns as [MainActivity].
 */
class BatchEnqueueActivity : Activity() {
  private val scope = MainScope()
  // CopyOnWriteArrayList: the error listener may fire on a background thread while
  // the main thread reads the list to render results.
  private val errors = java.util.concurrent.CopyOnWriteArrayList<String>()
  private val sdk = KioskOpsSdk.get()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_batch_enqueue)

    val inputBatchSize = findViewById<EditText>(R.id.inputBatchSize)
    val btnStartBatch = findViewById<Button>(R.id.btnStartBatch)
    val progressText = findViewById<TextView>(R.id.progressText)
    val queueDepthText = findViewById<TextView>(R.id.queueDepthText)
    val errorText = findViewById<TextView>(R.id.errorText)

    // Capture errors during the batch run, chaining to the app-wide listener so
    // SampleApp's logging stays active for the activity's lifetime.
    val appListener = (application as SampleApp).errorLogListener
    sdk.setErrorListener(KioskOpsErrorListener { error ->
      errors.add(error.message)
      appListener.onError(error)
    })

    btnStartBatch.setOnClickListener {
      val batchSize = inputBatchSize.text.toString().toIntOrNull() ?: 100
      errors.clear()
      errorText.text = ""
      btnStartBatch.isEnabled = false

      scope.launch {
        var accepted = 0
        var rejected = 0
        var totalDropped = 0

        for (i in 1..batchSize) {
          val result = sdk.enqueueDetailed(
            type = "SCAN",
            payloadJson = "{\"scan\":\"batch-$i\"}",
            userId = "batch-user",
          )
          when (result) {
            is EnqueueResult.Accepted -> {
              accepted++
              totalDropped += result.droppedOldest
            }
            is EnqueueResult.PiiRedacted -> accepted++
            is EnqueueResult.Rejected -> rejected++
          }

          // Update progress every 10 events or on last event.
          if (i % 10 == 0 || i == batchSize) {
            progressText.text = "Enqueued: $i/$batchSize, Accepted: $accepted, Rejected: $rejected"
          }
        }

        val depth = sdk.queueDepth()
        queueDepthText.text = "Queue depth: $depth"
        progressText.text = buildString {
          appendLine("Enqueued: $batchSize/$batchSize, Accepted: $accepted, Rejected: $rejected")
          appendLine("Oldest events dropped (overflow): $totalDropped")
        }

        if (errors.isNotEmpty()) {
          errorText.text = "Errors (${errors.size}):\n${errors.joinToString("\n")}"
        } else {
          errorText.text = "No errors during batch."
        }

        btnStartBatch.isEnabled = true
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    // Drop the activity-capturing listener so the SDK does not retain this Activity.
    sdk.setErrorListener((application as SampleApp).errorLogListener)
    scope.cancel()
  }
}
