package com.peterz.kioskops.sdk.audit

import android.content.Context
import com.peterz.kioskops.sdk.compliance.RetentionPolicy
import com.peterz.kioskops.sdk.crypto.CryptoProvider
import com.peterz.kioskops.sdk.util.Clock
import com.peterz.kioskops.sdk.util.Hashing
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * Local tamper-evident audit trail.
 *
 * This is not “unbreakable”. It is designed to make accidental corruption and casual tampering obvious.
 * We keep it local-first and encrypt-at-rest when crypto is enabled.
 */
class AuditTrail(
  context: Context,
  private val retentionProvider: () -> RetentionPolicy,
  private val clock: Clock,
  private val crypto: CryptoProvider,
) {
  private val json = Json { explicitNulls = false; ignoreUnknownKeys = true }
  private val dir = File(context.filesDir, "kioskops_audit").apply { mkdirs() }
  private val dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

  @Volatile private var lastHash: String = "GENESIS"

  /**
   * Records an audit event with hash-chain linking.
   *
   * Note: The hash chain is **process-local**. After app restart, the chain restarts from "GENESIS".
   * This is intentional and documents process lifetime boundaries. Cross-restart chain continuity
   * would require persisting `lastHash`, which adds complexity without significant security benefit
   * (an attacker who can modify files can also modify the persisted hash).
   *
   * @param name Event name (e.g., "sync_batch_success", "enqueue_rejected")
   * @param fields Optional key-value pairs for event context (avoid PII)
   */
  fun record(name: String, fields: Map<String, String> = emptyMap()) {
    val ts = clock.nowMs()
    val prev = lastHash
    val payload = "$ts|$name|${fields.toSortedMap()}|$prev"
    val hash = Hashing.sha256Base64Url(payload)
    lastHash = hash

    val e = AuditEvent(ts = ts, name = name, fields = fields, prevHash = prev, hash = hash)

    val day = dayFmt.format(Instant.ofEpochMilli(ts))
    val ext = if (crypto.isEnabled) ".jsonl.enc" else ".jsonl"
    val f = File(dir, "audit_${day}${ext}")

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

  /**
   * Deletes audit files older than the configured retention period.
   * Files are retained for [RetentionPolicy.retainAuditDays] full days.
   */
  fun purgeOldFiles() {
    val retention = retentionProvider()
    val cutoffMs = clock.nowMs() - retention.retainAuditDays.toLong() * 24 * 60 * 60 * 1000
    val files = dir.listFiles() ?: return

    for (f in files) {
      val day = parseDayFromName(f.name) ?: continue
      val dayStartMs = Instant.parse("${day}T00:00:00Z").toEpochMilli()
      // Use <= to ensure files are deleted on the exact retention boundary, not one day late.
      if (dayStartMs <= cutoffMs) {
        f.delete()
      }
    }
  }

  private fun parseDayFromName(name: String): String? {
    val m = Regex("""audit_(\d{4}-\d{2}-\d{2})\.jsonl(\.enc)?""").find(name)
    return m?.groupValues?.get(1)
  }
}
