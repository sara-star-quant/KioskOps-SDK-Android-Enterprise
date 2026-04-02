package com.sarastarquant.kioskops.sdk.fuzz

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.sarastarquant.kioskops.sdk.anomaly.AnomalyPolicy
import com.sarastarquant.kioskops.sdk.anomaly.StatisticalAnomalyDetector

class AnomalyDetectorFuzzTest {

  @FuzzTest(maxDuration = "30s")
  fun `StatisticalAnomalyDetector never throws`(data: FuzzedDataProvider) {
    val detector = StatisticalAnomalyDetector(AnomalyPolicy.enabledDefaults())
    val eventType = data.consumeString(50)
    val payload = data.consumeRemainingAsString()
    // Must never throw, regardless of input
    val result = detector.analyze(eventType, payload)
    assert(result.score in 0.0f..1.0f)
  }
}
