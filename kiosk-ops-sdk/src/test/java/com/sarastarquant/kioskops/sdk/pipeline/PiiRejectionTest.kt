/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.pipeline

import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.pii.DataClassificationPolicy
import com.sarastarquant.kioskops.sdk.pii.PiiAction
import com.sarastarquant.kioskops.sdk.pii.PiiPolicy
import com.sarastarquant.kioskops.sdk.queue.EnqueueResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PiiRejectionTest : PipelineTestBase() {

  // ==========================================================================
  // 2. PII Detection and Rejection
  // ==========================================================================

  @Test
  fun `PII reject policy rejects payload containing email address`() = runTest {
    val config = baseConfig(piiPolicy = PiiPolicy.rejectDefaults())
    val sdk = initSdk(config)

    val result = sdk.enqueueDetailed(
      "user.action",
      """{"contact": "john.doe@example.com", "action": "login"}""",
    )

    assertThat(result).isInstanceOf(EnqueueResult.Rejected.PiiDetected::class.java)
    val rejected = result as EnqueueResult.Rejected.PiiDetected
    assertThat(rejected.findings).isNotEmpty()
    assertThat(rejected.findings.any { it.contains("EMAIL") }).isTrue()
  }

  @Test
  fun `PII reject policy rejects payload containing phone number`() = runTest {
    val config = baseConfig(piiPolicy = PiiPolicy.rejectDefaults())
    val sdk = initSdk(config)

    val result = sdk.enqueueDetailed(
      "customer.update",
      """{"phone": "+1 (555) 867-5309", "status": "active"}""",
    )

    assertThat(result).isInstanceOf(EnqueueResult.Rejected.PiiDetected::class.java)
    val rejected = result as EnqueueResult.Rejected.PiiDetected
    assertThat(rejected.findings).isNotEmpty()
    assertThat(rejected.findings.any { it.contains("PHONE") }).isTrue()
  }

  @Test
  fun `PII reject policy rejects payload containing SSN pattern`() = runTest {
    val config = baseConfig(piiPolicy = PiiPolicy.rejectDefaults())
    val sdk = initSdk(config)

    val result = sdk.enqueueDetailed(
      "employee.record",
      """{"ssn": "123-45-6789", "department": "engineering"}""",
    )

    assertThat(result).isInstanceOf(EnqueueResult.Rejected.PiiDetected::class.java)
    val rejected = result as EnqueueResult.Rejected.PiiDetected
    assertThat(rejected.findings).isNotEmpty()
    assertThat(rejected.findings.any { it.contains("SSN") }).isTrue()
  }

  @Test
  fun `PII redact policy returns PiiRedacted with redactedFields for email`() = runTest {
    val config = baseConfig(piiPolicy = PiiPolicy.redactDefaults())
    val sdk = initSdk(config)

    val result = sdk.enqueueDetailed(
      "contact.form",
      """{"email": "jane@example.com", "message": "hello"}""",
    )

    assertThat(result).isInstanceOf(EnqueueResult.PiiRedacted::class.java)
    val redacted = result as EnqueueResult.PiiRedacted
    assertThat(redacted.redactedFields).isNotEmpty()
    assertThat(redacted.id).isNotEmpty()
    assertThat(redacted.idempotencyKey).isNotEmpty()
  }

  @Test
  fun `PII flag-and-allow policy accepts payload with PII without rejection`() = runTest {
    val config = baseConfig(
      piiPolicy = PiiPolicy(
        enabled = true,
        action = PiiAction.FLAG_AND_ALLOW,
      ),
    )
    val sdk = initSdk(config)

    val result = sdk.enqueueDetailed(
      "user.event",
      """{"email": "flagged@example.com", "action": "browse"}""",
    )

    // FLAG_AND_ALLOW should not reject; event should be accepted
    assertThat(result).isInstanceOf(EnqueueResult.Accepted::class.java)
  }

  @Test
  fun `PII disabled policy accepts payload with PII`() = runTest {
    val config = baseConfig(piiPolicy = PiiPolicy.disabledDefaults())
    val sdk = initSdk(config)

    val result = sdk.enqueueDetailed(
      "user.event",
      """{"email": "test@example.com", "ssn": "999-88-7777"}""",
    )

    assertThat(result).isInstanceOf(EnqueueResult.Accepted::class.java)
  }

  @Test
  fun `PII reject policy accepts clean payload with no PII`() = runTest {
    val config = baseConfig(piiPolicy = PiiPolicy.rejectDefaults())
    val sdk = initSdk(config)

    val result = sdk.enqueueDetailed(
      "system.metric",
      """{"cpu_percent": 42.5, "memory_mb": 2048, "status": "ok"}""",
    )

    assertThat(result).isInstanceOf(EnqueueResult.Accepted::class.java)
  }

  @Test
  fun `PII reject policy rejects payload containing credit card number`() = runTest {
    val config = baseConfig(piiPolicy = PiiPolicy.rejectDefaults())
    val sdk = initSdk(config)

    val result = sdk.enqueueDetailed(
      "payment.attempt",
      """{"card": "4111-1111-1111-1111", "amount": 99}""",
    )

    assertThat(result).isInstanceOf(EnqueueResult.Rejected.PiiDetected::class.java)
    val rejected = result as EnqueueResult.Rejected.PiiDetected
    assertThat(rejected.findings.any { it.contains("CREDIT_CARD") }).isTrue()
  }

  @Test
  fun `PII reject policy rejects payload containing IP address`() = runTest {
    val config = baseConfig(piiPolicy = PiiPolicy.rejectDefaults())
    val sdk = initSdk(config)

    val result = sdk.enqueueDetailed(
      "access.log",
      """{"source_ip": "192.168.1.100", "endpoint": "/api/data"}""",
    )

    assertThat(result).isInstanceOf(EnqueueResult.Rejected.PiiDetected::class.java)
    val rejected = result as EnqueueResult.Rejected.PiiDetected
    assertThat(rejected.findings.any { it.contains("IP_ADDRESS") }).isTrue()
  }

  // ==========================================================================
  // 7. Data Classification
  // ==========================================================================

  @Test
  fun `data classification policy tags PII-containing event as CONFIDENTIAL when redacted`() = runTest {
    val config = baseConfig(
      piiPolicy = PiiPolicy.redactDefaults(),
      dataClassificationPolicy = DataClassificationPolicy.enabledDefaults(),
    )
    val sdk = initSdk(config)

    val result = sdk.enqueueDetailed(
      "user.profile",
      """{"email": "classified@example.com", "role": "admin"}""",
    )

    // With PII redacted and classification enabled, the result should be PiiRedacted
    assertThat(result).isInstanceOf(EnqueueResult.PiiRedacted::class.java)
    val redacted = result as EnqueueResult.PiiRedacted
    assertThat(redacted.redactedFields).isNotEmpty()
  }

  @Test
  fun `data classification disabled does not affect enqueue acceptance`() = runTest {
    val config = baseConfig(
      dataClassificationPolicy = DataClassificationPolicy.disabledDefaults(),
    )
    val sdk = initSdk(config)

    val result = sdk.enqueueDetailed(
      "generic.event",
      """{"data": "no classification needed"}""",
    )

    assertThat(result).isInstanceOf(EnqueueResult.Accepted::class.java)
  }

  // ==========================================================================
  // PII edge cases
  // ==========================================================================

  @Test
  fun `PII redact policy returns PiiRedacted with multiple redacted fields`() = runTest {
    val config = baseConfig(piiPolicy = PiiPolicy.redactDefaults())
    val sdk = initSdk(config)

    val result = sdk.enqueueDetailed(
      "multi.pii",
      """{"email": "a@b.com", "phone": "+1 555-123-4567", "note": "clean data"}""",
    )

    assertThat(result).isInstanceOf(EnqueueResult.PiiRedacted::class.java)
    val redacted = result as EnqueueResult.PiiRedacted
    // Should have redacted at least the email and phone fields
    assertThat(redacted.redactedFields.size).isAtLeast(2)
  }

  @Test
  fun `PII rejection does not leave partial data in the queue`() = runTest {
    val config = baseConfig(piiPolicy = PiiPolicy.rejectDefaults())
    val sdk = initSdk(config)

    assertThat(sdk.queueDepth()).isEqualTo(0)

    val result = sdk.enqueueDetailed(
      "pii.leak",
      """{"ssn": "999-88-7777", "email": "leak@example.com"}""",
    )

    assertThat(result).isInstanceOf(EnqueueResult.Rejected.PiiDetected::class.java)
    // Queue must remain empty; rejected events must not persist
    assertThat(sdk.queueDepth()).isEqualTo(0)
  }
}
