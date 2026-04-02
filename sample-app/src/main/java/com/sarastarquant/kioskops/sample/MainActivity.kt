package com.sarastarquant.kioskops.sample

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.sarastarquant.kioskops.sdk.KioskOpsSdk
import com.sarastarquant.kioskops.sdk.queue.EnqueueResult
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Sample activity demonstrating KioskOps SDK integration with WCAG 2.1 AA patterns.
 *
 * Accessibility features demonstrated:
 * - Semantic heading structure (accessibilityHeading)
 * - Content descriptions on interactive and status elements
 * - Minimum 48dp touch targets on all buttons
 * - Live region announcements for result updates
 * - Sufficient color contrast (4.5:1 ratio for body text)
 */
class MainActivity : Activity() {
  private val scope = MainScope()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    val sdkVersion = findViewById<TextView>(R.id.sdkVersion)
    val queueStatus = findViewById<TextView>(R.id.queueStatus)
    val resultText = findViewById<TextView>(R.id.resultText)
    val btnEnqueue = findViewById<Button>(R.id.btnEnqueue)
    val btnHeartbeat = findViewById<Button>(R.id.btnHeartbeat)
    val btnHealthCheck = findViewById<Button>(R.id.btnHealthCheck)

    val sdk = KioskOpsSdk.get()
    sdkVersion.text = "Version: ${KioskOpsSdk.SDK_VERSION}"

    btnEnqueue.setOnClickListener {
      scope.launch {
        val result = sdk.enqueueDetailed(
          type = "SCAN",
          payloadJson = "{\"scan\":\"12345\"}",
          userId = "user-001",
        )
        resultText.text = when (result) {
          is EnqueueResult.Accepted -> "Enqueued (id=${result.id})"
          is EnqueueResult.PiiRedacted -> "Enqueued with PII redacted: ${result.redactedFields}"
          is EnqueueResult.Rejected -> "Rejected: ${result::class.java.simpleName}"
        }
        queueStatus.text = "Queue depth: ${sdk.queueDepth()}"
      }
    }

    btnHeartbeat.setOnClickListener {
      scope.launch {
        sdk.heartbeat(reason = "manual_sample")
        resultText.text = "Heartbeat sent"
      }
    }

    btnHealthCheck.setOnClickListener {
      scope.launch {
        val health = sdk.healthCheck()
        resultText.text = buildString {
          appendLine("Queue: ${health.queueDepth}")
          appendLine("Sync: ${if (health.syncEnabled) "enabled" else "disabled"}")
          appendLine("Auth: ${if (health.authProviderConfigured) "configured" else "none"}")
          appendLine("Encryption: ${if (health.encryptionEnabled) "on" else "off"}")
        }
      }
    }

    scope.launch {
      queueStatus.text = "Queue depth: ${sdk.queueDepth()}"
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    scope.cancel()
  }
}
