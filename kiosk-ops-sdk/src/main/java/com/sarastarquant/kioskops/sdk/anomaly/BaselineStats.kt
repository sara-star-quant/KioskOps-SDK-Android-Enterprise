/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.anomaly

/**
 * Pre-computed baseline statistics for seeding [StatisticalAnomalyDetector].
 *
 * Providing a [BaselineStats] via [StatisticalAnomalyDetector.seedBaseline] skips the
 * learning period entirely, allowing anomaly scoring to begin immediately with
 * known-good statistical profiles.
 *
 * @property meanPayloadSize Average payload size in bytes observed during baseline collection.
 * @property payloadSizeVariance Variance of payload sizes observed during baseline collection.
 * @property meanEventRatePerMinute Average event rate per minute during baseline collection.
 * @property knownFieldsByEventType Map of event type to the set of JSON field names observed
 *   during baseline collection. Used for schema deviation scoring.
 * @since 0.7.0
 */
data class BaselineStats(
  val meanPayloadSize: Double,
  val payloadSizeVariance: Double,
  val meanEventRatePerMinute: Double,
  val knownFieldsByEventType: Map<String, Set<String>> = emptyMap(),
)
