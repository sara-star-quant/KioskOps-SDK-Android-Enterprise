/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.pipeline

import com.google.common.truth.Truth.assertThat
import com.sarastarquant.kioskops.sdk.queue.EnqueueResult
import com.sarastarquant.kioskops.sdk.validation.UnknownEventTypeAction
import com.sarastarquant.kioskops.sdk.validation.ValidationPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ValidationRejectionTest : PipelineTestBase() {

  // ==========================================================================
  // 1. Validation Rejection (strict mode)
  // ==========================================================================

  @Test
  fun `strict validation rejects event that violates registered JSON schema`() = runTest {
    val config = baseConfig(validationPolicy = ValidationPolicy.strictDefaults())
    val sdk = initSdk(config)

    val schema = """
      {
        "type": "object",
        "required": ["amount", "currency"],
        "properties": {
          "amount": {"type": "number", "minimum": 0},
          "currency": {"type": "string", "minLength": 3, "maxLength": 3}
        }
      }
    """.trimIndent()
    sdk.schemaRegistry.register("payment.processed", schema)

    // Missing required field "currency" and amount is negative
    val result = sdk.enqueueDetailed("payment.processed", """{"amount": -5}""")

    assertThat(result).isInstanceOf(EnqueueResult.Rejected.ValidationFailed::class.java)
    val rejected = result as EnqueueResult.Rejected.ValidationFailed
    assertThat(rejected.errors).isNotEmpty()
  }

  @Test
  fun `strict validation with REJECT action rejects unknown event type with schema not found message`() = runTest {
    val config = baseConfig(
      validationPolicy = ValidationPolicy(
        enabled = true,
        strictMode = true,
        unknownEventTypeAction = UnknownEventTypeAction.REJECT,
      ),
    )
    val sdk = initSdk(config)

    // No schema registered for this event type
    val result = sdk.enqueueDetailed("unregistered.event", """{"data": "test"}""")

    assertThat(result).isInstanceOf(EnqueueResult.Rejected.ValidationFailed::class.java)
    val rejected = result as EnqueueResult.Rejected.ValidationFailed
    assertThat(rejected.errors).hasSize(1)
    assertThat(rejected.errors[0]).contains("No schema registered")
  }

  @Test
  fun `strict validation with ALLOW action accepts unknown event type`() = runTest {
    val config = baseConfig(
      validationPolicy = ValidationPolicy(
        enabled = true,
        strictMode = true,
        unknownEventTypeAction = UnknownEventTypeAction.ALLOW,
      ),
    )
    val sdk = initSdk(config)

    val result = sdk.enqueueDetailed("unregistered.event", """{"data": "test"}""")

    assertThat(result).isInstanceOf(EnqueueResult.Accepted::class.java)
  }

  @Test
  fun `permissive validation accepts event that would fail strict validation`() = runTest {
    val config = baseConfig(validationPolicy = ValidationPolicy.permissiveDefaults())
    val sdk = initSdk(config)

    val schema = """
      {
        "type": "object",
        "required": ["amount"],
        "properties": {
          "amount": {"type": "number"}
        }
      }
    """.trimIndent()
    sdk.schemaRegistry.register("payment.processed", schema)

    // Missing required "amount" field: permissive mode does not reject
    val result = sdk.enqueueDetailed("payment.processed", """{"note": "no amount"}""")

    assertThat(result).isInstanceOf(EnqueueResult.Accepted::class.java)
  }

  @Test
  fun `validation disabled accepts any event regardless of payload`() = runTest {
    val config = baseConfig(validationPolicy = ValidationPolicy.disabledDefaults())
    val sdk = initSdk(config)

    val result = sdk.enqueueDetailed("anything.goes", """{"garbage": true, "extra": 999}""")

    assertThat(result).isInstanceOf(EnqueueResult.Accepted::class.java)
  }

  @Test
  fun `strict validation rejects event with wrong property type`() = runTest {
    val config = baseConfig(validationPolicy = ValidationPolicy.strictDefaults())
    val sdk = initSdk(config)

    val schema = """
      {
        "type": "object",
        "properties": {
          "count": {"type": "integer"}
        }
      }
    """.trimIndent()
    sdk.schemaRegistry.register("inventory.update", schema)

    // "count" should be integer but provided as string
    val result = sdk.enqueueDetailed("inventory.update", """{"count": "not-a-number"}""")

    assertThat(result).isInstanceOf(EnqueueResult.Rejected.ValidationFailed::class.java)
    val rejected = result as EnqueueResult.Rejected.ValidationFailed
    assertThat(rejected.errors.any { it.contains("type") }).isTrue()
  }

  @Test
  fun `strict validation rejects event violating string pattern constraint`() = runTest {
    val config = baseConfig(validationPolicy = ValidationPolicy.strictDefaults())
    val sdk = initSdk(config)

    val schema = """
      {
        "type": "object",
        "properties": {
          "code": {"type": "string", "pattern": "^[A-Z]{3}-\\d{4}$"}
        }
      }
    """.trimIndent()
    sdk.schemaRegistry.register("product.code", schema)

    // Pattern requires "ABC-1234" format; this does not match
    val result = sdk.enqueueDetailed("product.code", """{"code": "invalid-code"}""")

    assertThat(result).isInstanceOf(EnqueueResult.Rejected.ValidationFailed::class.java)
    val rejected = result as EnqueueResult.Rejected.ValidationFailed
    assertThat(rejected.errors.any { it.contains("pattern") }).isTrue()
  }

  // ==========================================================================
  // Edge cases: validation-related
  // ==========================================================================

  @Test
  fun `strict validation rejects event with additional properties when not allowed`() = runTest {
    val config = baseConfig(validationPolicy = ValidationPolicy.strictDefaults())
    val sdk = initSdk(config)

    val schema = """
      {
        "type": "object",
        "properties": {
          "name": {"type": "string"}
        },
        "additionalProperties": false
      }
    """.trimIndent()
    sdk.schemaRegistry.register("strict.schema", schema)

    val result = sdk.enqueueDetailed(
      "strict.schema",
      """{"name": "valid", "extra_field": "not allowed"}""",
    )

    assertThat(result).isInstanceOf(EnqueueResult.Rejected.ValidationFailed::class.java)
    val rejected = result as EnqueueResult.Rejected.ValidationFailed
    assertThat(rejected.errors.any { it.contains("additional property") }).isTrue()
  }

  @Test
  fun `strict validation rejects event violating minimum value constraint`() = runTest {
    val config = baseConfig(validationPolicy = ValidationPolicy.strictDefaults())
    val sdk = initSdk(config)

    val schema = """
      {
        "type": "object",
        "properties": {
          "quantity": {"type": "integer", "minimum": 1}
        }
      }
    """.trimIndent()
    sdk.schemaRegistry.register("inventory.order", schema)

    val result = sdk.enqueueDetailed("inventory.order", """{"quantity": 0}""")

    assertThat(result).isInstanceOf(EnqueueResult.Rejected.ValidationFailed::class.java)
    val rejected = result as EnqueueResult.Rejected.ValidationFailed
    assertThat(rejected.errors.any { it.contains("minimum") }).isTrue()
  }

  @Test
  fun `multiple sequential rejections do not corrupt SDK state`() = runTest {
    val config = baseConfig(
      validationPolicy = ValidationPolicy.strictDefaults(),
      piiPolicy = com.sarastarquant.kioskops.sdk.pii.PiiPolicy.rejectDefaults(),
    )
    val sdk = initSdk(config)

    val schema = """
      {
        "type": "object",
        "required": ["id"],
        "properties": {
          "id": {"type": "string"}
        }
      }
    """.trimIndent()
    sdk.schemaRegistry.register("test.event", schema)

    // Trigger multiple validation rejections
    for (i in 1..5) {
      val result = sdk.enqueueDetailed("test.event", """{"wrong": $i}""")
      assertThat(result).isInstanceOf(EnqueueResult.Rejected.ValidationFailed::class.java)
    }

    // SDK should still function; accept a valid event
    val valid = sdk.enqueueDetailed("test.event", """{"id": "valid-after-rejections"}""")
    assertThat(valid).isInstanceOf(EnqueueResult.Accepted::class.java)

    // Queue should only contain the one valid event
    assertThat(sdk.queueDepth()).isEqualTo(1)
  }
}
