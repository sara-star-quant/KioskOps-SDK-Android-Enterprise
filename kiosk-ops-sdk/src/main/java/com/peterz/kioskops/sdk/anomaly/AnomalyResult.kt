/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.anomaly

/**
 * Result of anomaly analysis.
 *
 * @property score Anomaly score between 0.0 (normal) and 1.0 (highly anomalous).
 * @property reasons List of reasons contributing to the anomaly score.
 * @property recommendedAction Suggested action based on the score.
 * @since 0.5.0
 */
data class AnomalyResult(
  val score: Float,
  val reasons: List<String>,
  val recommendedAction: AnomalyAction,
) {
  companion object {
    val NORMAL = AnomalyResult(score = 0.0f, reasons = emptyList(), recommendedAction = AnomalyAction.ALLOW)
  }
}

/**
 * Recommended action based on anomaly analysis.
 * @since 0.5.0
 */
enum class AnomalyAction {
  ALLOW,
  FLAG,
  REJECT,
}
