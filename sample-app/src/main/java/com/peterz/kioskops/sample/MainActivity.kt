package com.peterz.kioskops.sample

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.peterz.kioskops.sdk.KioskOpsSdk
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

      // This payload will be encrypted at rest when SecurityPolicy.encryptQueuePayloads = true
      val ok = sdk.enqueue("SCAN", "{\"scan\":\"12345\"}")

      // Manual heartbeat (captures device posture + policy hash and applies retention)
      sdk.heartbeat(reason = "manual_sample")

      // Optional: manual network sync (only does something if SyncPolicy.enabled == true)
      // val syncResult = sdk.syncOnce()

      // Optional: host-controlled upload (this sample just logs in SampleApp)
      sdk.uploadDiagnosticsNow(metadata = mapOf("ticket" to "INC-DEMO"))

      tv.text = if (ok) {
        "Enqueued + heartbeat + diagnostics upload (host-controlled)."
      } else {
        "Enqueue rejected by compliance guardrails (see logs)."
      }
    }
  }
}
