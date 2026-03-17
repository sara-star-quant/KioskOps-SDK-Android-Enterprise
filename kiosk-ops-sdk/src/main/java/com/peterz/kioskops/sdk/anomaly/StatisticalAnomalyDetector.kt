/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.anomaly

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight statistical anomaly detector.
 *
 * Uses O(1) memory per event type for:
 * - Payload size z-score (rolling mean/stddev)
 * - Event rate anomaly detection (sliding window counter)
 * - Schema deviation scoring (unexpected/missing fields)
 * - Field cardinality tracking (detects enumeration attacks)
 *
 * Pure Kotlin, thread-safe, no external dependencies.
 * NIST SI-4: Information System Monitoring.
 *
 * @since 0.5.0
 */
class StatisticalAnomalyDetector(
  private val policy: AnomalyPolicy = AnomalyPolicy.enabledDefaults(),
) : AnomalyDetector {

  private val json = Json { ignoreUnknownKeys = true }

  // Rolling statistics per event type (Welford's online algorithm)
  private val sizeStats = ConcurrentHashMap<String, RollingStats>()

  // Event rate tracking per event type
  private val eventRates = ConcurrentHashMap<String, SlidingWindowCounter>()

  // Known fields per event type (first N events establish baseline)
  private val knownFields = ConcurrentHashMap<String, MutableSet<String>>()

  // Field value cardinality tracking
  private val fieldCardinality = ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicLong>>()

  override fun analyze(eventType: String, payloadJson: String): AnomalyResult {
    val reasons = mutableListOf<String>()
    var totalScore = 0.0f

    val payloadSize = payloadJson.length

    // 1. Payload size z-score
    val stats = sizeStats.getOrPut(eventType) { RollingStats() }
    if (stats.count > BASELINE_COUNT) {
      val zScore = stats.zScore(payloadSize.toDouble())
      if (zScore > sizeZScoreThreshold()) {
        val sizeScore = ((zScore - sizeZScoreThreshold()) / sizeZScoreThreshold()).coerceIn(0.0, 1.0).toFloat()
        totalScore += sizeScore * 0.3f
        reasons.add("payload_size_anomaly: z-score=%.2f, size=%d, mean=%.0f".format(zScore, payloadSize, stats.mean))
      }
    }
    stats.update(payloadSize.toDouble())

    // 2. Event rate anomaly
    val windowMs = policy.slidingWindowMinutes * 60_000L
    val rateCounter = eventRates.getOrPut(eventType) { SlidingWindowCounter(windowMs) }
    val currentRate = rateCounter.incrementAndGet()
    if (rateCounter.baselineRate > 0 && currentRate > rateCounter.baselineRate * rateMultiplierThreshold()) {
      val rateScore = ((currentRate.toFloat() / (rateCounter.baselineRate * rateMultiplierThreshold())) - 1.0f).coerceIn(0.0f, 1.0f)
      totalScore += rateScore * 0.3f
      reasons.add("event_rate_anomaly: rate=$currentRate, baseline=${rateCounter.baselineRate}")
    }

    // 3. Schema deviation
    try {
      val element = json.parseToJsonElement(payloadJson)
      if (element is JsonObject) {
        val fields = element.keys
        val known = knownFields.getOrPut(eventType) { java.util.Collections.synchronizedSet(mutableSetOf()) }

        if (known.size > BASELINE_COUNT) {
          val unexpected = fields - known
          val missing = known - fields
          val deviationRatio = (unexpected.size + missing.size).toFloat() / (known.size + fields.size).coerceAtLeast(1)
          if (deviationRatio > 0.3f) {
            totalScore += deviationRatio.coerceAtMost(1.0f) * 0.2f
            reasons.add("schema_deviation: unexpected=${unexpected.take(5)}, missing=${missing.take(5)}")
          }
        }
        known.addAll(fields)

        // 4. Field cardinality tracking
        val typeCardinality = fieldCardinality.getOrPut(eventType) { ConcurrentHashMap() }
        for ((key, value) in element) {
          if (value is JsonPrimitive && value.isString) {
            val fieldKey = "$eventType.$key"
            val counter = typeCardinality.getOrPut(fieldKey) { AtomicLong(0) }
            val count = counter.incrementAndGet()
            // Very high cardinality in a single field can indicate enumeration
            if (count > CARDINALITY_THRESHOLD && key.lowercase().let {
                it.contains("id") || it.contains("token") || it.contains("key") || it.contains("code")
              }) {
              val cardScore = 0.1f
              totalScore += cardScore
              reasons.add("high_cardinality: field=$key, unique_values=$count")
            }
          }
        }
      }
    } catch (_: Exception) {
      // Unparseable JSON is itself anomalous
      totalScore += 0.2f
      reasons.add("unparseable_json")
    }

    totalScore = totalScore.coerceIn(0.0f, 1.0f)

    val action = when {
      totalScore >= policy.rejectThreshold -> AnomalyAction.REJECT
      totalScore >= policy.flagThreshold -> AnomalyAction.FLAG
      else -> AnomalyAction.ALLOW
    }

    return AnomalyResult(score = totalScore, reasons = reasons, recommendedAction = action)
  }

  private fun sizeZScoreThreshold(): Double = when (policy.sensitivityLevel) {
    SensitivityLevel.LOW -> 4.0
    SensitivityLevel.MEDIUM -> 3.0
    SensitivityLevel.HIGH -> 2.0
  }

  private fun rateMultiplierThreshold(): Float = when (policy.sensitivityLevel) {
    SensitivityLevel.LOW -> 5.0f
    SensitivityLevel.MEDIUM -> 3.0f
    SensitivityLevel.HIGH -> 2.0f
  }

  companion object {
    private const val BASELINE_COUNT = 10
    private const val CARDINALITY_THRESHOLD = 1000L
  }
}

/**
 * Welford's online algorithm for running mean and variance.
 * O(1) memory, thread-safe via synchronization.
 */
internal class RollingStats {
  var count = 0L
    private set
  var mean = 0.0
    private set
  private var m2 = 0.0

  @Synchronized
  fun update(value: Double) {
    count++
    val delta = value - mean
    mean += delta / count
    val delta2 = value - mean
    m2 += delta * delta2
  }

  @Synchronized
  fun variance(): Double = if (count < 2) 0.0 else m2 / (count - 1)

  fun stddev(): Double = Math.sqrt(variance())

  fun zScore(value: Double): Double {
    val sd = stddev()
    return if (sd < 0.001) 0.0 else Math.abs(value - mean) / sd
  }
}

/**
 * Sliding window event counter for rate detection.
 * Thread-safe via atomic operations.
 */
internal class SlidingWindowCounter(private val windowMs: Long) {
  private val windowStart = AtomicLong(System.currentTimeMillis())
  private val currentCount = AtomicLong(0)
  private val previousCount = AtomicLong(0)

  var baselineRate: Long = 0L
    private set

  fun incrementAndGet(): Long {
    val now = System.currentTimeMillis()
    val start = windowStart.get()
    if (now - start > windowMs) {
      val prev = currentCount.getAndSet(0)
      previousCount.set(prev)
      windowStart.set(now)
      if (baselineRate == 0L) {
        baselineRate = prev
      } else {
        // Exponential moving average
        baselineRate = (baselineRate * 3 + prev) / 4
      }
    }
    return currentCount.incrementAndGet()
  }
}
