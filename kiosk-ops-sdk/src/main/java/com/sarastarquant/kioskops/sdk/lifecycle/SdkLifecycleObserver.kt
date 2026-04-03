/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.lifecycle

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.sarastarquant.kioskops.sdk.KioskOpsSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Observes application lifecycle via [ProcessLifecycleOwner].
 *
 * When the host application moves to the background, the observer triggers
 * a heartbeat with reason "app_backgrounded" to record the transition in
 * telemetry and audit. This provides fleet-level visibility into device
 * usage patterns without requiring host app instrumentation.
 *
 * Does not call [KioskOpsSdk.shutdown] on background; the SDK survives
 * background transitions and continues processing WorkManager tasks.
 *
 * @since 0.9.0
 */
internal class SdkLifecycleObserver(
  private val sdkProvider: () -> KioskOpsSdk?,
  private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

  override fun onStop(owner: LifecycleOwner) {
    val sdk = sdkProvider() ?: return
    scope.launch {
      @Suppress("TooGenericExceptionCaught")
      try {
        sdk.heartbeat(reason = "app_backgrounded")
      } catch (e: Exception) {
        Log.w(TAG, "Heartbeat on background failed", e)
      }
    }
  }

  companion object {
    private const val TAG = "KioskOpsLifecycle"

    /**
     * Register the observer with [ProcessLifecycleOwner].
     * Safe to call from any thread; registration is posted to the main thread.
     */
    fun register(sdkProvider: () -> KioskOpsSdk?, scope: CoroutineScope) {
      @Suppress("TooGenericExceptionCaught")
      try {
        val observer = SdkLifecycleObserver(sdkProvider, scope)
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        lifecycle.addObserver(observer)
      } catch (e: Exception) {
        Log.w(TAG, "ProcessLifecycleOwner not available; lifecycle observer disabled", e)
      }
    }
  }
}
