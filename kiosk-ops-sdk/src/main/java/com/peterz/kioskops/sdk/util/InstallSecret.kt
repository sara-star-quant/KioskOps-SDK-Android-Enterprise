package com.peterz.kioskops.sdk.util

import android.content.Context
import java.security.SecureRandom
import java.util.Base64

/**
 * Per-install secret used for deterministic idempotency and similar local-only features.
 *
 * This secret never leaves the device unless the host explicitly exports diagnostics.
 * It is not considered personal data by itself, but treat it as sensitive.
 */
object InstallSecret {
  private const val PREFS = "kioskops_install_secret"
  private const val KEY = "secret_b64"

  fun getOrCreate(context: Context, bytes: Int = 32): ByteArray {
    val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val existing = sp.getString(KEY, null)
    if (!existing.isNullOrBlank()) {
      return Base64.getDecoder().decode(existing)
    }
    val buf = ByteArray(bytes)
    SecureRandom().nextBytes(buf)
    sp.edit().putString(KEY, Base64.getEncoder().encodeToString(buf)).apply()
    return buf
  }
}
