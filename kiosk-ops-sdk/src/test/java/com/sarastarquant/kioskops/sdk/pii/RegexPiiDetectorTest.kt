package com.sarastarquant.kioskops.sdk.pii

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RegexPiiDetectorTest {
  private val detector = RegexPiiDetector()

  @Test fun `detects email`() {
    val result = detector.scan("""{"contact":"user@example.com"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.EMAIL }).isTrue()
  }

  @Test fun `detects phone number`() {
    val result = detector.scan("""{"phone":"+1-555-123-4567"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.PHONE }).isTrue()
  }

  @Test fun `detects SSN`() {
    val result = detector.scan("""{"ssn":"123-45-6789"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.SSN }).isTrue()
  }

  @Test fun `detects credit card Visa`() {
    val result = detector.scan("""{"card":"4111 1111 1111 1111"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.CREDIT_CARD }).isTrue()
  }

  @Test fun `detects IP address`() {
    val result = detector.scan("""{"ip":"192.168.1.100"}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings.any { it.piiType == PiiType.IP_ADDRESS }).isTrue()
  }

  @Test fun `no PII in clean payload`() {
    val result = detector.scan("""{"type":"SCAN","barcode":"ABC123","timestamp":1234567890}""")
    assertThat(result.hasPii).isFalse()
  }

  @Test fun `respects minimum confidence`() {
    val strictDetector = RegexPiiDetector(minimumConfidence = 0.99f)
    // Passport pattern has 0.70 confidence
    val result = strictDetector.scan("""{"doc":"AB1234567"}""")
    // Should be filtered out by high minimum confidence
    assertThat(result.findings.filter { it.piiType == PiiType.PASSPORT }).isEmpty()
  }

  @Test fun `respects field exclusions`() {
    val detector = RegexPiiDetector(fieldExclusions = setOf("$.contact"))
    val result = detector.scan("""{"contact":"user@example.com","other":"test@test.com"}""")
    // The excluded path should not have findings; the other path should
    val contactFindings = result.findings.filter { it.jsonPath == "$.contact" }
    assertThat(contactFindings).isEmpty()
    assertThat(result.findings.any { it.jsonPath == "$.other" }).isTrue()
  }

  @Test fun `walks nested objects`() {
    val result = detector.scan("""{"user":{"email":"test@test.com"}}""")
    assertThat(result.hasPii).isTrue()
    assertThat(result.findings[0].jsonPath).contains("email")
  }

  @Test fun `walks arrays`() {
    val result = detector.scan("""{"emails":["a@b.com","c@d.com"]}""")
    assertThat(result.findings.size).isAtLeast(2)
  }

  @Test fun `handles malformed JSON gracefully`() {
    val result = detector.scan("not json")
    assertThat(result.hasPii).isFalse()
  }
}
