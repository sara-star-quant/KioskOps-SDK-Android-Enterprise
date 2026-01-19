package com.peterz.kioskops.sdk.telemetry

import android.content.Context
import com.peterz.kioskops.sdk.compliance.RetentionPolicy
import com.peterz.kioskops.sdk.compliance.TelemetryPolicy
import com.peterz.kioskops.sdk.crypto.CryptoProvider
import com.peterz.kioskops.sdk.util.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * Telemetry store designed for worldwide regulatory sanity:
 * - Data minimization: allow-listed fields only
 * - Local-first storage
 * - Encryption at rest when crypto is enabled
 * - Deterministic retention by UTC day
 *
 * File format:
 * - If crypto enabled: JSON line is encrypted and base64url-encoded per line (append-friendly)
 * - Else: plain JSONL
 */
class EncryptedTelemetryStore(
  private val context: Context,
  private val policyProvider: () -> TelemetryPolicy,
  private val retentionProvider: () -> RetentionPolicy,
  private val clock: Clock,
  private val crypto: CryptoProvider,
) : TelemetrySink {

  private val json = Json { explicitNulls = false; ignoreUnknownKeys = true }
  private val dir = File(context.filesDir, "kioskops_telemetry").apply { mkdirs() }
  private val dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

  override fun emit(event: String, fields: Map<String, String>) {
    val policy = policyProvider()
    if (!policy.enabled) return

    val cleaned = TelemetryRedactor.apply(policy, fields)
    val e = TelemetryEvent(ts = clock.nowMs(), name = event, fields = cleaned)

    val day = dayFmt.format(Instant.ofEpochMilli(e.ts))
    val ext = if (crypto.isEnabled) ".jsonl.enc" else ".jsonl"
    val f = File(dir, "telemetry_${day}${ext}")

    val line = (json.encodeToString(e) + "\n").toByteArray(Charsets.UTF_8)
    val outLine: ByteArray = if (crypto.isEnabled) {
      val blob = crypto.encrypt(line)
      val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(blob)
      (b64 + "\n").toByteArray(Charsets.UTF_8)
    } else {
      line
    }

    f.appendBytes(outLine)
    purgeOldFiles()
  }

  fun listFiles(): List<File> {
    purgeOldFiles()
    return dir.listFiles()?.sortedBy { it.name }?.toList() ?: emptyList()
  }

  fun purgeOldFiles() {
    val retention = retentionProvider()
    val cutoffMs = clock.nowMs() - retention.retainTelemetryDays.toLong() * 24 * 60 * 60 * 1000
    val files = dir.listFiles() ?: return

    for (f in files) {
      val day = parseDayFromName(f.name) ?: continue
      val dayStartMs = Instant.parse("${day}T00:00:00Z").toEpochMilli()
      if (dayStartMs < cutoffMs) {
        f.delete()
      }
    }
  }

  private fun parseDayFromName(name: String): String? {
    val m = Regex("""telemetry_(\d{4}-\d{2}-\d{2})\.jsonl(\.enc)?""").find(name)
    return m?.groupValues?.get(1)
  }
}
