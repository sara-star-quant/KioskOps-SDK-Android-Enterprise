package com.sarastarquant.kioskops.sdk.pii

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PiiRedactorTest {
  private val redactor = PiiRedactor()

  @Test fun `redacts detected PII`() {
    val findings = listOf(
      PiiFinding("$.email", PiiType.EMAIL, 0.95f),
    )
    val result = redactor.redact("""{"email":"user@example.com","safe":"ok"}""", findings)
    assertThat(result.redactedJson).contains("[REDACTED:EMAIL]")
    assertThat(result.redactedJson).contains("safe")
    assertThat(result.redactedPaths).containsExactly("$.email")
  }

  @Test fun `empty findings returns original`() {
    val original = """{"test":"value"}"""
    val result = redactor.redact(original, emptyList())
    assertThat(result.redactedJson).isEqualTo(original)
    assertThat(result.redactedPaths).isEmpty()
  }

  @Test fun `redacts multiple fields`() {
    val findings = listOf(
      PiiFinding("$.email", PiiType.EMAIL, 0.95f),
      PiiFinding("$.phone", PiiType.PHONE, 0.85f),
    )
    val result = redactor.redact(
      """{"email":"user@test.com","phone":"555-1234","keep":"ok"}""",
      findings,
    )
    assertThat(result.redactedJson).contains("[REDACTED:EMAIL]")
    assertThat(result.redactedJson).contains("[REDACTED:PHONE]")
    assertThat(result.redactedJson).contains("keep")
    assertThat(result.redactedPaths).hasSize(2)
  }

  @Test fun `uses highest confidence type for redaction marker`() {
    val findings = listOf(
      PiiFinding("$.data", PiiType.SSN, 0.90f),
      PiiFinding("$.data", PiiType.PHONE, 0.80f),
    )
    val result = redactor.redact("""{"data":"123-45-6789"}""", findings)
    assertThat(result.redactedJson).contains("[REDACTED:SSN]")
  }
}
