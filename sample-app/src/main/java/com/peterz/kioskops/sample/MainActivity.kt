package com.peterz.kioskops.sample

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.peterz.kioskops.sdk.KioskOpsSdk
import com.peterz.kioskops.sdk.queue.EnqueueResult
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : Activity() {
  private val scope = MainScope()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val tv = TextView(this)
    tv.text = "KioskOps SDK Sample"
    setContentView(tv)

    scope.launch {
      val sdk = KioskOpsSdk.get()

      // v0.5.0: enqueue with userId for GDPR tracking
      val result = sdk.enqueueDetailed(
        type = "SCAN",
        payloadJson = "{\"scan\":\"12345\"}",
        userId = "user-001",
      )

      // Manual heartbeat (captures device posture + policy hash and applies retention)
      sdk.heartbeat(reason = "manual_sample")

      // v0.5.0: Data rights demo
      // val exportResult = sdk.dataRights.exportUserData("user-001")
      // val deleteResult = sdk.dataRights.deleteUserData("user-001")

      // Optional: host-controlled upload (this sample just logs in SampleApp)
      sdk.uploadDiagnosticsNow(metadata = mapOf("ticket" to "INC-DEMO"))

      tv.text = when (result) {
        is EnqueueResult.Accepted -> "Enqueued (id=${result.id})"
        is EnqueueResult.PiiRedacted -> "Enqueued with PII redacted: ${result.redactedFields}"
        is EnqueueResult.Rejected -> "Rejected: ${result::class.java.simpleName}"
      }
    }
  }
}
