/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.diagnostics

/**
 * Policy for scheduled and remote-triggered diagnostics collection.
 *
 * Security Controls (BSI APP.4.4.A7):
 * - Remote triggers require explicit opt-in
 * - Rate limiting prevents abuse (maxRemoteTriggersPerDay)
 * - Cooldown prevents rapid cycling attacks
 * - All triggers are audit logged
 *
 * @property scheduledEnabled Enable scheduled diagnostics collection
 * @property scheduleType DAILY or WEEKLY schedule
 * @property scheduleHour Hour of day for collection (0-23, device local time)
 * @property scheduleDayOfWeek Day of week for WEEKLY (1=Monday, 7=Sunday)
 * @property remoteTriggerEnabled Enable remote diagnostics trigger via managed config
 * @property maxRemoteTriggersPerDay Maximum remote triggers per day (rate limit)
 * @property remoteTriggerCooldownMs Minimum interval between triggers (ms)
 * @property autoUploadEnabled Auto-upload scheduled diagnostics (requires uploader)
 * @property includeExtendedPosture Include extended posture in reports
 */
data class DiagnosticsSchedulePolicy(
  val scheduledEnabled: Boolean = false,
  val scheduleType: ScheduleType = ScheduleType.DAILY,
  val scheduleHour: Int = 3,
  val scheduleDayOfWeek: Int = 1,
  val remoteTriggerEnabled: Boolean = false,
  val maxRemoteTriggersPerDay: Int = 3,
  val remoteTriggerCooldownMs: Long = 3_600_000L,
  val autoUploadEnabled: Boolean = false,
  val includeExtendedPosture: Boolean = true,
  /**
   * Maximum size in bytes for the diagnostics ZIP produced by
   * [com.sarastarquant.kioskops.sdk.KioskOpsSdk.exportDiagnostics]. On a deep backlog the
   * export can otherwise reach hundreds of MiB, stalling the host-supplied uploader or
   * running the device out of memory. When the cap is exceeded, later telemetry/audit/log
   * entries are omitted and a `truncation.txt` marker entry is added to the ZIP.
   *
   * Default 50 MiB. Set to 0 to disable the cap.
   * @since 1.2.0
   */
  val maxExportBytes: Long = 50L * 1024L * 1024L,
) {
  /**
   * Schedule type for diagnostics collection.
   */
  enum class ScheduleType {
    /** Collect daily at scheduleHour. */
    DAILY,
    /** Collect weekly on scheduleDayOfWeek at scheduleHour. */
    WEEKLY,
  }

  init {
    require(scheduleHour in 0..23) { "scheduleHour must be 0-23" }
    require(scheduleDayOfWeek in 1..7) { "scheduleDayOfWeek must be 1-7" }
    require(maxRemoteTriggersPerDay >= 0) { "maxRemoteTriggersPerDay must be non-negative" }
    require(remoteTriggerCooldownMs >= 0) { "remoteTriggerCooldownMs must be non-negative" }
    require(maxExportBytes >= 0) { "maxExportBytes must be non-negative" }
  }

  companion object {
    /**
     * Disabled defaults - diagnostics scheduling is opt-in.
     */
    fun disabledDefaults() = DiagnosticsSchedulePolicy(
      scheduledEnabled = false,
      remoteTriggerEnabled = false,
    )

    /**
     * Enterprise defaults with daily scheduling and remote trigger.
     *
     * - Daily collection at 3 AM (low activity period)
     * - Remote trigger enabled with rate limiting
     * - No auto-upload (explicit upload required)
     */
    fun enterpriseDefaults() = DiagnosticsSchedulePolicy(
      scheduledEnabled = true,
      scheduleType = ScheduleType.DAILY,
      scheduleHour = 3,
      remoteTriggerEnabled = true,
      maxRemoteTriggersPerDay = 3,
      remoteTriggerCooldownMs = 3_600_000L,
      autoUploadEnabled = false,
      includeExtendedPosture = true,
    )

    /**
     * Weekly schedule for low-activity fleets.
     */
    fun weeklyDefaults() = DiagnosticsSchedulePolicy(
      scheduledEnabled = true,
      scheduleType = ScheduleType.WEEKLY,
      scheduleHour = 2,
      scheduleDayOfWeek = 1, // Monday
      remoteTriggerEnabled = true,
      maxRemoteTriggersPerDay = 5,
      autoUploadEnabled = false,
    )
  }
}
