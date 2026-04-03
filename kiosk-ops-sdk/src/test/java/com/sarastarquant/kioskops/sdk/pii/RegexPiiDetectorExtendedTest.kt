/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.pii

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Extended tests for [RegexPiiDetector] covering country-specific patterns,
 * safe-pattern exclusions, and confidence threshold filtering.
 *
 * @since 0.7.0
 */
@RunWith(RobolectricTestRunner::class)
class RegexPiiDetectorExtendedTest {

  private val detector = RegexPiiDetector()

  // -----------------------------------------------------------------------
  // Country-specific patterns: Australian TFN
  // -----------------------------------------------------------------------

  @Test fun `detects Australian TFN without spaces`() {
    val result = detector.scan("""{"tfn":"123456789"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.NATIONAL_ID }).isTrue()
  }

  @Test fun `detects Australian TFN with spaces`() {
    val result = detector.scan("""{"tfn":"123 456 789"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.NATIONAL_ID }).isTrue()
  }

  // -----------------------------------------------------------------------
  // Country-specific patterns: UK National Insurance Number
  // -----------------------------------------------------------------------

  @Test fun `detects UK NIN without spaces`() {
    val result = detector.scan("""{"nin":"AB123456C"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.NATIONAL_ID }).isTrue()
  }

  @Test fun `detects UK NIN with spaces`() {
    val result = detector.scan("""{"nin":"AB 12 34 56 C"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.NATIONAL_ID }).isTrue()
  }

  @Test fun `rejects invalid UK NIN prefix letters`() {
    // D and F are not valid first-position letters per the NIN spec
    val result = detector.scan("""{"nin":"DA123456C"}""")
    val ninFindings = result.findings.filter {
      it.piiType == PiiType.NATIONAL_ID && it.confidence >= 0.85f
    }
    assertThat(ninFindings).isEmpty()
  }

  @Test fun `rejects invalid UK NIN suffix letter`() {
    // Only A-D are valid suffix letters
    val result = detector.scan("""{"nin":"AB123456E"}""")
    val ninFindings = result.findings.filter {
      it.piiType == PiiType.NATIONAL_ID && it.confidence >= 0.85f
    }
    assertThat(ninFindings).isEmpty()
  }

  // -----------------------------------------------------------------------
  // Country-specific patterns: Canadian SIN
  // -----------------------------------------------------------------------

  @Test fun `detects Canadian SIN without spaces`() {
    val result = detector.scan("""{"sin":"987654321"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.NATIONAL_ID }).isTrue()
  }

  @Test fun `detects Canadian SIN with spaces`() {
    val result = detector.scan("""{"sin":"987 654 321"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.NATIONAL_ID }).isTrue()
  }

  // -----------------------------------------------------------------------
  // Country-specific patterns: German Steuer-ID
  // -----------------------------------------------------------------------

  @Test fun `detects German Steuer-ID without spaces`() {
    val result = detector.scan("""{"steuerId":"12345678901"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.NATIONAL_ID }).isTrue()
  }

  @Test fun `detects German Steuer-ID with spaces`() {
    val result = detector.scan("""{"steuerId":"12 345 678 901"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.NATIONAL_ID }).isTrue()
  }

  // -----------------------------------------------------------------------
  // Safe pattern exclusions: UUIDs
  // -----------------------------------------------------------------------

  @Test fun `UUID is not flagged as PII`() {
    val result = detector.scan("""{"id":"550e8400-e29b-41d4-a716-446655440000"}""")
    assertThat(result.hasPii).isFalse()
  }

  @Test fun `UUID v4 is not flagged as PII`() {
    val result = detector.scan("""{"requestId":"6ba7b810-9dad-11d1-80b4-00c04fd430c8"}""")
    assertThat(result.hasPii).isFalse()
  }

  @Test fun `uppercase UUID is not flagged as PII`() {
    val result = detector.scan("""{"traceId":"550E8400-E29B-41D4-A716-446655440000"}""")
    assertThat(result.hasPii).isFalse()
  }

  // -----------------------------------------------------------------------
  // Safe pattern exclusions: ISO timestamps
  // -----------------------------------------------------------------------

  @Test fun `ISO timestamp is not flagged as PII`() {
    val result = detector.scan("""{"ts":"2026-04-02T10:30:00Z"}""")
    assertThat(result.hasPii).isFalse()
  }

  @Test fun `ISO timestamp with offset is not flagged as PII`() {
    val result = detector.scan("""{"created":"2026-01-15T08:45:30+05:30"}""")
    assertThat(result.hasPii).isFalse()
  }

