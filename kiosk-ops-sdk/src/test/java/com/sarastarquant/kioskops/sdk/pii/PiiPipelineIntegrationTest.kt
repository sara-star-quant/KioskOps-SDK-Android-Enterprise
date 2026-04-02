package com.sarastarquant.kioskops.sdk.pii

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PiiPipelineIntegrationTest {

  @Test fun `detect and redact PII end-to-end`() {
    val detector = RegexPiiDetector()
    val redactor = PiiRedactor()

    val payload = """{"name":"John","email":"john@example.com","barcode":"ABC123"}"""
    val scanResult = detector.scan(payload)
    assertThat(scanResult.hasPii).isTrue()

    val redactionResult = redactor.redact(payload, scanResult.findings)
    assertThat(redactionResult.redactedJson).contains("[REDACTED:EMAIL]")
    assertThat(redactionResult.redactedJson).contains("barcode")
    assertThat(redactionResult.redactedJson).contains("ABC123")
    assertThat(redactionResult.redactedPaths).isNotEmpty()
  }

  @Test fun `clean payload passes through unchanged`() {
    val detector = RegexPiiDetector()
    val redactor = PiiRedactor()

    val payload = """{"type":"SCAN","barcode":"ITEM-001","quantity":5}"""
    val scanResult = detector.scan(payload)
    assertThat(scanResult.hasPii).isFalse()

    val redactionResult = redactor.redact(payload, scanResult.findings)
    assertThat(redactionResult.redactedJson).isEqualTo(payload)
    assertThat(redactionResult.redactedPaths).isEmpty()
  }

  @Test fun `PiiPolicy configuration`() {
    val reject = PiiPolicy.rejectDefaults()
    assertThat(reject.enabled).isTrue()
    assertThat(reject.action).isEqualTo(PiiAction.REJECT)

    val redact = PiiPolicy.redactDefaults()
    assertThat(redact.enabled).isTrue()
    assertThat(redact.action).isEqualTo(PiiAction.REDACT_VALUE)

    val disabled = PiiPolicy.disabledDefaults()
    assertThat(disabled.enabled).isFalse()
  }

  @Test fun `DataClassificationPolicy configuration`() {
    val enabled = DataClassificationPolicy.enabledDefaults()
    assertThat(enabled.enabled).isTrue()
    assertThat(enabled.defaultClassification).isEqualTo(DataClassification.INTERNAL)
    assertThat(enabled.piiClassification).isEqualTo(DataClassification.CONFIDENTIAL)
  }
}
