package com.peterz.kioskops.sdk

import android.content.Context
import androidx.startup.Initializer

/**
 * AndroidX Startup initializer.
 *
 * NOTE: We don't auto-init the SDK here because enterprise integrators often
 * need to supply config dynamically (managed configs, tokens, etc.).
 */
class KioskOpsInitializer : Initializer<Unit> {
  override fun create(context: Context) {
    // no-op
  }

  override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
