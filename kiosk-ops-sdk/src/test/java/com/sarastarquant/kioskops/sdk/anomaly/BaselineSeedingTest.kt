/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.anomaly

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BaselineSeedingTest {

  @Test fun `events during learning period always return ALLOW`() {
    val policy = AnomalyPolicy(enabled = true, baselineEventCount = 10)
    val detector = StatisticalAnomalyDetector(policy)

    // Send events that would normally be flagged (unparseable JSON)
    repeat(9) {
      val result = detector.analyze("SCAN", "not valid json at all")
      assertThat(result.recommendedAction).isEqualTo(AnomalyAction.ALLOW)
      assertThat(result.score).isEqualTo(0.0f)
    }
  }

  @Test fun `after baseline count reached scoring activates`() {
    val policy = AnomalyPolicy(enabled = true, baselineEventCount = 5)
    val detector = StatisticalAnomalyDetector(policy)

    // First 4 events are in learning period (events 1-4, all < 5)
    repeat(4) {
      val result = detector.analyze("SCAN", """{"barcode":"item-$it"}""")
      assertThat(result.recommendedAction).isEqualTo(AnomalyAction.ALLOW)
      assertThat(result.score).isEqualTo(0.0f)
    }

    // Event 5 reaches the baseline threshold; scoring activates.
    // Unparseable JSON should now be scored.
    val result = detector.analyze("SCAN", "not valid json")
    assertThat(result.score).isGreaterThan(0.0f)
    assertThat(result.reasons).contains("unparseable_json")
  }

  @Test fun `seedBaseline immediately activates scoring`() {
    val policy = AnomalyPolicy(enabled = true, baselineEventCount = 1000)
    val detector = StatisticalAnomalyDetector(policy)

    detector.seedBaseline(
      BaselineStats(
        meanPayloadSize = 50.0,
        payloadSizeVariance = 25.0,
        meanEventRatePerMinute = 10.0,
      )
    )

    // Even though baselineEventCount is 1000, seedBaseline skips the learning period.
    // Unparseable JSON should be scored immediately.
    val result = detector.analyze("SCAN", "not valid json")
    assertThat(result.score).isGreaterThan(0.0f)
    assertThat(result.reasons).contains("unparseable_json")
  }

  @Test fun `seedBaseline with known stats influences subsequent scoring`() {
    val policy = AnomalyPolicy(
      enabled = true,
      sensitivityLevel = SensitivityLevel.HIGH,
      baselineEventCount = 100,
    )
    val detector = StatisticalAnomalyDetector(policy)

    // Seed with small payload stats (mean=30, low variance)
    detector.seedBaseline(
      BaselineStats(
        meanPayloadSize = 30.0,
        payloadSizeVariance = 4.0,
        meanEventRatePerMinute = 5.0,
        knownFieldsByEventType = mapOf("SCAN" to setOf("barcode", "timestamp")),
      )
    )

    // A normal-sized payload should score low
    val normalResult = detector.analyze("SCAN", """{"barcode":"abc","timestamp":"123"}""")
    assertThat(normalResult.score).isLessThan(0.5f)

    // A dramatically oversized payload should trigger size anomaly
    val hugePayload = """{"barcode":"${"x".repeat(50000)}","timestamp":"123"}"""
    val hugeResult = detector.analyze("SCAN", hugePayload)
    assertThat(hugeResult.score).isGreaterThan(0.0f)
  }

  @Test fun `baselineEventCount zero means no learning period`() {
    val policy = AnomalyPolicy(enabled = true, baselineEventCount = 0)
    val detector = StatisticalAnomalyDetector(policy)

    // Very first event should be scored, not suppressed
    val result = detector.analyze("SCAN", "not valid json")
    assertThat(result.score).isGreaterThan(0.0f)
    assertThat(result.reasons).contains("unparseable_json")
  }

  @Test fun `highSecurityDefaults has shorter baseline than enabledDefaults`() {
    val highSec = AnomalyPolicy.highSecurityDefaults()
    val enabled = AnomalyPolicy.enabledDefaults()

    assertThat(highSec.baselineEventCount).isLessThan(enabled.baselineEventCount)
    assertThat(highSec.baselineEventCount).isEqualTo(50)
    assertThat(enabled.baselineEventCount).isEqualTo(100)
  }

  @Test fun `multiple event types each maintain their own baseline`() {
    val policy = AnomalyPolicy(enabled = true, baselineEventCount = 5)
    val detector = StatisticalAnomalyDetector(policy)

    // Send 5 SCAN events to complete learning period for all types
    // (totalEventsAnalyzed is global, not per-type)
    repeat(4) {
      detector.analyze("SCAN", """{"barcode":"item-$it"}""")
    }

    // 5th event reaches the threshold; scoring activates globally
    val scanResult = detector.analyze("SCAN", """{"barcode":"item-normal"}""")
    // After baseline, normal payloads should still be allowed
    assertThat(scanResult.recommendedAction).isEqualTo(AnomalyAction.ALLOW)

    // A new event type should also be scored (baseline is complete globally)
    val loginResult = detector.analyze("LOGIN", "not valid json")
    assertThat(loginResult.score).isGreaterThan(0.0f)
  }

  @Test fun `default baseline count is 100`() {
    val policy = AnomalyPolicy()
    assertThat(policy.baselineEventCount).isEqualTo(100)

    val enabledPolicy = AnomalyPolicy.enabledDefaults()
    assertThat(enabledPolicy.baselineEventCount).isEqualTo(100)
  }
}
