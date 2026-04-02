/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.anomaly

/**
 * Interface for detecting anomalous event patterns.
 *
 * Implementations analyze event payloads for unusual characteristics
 * that may indicate attack patterns or data quality issues.
 * NIST SI-4: Information System Monitoring.
 *
 * @since 0.5.0
 */
interface AnomalyDetector {
  /**
   * Analyze an event for anomalies.
   *
   * @param eventType The event type identifier.
   * @param payloadJson The JSON payload to analyze.
   * @return Analysis result with anomaly score and reasons.
   */
  fun analyze(eventType: String, payloadJson: String): AnomalyResult
}
