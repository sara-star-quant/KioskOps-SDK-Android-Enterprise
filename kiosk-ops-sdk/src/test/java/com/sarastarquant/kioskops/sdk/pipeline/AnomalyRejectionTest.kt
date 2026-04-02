/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.pipeline

import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.anomaly.AnomalyAction
import com.sarastarquant.kioskops.sdk.anomaly.AnomalyDetector
import com.sarastarquant.kioskops.sdk.anomaly.AnomalyPolicy
import com.sarastarquant.kioskops.sdk.anomaly.AnomalyResult
import com.sarastarquant.kioskops.sdk.anomaly.SensitivityLevel
import com.sarastarquant.kioskops.sdk.queue.EnqueueResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnomalyRejectionTest : PipelineTestBase() {

  // ==========================================================================
  // 3. Anomaly Detection Rejection
  // ==========================================================================

  @Test
  fun `anomaly detection rejects event when custom detector returns REJECT action`() = runTest {
    val config = baseConfig(
      anomalyPolicy = AnomalyPolicy(
        enabled = true,
        sensitivityLevel = SensitivityLevel.HIGH,
        flagThreshold = 0.1f,
        rejectThreshold = 0.2f,
      ),
    )
    val sdk = initSdk(config)

    // Inject a custom anomaly detector that always rejects
    sdk.setAnomalyDetector(object : AnomalyDetector {
      override fun analyze(eventType: String, payloadJson: String): AnomalyResult {
        return AnomalyResult(
          score = 0.95f,
          reasons = listOf("test_anomaly: injected high score"),
          recommendedAction = AnomalyAction.REJECT,
        )
      }
    })

    val result = sdk.enqueueDetailed("anomalous.event", """{"data": "suspicious"}""")

    assertThat(result).isInstanceOf(EnqueueResult.Rejected.AnomalyRejected::class.java)
    val rejected = result as EnqueueResult.Rejected.AnomalyRejected
    assertThat(rejected.score).isGreaterThan(0.0f)
    assertThat(rejected.reasons).isNotEmpty()
  }

  @Test
  fun `anomaly detection disabled accepts event regardless of payload content`() = runTest {
    val config = baseConfig(anomalyPolicy = AnomalyPolicy.disabledDefaults())
    val sdk = initSdk(config)

    val result = sdk.enqueueDetailed(
      "any.event",
      """{"unusual_field_1": "x", "unusual_field_2": "y", "unusual_field_3": "z"}""",
    )

    assertThat(result).isInstanceOf(EnqueueResult.Accepted::class.java)
  }

  @Test
  fun `anomaly detection allows event when custom detector returns ALLOW action`() = runTest {
    val config = baseConfig(
      anomalyPolicy = AnomalyPolicy(
        enabled = true,
        sensitivityLevel = SensitivityLevel.HIGH,
        flagThreshold = 0.1f,
        rejectThreshold = 0.2f,
      ),
    )
    val sdk = initSdk(config)

    sdk.setAnomalyDetector(object : AnomalyDetector {
      override fun analyze(eventType: String, payloadJson: String): AnomalyResult {
        return AnomalyResult.NORMAL
      }
    })

    val result = sdk.enqueueDetailed("normal.event", """{"data": "clean"}""")

    assertThat(result).isInstanceOf(EnqueueResult.Accepted::class.java)
  }

  // ==========================================================================
  // Anomaly edge cases
  // ==========================================================================

  @Test
  fun `anomaly detection with FLAG action does not reject the event`() = runTest {
    val config = baseConfig(
      anomalyPolicy = AnomalyPolicy(
        enabled = true,
        sensitivityLevel = SensitivityLevel.MEDIUM,
        flagThreshold = 0.1f,
        rejectThreshold = 0.9f,
      ),
    )
    val sdk = initSdk(config)

    sdk.setAnomalyDetector(object : AnomalyDetector {
      override fun analyze(eventType: String, payloadJson: String): AnomalyResult {
        return AnomalyResult(
          score = 0.5f,
          reasons = listOf("flagged_but_not_rejected"),
          recommendedAction = AnomalyAction.FLAG,
        )
      }
    })

    val result = sdk.enqueueDetailed("flagged.event", """{"data": "flagged"}""")

    // FLAG action should still accept the event
    assertThat(result).isInstanceOf(EnqueueResult.Accepted::class.java)
  }
}
