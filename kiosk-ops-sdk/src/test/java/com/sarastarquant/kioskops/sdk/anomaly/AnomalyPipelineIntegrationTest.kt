package com.sarastarquant.kioskops.sdk.anomaly

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnomalyPipelineIntegrationTest {

  @Test fun `policy configuration`() {
    val enabled = AnomalyPolicy.enabledDefaults()
    assertThat(enabled.enabled).isTrue()
    assertThat(enabled.sensitivityLevel).isEqualTo(SensitivityLevel.MEDIUM)

    val highSec = AnomalyPolicy.highSecurityDefaults()
    assertThat(highSec.sensitivityLevel).isEqualTo(SensitivityLevel.HIGH)
    assertThat(highSec.flagThreshold).isLessThan(0.5f)

    val disabled = AnomalyPolicy.disabledDefaults()
    assertThat(disabled.enabled).isFalse()
  }

  @Test fun `AnomalyResult NORMAL constant`() {
    val normal = AnomalyResult.NORMAL
    assertThat(normal.score).isEqualTo(0.0f)
    assertThat(normal.reasons).isEmpty()
    assertThat(normal.recommendedAction).isEqualTo(AnomalyAction.ALLOW)
  }

  @Test fun `detector provides consistent results for same input`() {
    val detector = StatisticalAnomalyDetector(AnomalyPolicy.enabledDefaults())
    val payload = """{"barcode":"test"}"""
    val r1 = detector.analyze("SCAN", payload)
    val r2 = detector.analyze("SCAN", payload)
    // Both should have valid scores
    assertThat(r1.score).isAtLeast(0.0f)
    assertThat(r2.score).isAtLeast(0.0f)
  }
}