  @Test fun `ISO timestamp with millis is not flagged as PII`() {
    val result = detector.scan("""{"eventTime":"2025-12-31T23:59:59.999Z"}""")
    assertThat(result.hasPii).isFalse()
  }

  // -----------------------------------------------------------------------
  // Safe pattern exclusions: Semantic versions
  // -----------------------------------------------------------------------

  @Test fun `semantic version is not flagged as PII`() {
    val result = detector.scan("""{"sdkVersion":"0.7.0"}""")
    assertThat(result.hasPii).isFalse()
  }

  @Test fun `version with prerelease tag is not flagged as PII`() {
    val result = detector.scan("""{"version":"1.2.3-beta.1"}""")
    assertThat(result.hasPii).isFalse()
  }

  @Test fun `long version string is not flagged as PII`() {
    val result = detector.scan("""{"appVersion":"12.0.1"}""")
    assertThat(result.hasPii).isFalse()
  }

  // -----------------------------------------------------------------------
  // Confidence threshold filtering
  // -----------------------------------------------------------------------

  @Test fun `high confidence threshold excludes low-confidence patterns`() {
    val strictDetector = RegexPiiDetector(minimumConfidence = 0.90f)
    // Passport has 0.70 confidence; should be excluded
    val result = strictDetector.scan("""{"doc":"AB1234567"}""")
    assertThat(result.findings.filter { it.piiType == PiiType.PASSPORT }).isEmpty()
  }

  @Test fun `high confidence threshold excludes country-specific national IDs`() {
    // Australian TFN and German Steuer-ID have 0.75 confidence
    val strictDetector = RegexPiiDetector(minimumConfidence = 0.80f)
    val result = strictDetector.scan("""{"tfn":"123 456 789"}""")
    val nationalIdFindings = result.findings.filter {
      it.piiType == PiiType.NATIONAL_ID && it.confidence < 0.80f
    }
    assertThat(nationalIdFindings).isEmpty()
  }

  @Test fun `high confidence threshold retains high-confidence patterns`() {
    val strictDetector = RegexPiiDetector(minimumConfidence = 0.90f)
    // Email has 0.95 confidence; should still be detected
    val result = strictDetector.scan("""{"email":"user@example.com"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.EMAIL }).isTrue()
  }

  @Test fun `UK NIN at 85pct confidence passes 80pct threshold`() {
    val detector80 = RegexPiiDetector(minimumConfidence = 0.80f)
    val result = detector80.scan("""{"nin":"AB123456C"}""")
    assertThat(result.findings.any { it.piiType == PiiType.NATIONAL_ID }).isTrue()
  }

  @Test fun `UK NIN at 85pct confidence fails 90pct threshold`() {
    val detector90 = RegexPiiDetector(minimumConfidence = 0.90f)
    val result = detector90.scan("""{"nin":"AB123456C"}""")
    val ninFindings = result.findings.filter {
      it.piiType == PiiType.NATIONAL_ID && it.confidence == 0.85f
    }
    assertThat(ninFindings).isEmpty()
  }

  // -----------------------------------------------------------------------
  // Existing patterns still work
  // -----------------------------------------------------------------------

  @Test fun `email detection still works`() {
    val result = detector.scan("""{"contact":"admin@corp.io"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.EMAIL }).isTrue()
  }

  @Test fun `SSN detection still works`() {
    val result = detector.scan("""{"ssn":"123-45-6789"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.SSN }).isTrue()
  }

  @Test fun `Visa credit card detection still works`() {
    val result = detector.scan("""{"card":"4111111111111111"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.CREDIT_CARD }).isTrue()
  }

  @Test fun `Mastercard detection still works`() {
    val result = detector.scan("""{"card":"5500 0000 0000 0004"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.CREDIT_CARD }).isTrue()
  }

  @Test fun `Amex detection still works`() {
    val result = detector.scan("""{"card":"3782 822463 10005"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.CREDIT_CARD }).isTrue()
  }

  @Test fun `IP address detection still works`() {
    val result = detector.scan("""{"ip":"10.0.0.1"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.IP_ADDRESS }).isTrue()
  }

  @Test fun `phone detection still works`() {
    val result = detector.scan("""{"phone":"+1-800-555-1234"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.PHONE }).isTrue()
  }

  @Test fun `clean payload with no PII returns no findings`() {
    val result = detector.scan("""{"type":"SCAN","barcode":"ITEM-001","qty":5}""")
    assertThat(result.hasPii).isFalse()
    assertThat(result.findings).isEmpty()
  }

  // -----------------------------------------------------------------------
  // Edge cases: safe patterns do not prevent detection in non-matching values
  // -----------------------------------------------------------------------

