/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Tests for correlated operations.
 *
 * Compliance (ISO 27001 A.12.4): Validates operation tracking
 * with correlation and timing metadata.
 */
@RunWith(RobolectricTestRunner::class)
class CorrelatedOperationTest {

  @After
  fun tearDown() {
    CorrelationContext.clear()
  }

  @Test
  fun `correlatedOperation returns result with metadata`() = runBlocking {
    val result = correlatedOperation("test_operation") {
      "test_result"
    }

    assertThat(result.value).isEqualTo("test_result")
    assertThat(result.operationName).isEqualTo("test_operation")
    assertThat(result.correlationId).isNotEmpty()
    assertThat(result.durationNanos).isGreaterThan(0)
  }

  @Test
  fun `correlatedOperation measures duration`() = runBlocking {
    val result = correlatedOperation("timed_operation") {
      delay(50)
      "done"
    }

    // Should be at least 50ms = 50_000_000 nanos
    assertThat(result.durationNanos).isAtLeast(40_000_000L)
  }

  @Test
  fun `correlatedOperation uses existing correlation ID`() = runBlocking {
    CorrelationContext.set(CorrelationContext.KEY_CORRELATION_ID, "existing_id")

    val result = correlatedOperation("test_operation") {
      "result"
    }

    assertThat(result.correlationId).isEqualTo("existing_id")
  }

  @Test
  fun `correlatedOperation sets operation name in context`() = runBlocking {
    var capturedName: String? = null

    correlatedOperation("my_operation") {
      capturedName = CorrelationContext.operationName
      "result"
    }

    assertThat(capturedName).isEqualTo("my_operation")
  }

  @Test
  fun `correlatedOperation cleans up operation name after completion`() = runBlocking {
    correlatedOperation("temporary_operation") {
      "result"
    }

    assertThat(CorrelationContext.operationName).isNull()
  }

  @Test
  fun `correlatedOperation adds custom attributes`() = runBlocking {
    var capturedAttr: String? = null

    correlatedOperation(
      operationName = "attributed_operation",
      attributes = mapOf("custom_attr" to "custom_value"),
    ) {
      capturedAttr = CorrelationContext.get("custom_attr")
      "result"
    }

    assertThat(capturedAttr).isEqualTo("custom_value")
    // Attribute should be cleaned up
    assertThat(CorrelationContext.get("custom_attr")).isNull()
  }

  @Test
  fun `correlatedOperation propagates exceptions`() = runBlocking {
    val exception = runCatching {
      correlatedOperation<String>("failing_operation") {
        throw IllegalStateException("Test exception")
      }
    }.exceptionOrNull()

    assertThat(exception).isInstanceOf(IllegalStateException::class.java)
    assertThat(exception?.message).isEqualTo("Test exception")
  }

  @Test
  fun `trackedOperation calls onSuccess callback`() = runBlocking {
    val successCalled = AtomicBoolean(false)
    val capturedCorrelationId = AtomicReference<String>()
    val capturedDuration = AtomicLong()

    val result = trackedOperation(
      operationName = "tracked_success",
      onSuccess = { _, correlationId, durationNanos ->
        successCalled.set(true)
        capturedCorrelationId.set(correlationId)
        capturedDuration.set(durationNanos)
      },
    ) {
      "success_result"
    }

    assertThat(result).isEqualTo("success_result")
    assertThat(successCalled.get()).isTrue()
    assertThat(capturedCorrelationId.get()).isNotEmpty()
    assertThat(capturedDuration.get()).isGreaterThan(0)
  }

  @Test
  fun `trackedOperation calls onFailure callback on exception`() = runBlocking {
    val failureCalled = AtomicBoolean(false)
    val capturedException = AtomicReference<Throwable>()

    val exception = runCatching {
      trackedOperation<String>(
        operationName = "tracked_failure",
        onFailure = { error, _, _ ->
          failureCalled.set(true)
          capturedException.set(error)
        },
      ) {
        throw RuntimeException("Tracked failure")
      }
    }.exceptionOrNull()

    assertThat(exception).isInstanceOf(RuntimeException::class.java)
    assertThat(failureCalled.get()).isTrue()
    assertThat(capturedException.get()?.message).isEqualTo("Tracked failure")
  }

  @Test
  fun `correlatedBlock works for non-suspending code`() {
    val result = correlatedBlock("blocking_operation") {
      "blocking_result"
    }

    assertThat(result.value).isEqualTo("blocking_result")
    assertThat(result.operationName).isEqualTo("blocking_operation")
    assertThat(result.correlationId).isNotEmpty()
    assertThat(result.durationNanos).isGreaterThan(0)
  }

  @Test
  fun `correlatedBlock measures timing accurately`() {
    val result = correlatedBlock("timed_block") {
      Thread.sleep(50)
      "done"
    }

    // Should be at least 50ms = 50_000_000 nanos
    assertThat(result.durationNanos).isAtLeast(40_000_000L)
  }

  @Test
  fun `correlatedBlock with attributes`() {
    var capturedAttr: String? = null

    correlatedBlock(
      operationName = "block_with_attrs",
      attributes = mapOf("block_attr" to "block_value"),
    ) {
      capturedAttr = CorrelationContext.get("block_attr")
      "result"
    }

    assertThat(capturedAttr).isEqualTo("block_value")
    assertThat(CorrelationContext.get("block_attr")).isNull()
  }

  @Test
  fun `CorrelationContextElement captures snapshot`() {
    CorrelationContext.set("element_key", "element_value")
    val element = CorrelationContextElement()

    // Clear context
    CorrelationContext.clear()
    assertThat(CorrelationContext.get("element_key")).isNull()

    // Restore from element
    element.restore()
    assertThat(CorrelationContext.get("element_key")).isEqualTo("element_value")
  }

  @Test
  fun `CorrelationContextElement has proper key`() {
    val element = CorrelationContextElement()
    assertThat(element.key).isEqualTo(CorrelationContextElement.Key)
  }

  @Test
  fun `nested correlated operations maintain correct context`() = runBlocking {
    CorrelationContext.set(CorrelationContext.KEY_CORRELATION_ID, "parent_id")

    val outerResult = correlatedOperation("outer_operation") {
      val innerResult = correlatedOperation(
        operationName = "inner_operation",
        attributes = mapOf("inner_attr" to "inner_val"),
      ) {
        assertThat(CorrelationContext.get("inner_attr")).isEqualTo("inner_val")
        "inner"
      }
      assertThat(innerResult.operationName).isEqualTo("inner_operation")

      // After inner completes, inner_attr should be cleaned up
      assertThat(CorrelationContext.get("inner_attr")).isNull()
      "outer"
    }

    assertThat(outerResult.value).isEqualTo("outer")
    assertThat(outerResult.correlationId).isEqualTo("parent_id")
  }
}
