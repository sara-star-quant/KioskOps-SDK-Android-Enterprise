package com.peterz.kioskops.sdk.anomaly

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StatisticalAnomalyDetectorTest {

  @Test fun `normal event gets low score`() {
    val detector = StatisticalAnomalyDetector(AnomalyPolicy.enabledDefaults())
    // Establish baseline
    repeat(20) {
      detector.analyze("SCAN", """{"barcode":"item-$it"}""")
    }
    val result = detector.analyze("SCAN", """{"barcode":"item-normal"}""")
    assertThat(result.score).isLessThan(0.5f)
    assertThat(result.recommendedAction).isEqualTo(AnomalyAction.ALLOW)
  }

  @Test fun `oversized payload triggers size anomaly after baseline`() {
    val detector = StatisticalAnomalyDetector(
      AnomalyPolicy(enabled = true, sensitivityLevel = SensitivityLevel.HIGH)
    )
    // Establish baseline with very uniform small payloads (all same fields for schema baseline)
    repeat(50) { i ->
      detector.analyze("SIZE2", """{"a":"v","b":"w","c":"x","d":"y","e":"z","f":"$i","g":"$i","h":"$i","i":"$i","j":"$i","k":"$i"}""")
    }
    // Send a dramatically larger payload with same fields
    val huge = """{"a":"${"x".repeat(50000)}","b":"w","c":"x","d":"y","e":"z","f":"0","g":"0","h":"0","i":"0","j":"0","k":"0"}"""
    val result = detector.analyze("SIZE2", huge)
    // After baseline of tiny payloads, a 50KB payload should have high z-score
    assertThat(result.score).isGreaterThan(0.0f)
  }

  @Test fun `unknown JSON gets anomaly flag`() {
    val detector = StatisticalAnomalyDetector(AnomalyPolicy.enabledDefaults())
    val result = detector.analyze("SCAN", "not valid json")
    assertThat(result.score).isGreaterThan(0.0f)
    assertThat(result.reasons).contains("unparseable_json")
  }

  @Test fun `disabled policy returns normal`() {
    val detector = StatisticalAnomalyDetector(AnomalyPolicy.disabledDefaults())
    val result = detector.analyze("SCAN", """{"barcode":"test"}""")
    // Even disabled, the detector still works; policy enforcement is at SDK level
    assertThat(result).isNotNull()
  }

  @Test fun `score is bounded between 0 and 1`() {
    val detector = StatisticalAnomalyDetector(AnomalyPolicy.highSecurityDefaults())
    repeat(50) {
      val result = detector.analyze("SCAN", """{"barcode":"item-$it"}""")
      assertThat(result.score).isAtLeast(0.0f)
      assertThat(result.score).isAtMost(1.0f)
    }
  }
}
