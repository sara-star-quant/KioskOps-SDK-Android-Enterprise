package com.peterz.kioskops.sdk.fleet

import android.content.Context
import com.peterz.kioskops.sdk.KioskOpsConfig
import com.peterz.kioskops.sdk.util.Hashing

/**
 * Detects policy/config drift across runs.
 *
 * IMPORTANT: We hash a sanitized projection of config. Do not include secrets.
 *
 * This is local-only; whether you transmit drift events off-device is controlled by the host app.
 */
class PolicyDriftDetector(context: Context) {
  private val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

  data class DriftResult(
    val drifted: Boolean,
    val previousHash: String?,
    val currentHash: String,
    val firstSeenAtMs: Long,
    val lastChangedAtMs: Long
  )

  fun checkAndStore(cfg: KioskOpsConfig, nowMs: Long): DriftResult {
    val current = hashOf(sanitizedProjection(cfg))
    val prev = sp.getString(KEY_HASH, null)
    val firstSeen = sp.getLong(KEY_FIRST_SEEN, 0L).let { if (it == 0L) nowMs else it }

    val changed = prev != null && prev != current
    val lastChanged = if (changed) nowMs else sp.getLong(KEY_LAST_CHANGED, 0L).let { if (it == 0L) nowMs else it }

    sp.edit()
      .putString(KEY_HASH, current)
      .putLong(KEY_FIRST_SEEN, firstSeen)
      .putLong(KEY_LAST_CHANGED, lastChanged)
      .apply()

    return DriftResult(
      drifted = changed,
      previousHash = prev,
      currentHash = current,
      firstSeenAtMs = firstSeen,
      lastChangedAtMs = lastChanged
    )
  }

  /**
   * Keep this stable and non-sensitive.
   * If you add fields, it's okay that the drift hash changes: that's the point.
   */
  internal fun sanitizedProjection(cfg: KioskOpsConfig): String {
    val t = cfg.telemetryPolicy
    val s = cfg.securityPolicy
    val r = cfg.retentionPolicy

    return buildString {
      append("baseUrl=").append(cfg.baseUrl).append('|')
      append("locationId=").append(cfg.locationId).append('|')
      append("kioskEnabled=").append(cfg.kioskEnabled).append('|')
      append("syncIntervalMinutes=").append(cfg.syncIntervalMinutes).append('|')

      // telemetry knobs (avoid allow-list details; it can be large and not always stable)
      append("telemetryEnabled=").append(t.enabled).append('|')
      append("telemetryIncludeDeviceId=").append(t.includeDeviceId).append('|')

      // security knobs
      append("encryptQueuePayloads=").append(s.encryptQueuePayloads).append('|')
      append("encryptTelemetryAtRest=").append(s.encryptTelemetryAtRest).append('|')
      append("encryptDiagnosticsBundle=").append(s.encryptDiagnosticsBundle).append('|')
      append("encryptExportedLogs=").append(s.encryptExportedLogs).append('|')
      append("maxEventPayloadBytes=").append(s.maxEventPayloadBytes).append('|')
      append("allowRawPayloadStorage=").append(s.allowRawPayloadStorage).append('|')

      // retention knobs
      append("retainSentEventsDays=").append(r.retainSentEventsDays).append('|')
      append("retainFailedEventsDays=").append(r.retainFailedEventsDays).append('|')
      append("retainTelemetryDays=").append(r.retainTelemetryDays).append('|')
      append("retainAuditDays=").append(r.retainAuditDays).append('|')
      append("retainLogsDays=").append(r.retainLogsDays).append('|')

      // network sync knobs (avoid secrets; only behavior)
      val p = cfg.syncPolicy
      append("syncEnabled=").append(p.enabled).append('|')
      append("syncEndpointPath=").append(p.endpointPath).append('|')
      append("syncBatchSize=").append(p.batchSize).append('|')
      append("syncRequireUnmetered=").append(p.requireUnmeteredNetwork).append('|')
      append("syncMaxAttemptsPerEvent=").append(p.maxAttemptsPerEvent)
    }
  }

  private fun hashOf(s: String): String = Hashing.sha256Base64Url(s)

  companion object {
    private const val PREF = "kioskops_policy"
    private const val KEY_HASH = "cfg_hash"
    private const val KEY_FIRST_SEEN = "first_seen_ms"
    private const val KEY_LAST_CHANGED = "last_changed_ms"
  }
}
