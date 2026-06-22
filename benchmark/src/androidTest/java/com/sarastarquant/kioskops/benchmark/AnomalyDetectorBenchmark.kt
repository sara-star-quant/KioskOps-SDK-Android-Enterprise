/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sarastarquant.kioskops.sdk.anomaly.AnomalyPolicy
import com.sarastarquant.kioskops.sdk.anomaly.StatisticalAnomalyDetector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Per-event scoring throughput of [StatisticalAnomalyDetector] once past its
 * learning period (the path taken for every enqueued event in production).
 */
@RunWith(AndroidJUnit4::class)
class AnomalyDetectorBenchmark {

  @get:Rule
  val benchmarkRule = BenchmarkRule()

  private val detector = StatisticalAnomalyDetector(AnomalyPolicy.enabledDefaults())
  private val eventType = "kiosk_heartbeat"
  private val payload =
    """{"battery":87,"uptimeMs":1234567,"appVersion":"1.3.1","screen":"locked","temp":31.5}"""

  init {
    // Warm past baselineEventCount so analyze() actually scores instead of returning ALLOW.
    repeat(200) { detector.analyze(eventType, payload) }
  }

  @Test
  fun analyze() {
    benchmarkRule.measureRepeated {
      detector.analyze(eventType, payload)
    }
  }
}