  @Test fun `email in payload with UUID in another field is still detected`() {
    val result = detector.scan(
      """{"id":"550e8400-e29b-41d4-a716-446655440000","email":"user@example.com"}"""
    )
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.EMAIL }).isTrue()
    // UUID field should have no findings
    val idFindings = result.findings.filter { it.jsonPath == "$.id" }
    assertThat(idFindings).isEmpty()
  }

  @Test fun `timestamp field excluded but PII field still detected`() {
    val result = detector.scan(
      """{"ts":"2026-04-02T10:30:00Z","ssn":"123-45-6789"}"""
    )
    val tsFindings = result.findings.filter { it.jsonPath == "$.ts" }
    assertThat(tsFindings).isEmpty()
    assertThat(result.findings.any { it.piiType == PiiType.SSN }).isTrue()
  }

  // -----------------------------------------------------------------------
  // Country-specific patterns: Japan My Number
  // -----------------------------------------------------------------------

  @Test fun `detects Japan My Number 12 digits`() {
    val result = detector.scan("""{"myNumber":"123456789012"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.NATIONAL_ID }).isTrue()
  }

  @Test fun `Japan My Number not detected when embedded in longer string`() {
    // 13+ digit number should not match the 12-digit My Number pattern
    val result = detector.scan("""{"serial":"1234567890123"}""")
    val myNumberFindings = result.findings.filter {
      it.piiType == PiiType.NATIONAL_ID && it.confidence == 0.70f
    }
    assertThat(myNumberFindings).isEmpty()
  }

  @Test fun `Japan My Number excluded at 75pct confidence threshold`() {
    val strictDetector = RegexPiiDetector(minimumConfidence = 0.75f)
    val result = strictDetector.scan("""{"myNumber":"123456789012"}""")
    val myNumberFindings = result.findings.filter {
      it.piiType == PiiType.NATIONAL_ID && it.confidence == 0.70f
    }
    assertThat(myNumberFindings).isEmpty()
  }

  // -----------------------------------------------------------------------
  // Country-specific patterns: India Aadhaar
  // -----------------------------------------------------------------------

  @Test fun `detects India Aadhaar with spaces`() {
    val result = detector.scan("""{"aadhaar":"2345 6789 0123"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.NATIONAL_ID }).isTrue()
  }

  @Test fun `detects India Aadhaar without spaces`() {
    val result = detector.scan("""{"aadhaar":"234567890123"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.NATIONAL_ID }).isTrue()
  }

  @Test fun `India Aadhaar excluded at 80pct confidence threshold`() {
    val strictDetector = RegexPiiDetector(minimumConfidence = 0.80f)
    val result = strictDetector.scan("""{"aadhaar":"2345 6789 0123"}""")
    val aadhaarFindings = result.findings.filter {
      it.piiType == PiiType.NATIONAL_ID && it.confidence == 0.75f
    }
    assertThat(aadhaarFindings).isEmpty()
  }

  // -----------------------------------------------------------------------
  // Country-specific patterns: Brazil CPF
  // -----------------------------------------------------------------------

  @Test fun `detects Brazil CPF in formatted pattern`() {
    // Embed CPF in surrounding text so the safe version pattern (anchored
    // with ^...$) does not suppress the entire value.
    val result = detector.scan("""{"cpf":"CPF 123.456.789-09"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.NATIONAL_ID }).isTrue()
  }

  @Test fun `Brazil CPF excluded at 95pct confidence threshold`() {
    val strictDetector = RegexPiiDetector(minimumConfidence = 0.95f)
    val result = strictDetector.scan("""{"cpf":"CPF 123.456.789-09"}""")
    val cpfFindings = result.findings.filter {
      it.piiType == PiiType.NATIONAL_ID && it.confidence == 0.90f
    }
    assertThat(cpfFindings).isEmpty()
  }

  // -----------------------------------------------------------------------
  // Country-specific patterns: South Africa ID
  // -----------------------------------------------------------------------

  @Test fun `detects South Africa ID with valid date prefix`() {
    // 9501015800085 = 1995-01-01 followed by 7 digits
    val result = detector.scan("""{"saId":"9501015800085"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.NATIONAL_ID }).isTrue()
  }

  @Test fun `South Africa ID excluded at 85pct confidence threshold`() {
    val strictDetector = RegexPiiDetector(minimumConfidence = 0.85f)
    val result = strictDetector.scan("""{"saId":"9501015800085"}""")
    val saIdFindings = result.findings.filter {
      it.piiType == PiiType.NATIONAL_ID && it.confidence == 0.80f
    }
    assertThat(saIdFindings).isEmpty()
  }
}
