/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.anomaly

/**
 * Policy controlling anomaly detection behavior.
 *
 * @property enabled Whether anomaly detection is active.
 * @property sensitivityLevel Detection sensitivity.
 * @property flagThreshold Score threshold for flagging events.
 * @property rejectThreshold Score threshold for rejecting events.
 * @property slidingWindowMinutes Window size for rate-based anomaly detection.
 * @since 0.5.0
 */
data class AnomalyPolicy(
  val enabled: Boolean = false,
  val sensitivityLevel: SensitivityLevel = SensitivityLevel.MEDIUM,
  val flagThreshold: Float = 0.5f,
  val rejectThreshold: Float = 0.8f,
  val slidingWindowMinutes: Int = 5,
) {
  companion object {
    fun disabledDefaults() = AnomalyPolicy(enabled = false)

    fun enabledDefaults() = AnomalyPolicy(
      enabled = true,
      sensitivityLevel = SensitivityLevel.MEDIUM,
    )

    fun highSecurityDefaults() = AnomalyPolicy(
      enabled = true,
      sensitivityLevel = SensitivityLevel.HIGH,
      flagThreshold = 0.3f,
      rejectThreshold = 0.6f,
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
