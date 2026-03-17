/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.pii

/**
 * Policy controlling PII detection behavior.
 *
 * @property enabled Whether PII detection is active.
 * @property detectionMode Detection strategy: rule-based regex or ML-assisted.
 * @property action What to do when PII is detected.
 * @property minimumConfidence Minimum confidence threshold for a finding to be actionable.
 * @property fieldExclusions JSON paths to exclude from scanning (e.g., "$.metadata.tag").
 * @property mlTimeoutMs Timeout for ML-assisted detection before fallback to rule-based.
 * @since 0.5.0
 */
data class PiiPolicy(
  val enabled: Boolean = false,
  val detectionMode: PiiDetectionMode = PiiDetectionMode.RULE_BASED,
  val action: PiiAction = PiiAction.REJECT,
  val minimumConfidence: Float = 0.7f,
  val fieldExclusions: Set<String> = emptySet(),
  val mlTimeoutMs: Long = 200L,
) {
  companion object {
    fun disabledDefaults() = PiiPolicy(enabled = false)

    fun rejectDefaults() = PiiPolicy(
      enabled = true,
      action = PiiAction.REJECT,
    )

    fun redactDefaults() = PiiPolicy(
      enabled = true,
      action = PiiAction.REDACT_VALUE,
    )
  }
}

/**
 * PII detection strategy.
 * @since 0.5.0
 */
enum class PiiDetectionMode {
  RULE_BASED,
  ML_ASSISTED,
}

/**
 * Action to take when PII is detected.
 * @since 0.5.0
 */
enum class PiiAction {
  REJECT,
  REDACT_VALUE,
  FLAG_AND_ALLOW,
}
