package com.sarastarquant.kioskops.sdk.fuzz

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.sarastarquant.kioskops.sdk.pii.RegexPiiDetector

class PiiDetectorFuzzTest {

  @FuzzTest(maxDuration = "30s")
  fun `RegexPiiDetector never throws`(data: FuzzedDataProvider) {
    val detector = RegexPiiDetector()
    val input = data.consumeRemainingAsString()
    // Must never throw, regardless of input
    val result = detector.scan(input)
    assert(result.findings.all { it.confidence in 0.0f..1.0f })
  }
}
