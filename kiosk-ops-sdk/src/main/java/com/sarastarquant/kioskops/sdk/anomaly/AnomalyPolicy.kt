/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.anomaly

/**
 * Policy controlling anomaly detection behavior.
 *
 * @property enabled Whether anomaly detection is active.
 * @property sensitivityLevel Detection sensitivity.
 * @property flagThreshold Score threshold for flagging events.
 * @property rejectThreshold Score threshold for rejecting events.
 * @property slidingWindowMinutes Window size for rate-based anomaly detection.
 * @property baselineEventCount Number of events to observe before activating scoring.
 *   During the learning period all events return ALLOW. Use [seedBaseline][StatisticalAnomalyDetector.seedBaseline]
 *   to skip the learning period entirely.
 * @since 0.5.0
 */
data class AnomalyPolicy(
  val enabled: Boolean = false,
  val sensitivityLevel: SensitivityLevel = SensitivityLevel.MEDIUM,
  val flagThreshold: Float = 0.5f,
  val rejectThreshold: Float = 0.8f,
  val slidingWindowMinutes: Int = 5,
  val baselineEventCount: Int = 100,
) {
  companion object {
    fun disabledDefaults() = AnomalyPolicy(enabled = false)

    fun enabledDefaults() = AnomalyPolicy(
      enabled = true,
      sensitivityLevel = SensitivityLevel.MEDIUM,
      baselineEventCount = 100,
    )

    fun highSecurityDefaults() = AnomalyPolicy(
      enabled = true,
      sensitivityLevel = SensitivityLevel.HIGH,
      flagThreshold = 0.3f,
      rejectThreshold = 0.6f,
      baselineEventCount = 50,
    )
  }
}

/**
 * Anomaly detection sensitivity level.
 * @since 0.5.0
 */
enum class SensitivityLevel {
  LOW,
  MEDIUM,
  HIGH,
}
