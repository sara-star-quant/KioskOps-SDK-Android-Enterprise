package com.peterz.kioskops.sdk.logging

import android.content.Context
import com.peterz.kioskops.sdk.crypto.CryptoProvider
import java.io.File
import java.util.Base64

class LogExporter(
  private val context: Context,
  private val ringLog: RingLog,
  private val crypto: CryptoProvider,
) {
  /**
   * Exports ring buffer logs to a file.
   * If crypto is enabled, the exported content is encrypted and base64url-encoded.
   */
  fun export(): File {
    val plain = ringLog.exportToFile()
    if (!crypto.isEnabled) return plain

    val enc = File(context.cacheDir, plain.name + ".enc")
    val blob = crypto.encrypt(plain.readBytes())
    val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(blob)
    enc.writeText(b64)
    return enc
  }
}
