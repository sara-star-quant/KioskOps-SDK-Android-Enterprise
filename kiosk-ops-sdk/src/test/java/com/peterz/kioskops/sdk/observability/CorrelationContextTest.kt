/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * Tests for CorrelationContext.
 *
 * Compliance (ISO 27001 A.12.4): Validates correlation ID propagation
 * for audit trail linkage.
 */
@RunWith(RobolectricTestRunner::class)
class CorrelationContextTest {

  @After
  fun tearDown() {
    CorrelationContext.clear()
  }

  @Test
  fun `correlationId is auto-generated if not set`() {
    val id = CorrelationContext.correlationId
    assertThat(id).isNotEmpty()
    assertThat(id).hasLength(16)
    assertThat(id).matches("[a-f0-9]+")
  }

  @Test
  fun `correlationId returns same value on subsequent calls`() {
    val first = CorrelationContext.correlationId
    val second = CorrelationContext.correlationId
    assertThat(first).isEqualTo(second)
  }

  @Test
  fun `correlationId changes after clear`() {
    val first = CorrelationContext.correlationId
    CorrelationContext.clear()
    val second = CorrelationContext.correlationId
    assertThat(first).isNotEqualTo(second)
  }

  @Test
  fun `set and get work for arbitrary keys`() {
    CorrelationContext.set("custom_key", "custom_value")
    assertThat(CorrelationContext.get("custom_key")).isEqualTo("custom_value")
  }

  @Test
  fun `get returns null for unset keys`() {
    assertThat(CorrelationContext.get("nonexistent")).isNull()
  }

  @Test
  fun `remove removes a key`() {
    CorrelationContext.set("to_remove", "value")
    CorrelationContext.remove("to_remove")
    assertThat(CorrelationContext.get("to_remove")).isNull()
  }

  @Test
  fun `traceId is null by default`() {
    assertThat(CorrelationContext.traceId).isNull()
  }

  @Test
  fun `spanId is null by default`() {
    assertThat(CorrelationContext.spanId).isNull()
  }

  @Test
  fun `traceId can be set and retrieved`() {
    CorrelationContext.set(CorrelationContext.KEY_TRACE_ID, "abc123def456789012345678901234")
    assertThat(CorrelationContext.traceId).isEqualTo("abc123def456789012345678901234")
  }

  @Test
  fun `snapshot captures current context`() {
    CorrelationContext.set("key1", "value1")
    CorrelationContext.set("key2", "value2")

    val snapshot = CorrelationContext.snapshot()

    assertThat(snapshot).containsEntry("key1", "value1")
    assertThat(snapshot).containsEntry("key2", "value2")
  }

  @Test
  fun `restore replaces current context`() {
    CorrelationContext.set("original", "value")

    val newSnapshot = mapOf("restored" to "new_value")
    CorrelationContext.restore(newSnapshot)

    assertThat(CorrelationContext.get("original")).isNull()
    assertThat(CorrelationContext.get("restored")).isEqualTo("new_value")
  }

  @Test
  fun `restore with empty map clears context`() {
    CorrelationContext.set("key", "value")
    CorrelationContext.restore(emptyMap())
    assertThat(CorrelationContext.get("key")).isNull()
  }

  @Test
  fun `withContext restores previous context`() {
    CorrelationContext.set("outer", "outer_value")

    CorrelationContext.withContext(correlationId = "inner_correlation") {
      assertThat(CorrelationContext.correlationId).isEqualTo("inner_correlation")
      CorrelationContext.set("inner", "inner_value")
    }

    assertThat(CorrelationContext.get("outer")).isEqualTo("outer_value")
    assertThat(CorrelationContext.get("inner")).isNull()
  }

  @Test
  fun `withContext sets operationName`() {
    CorrelationContext.withContext(operationName = "test_operation") {
      assertThat(CorrelationContext.operationName).isEqualTo("test_operation")
    }
    assertThat(CorrelationContext.operationName).isNull()
  }

  @Test
  fun `withAttributes adds and removes attributes`() {
    CorrelationContext.withAttributes(mapOf("attr1" to "val1", "attr2" to "val2")) {
      assertThat(CorrelationContext.get("attr1")).isEqualTo("val1")
      assertThat(CorrelationContext.get("attr2")).isEqualTo("val2")
    }
    assertThat(CorrelationContext.get("attr1")).isNull()
    assertThat(CorrelationContext.get("attr2")).isNull()
  }

  @Test
  fun `generateId produces valid format`() {
    val id = CorrelationContext.generateId()
    assertThat(id).hasLength(16)
    assertThat(id).matches("[a-f0-9]+")
  }

  @Test
  fun `generateTraceId produces valid W3C format`() {
    val traceId = CorrelationContext.generateTraceId()
    assertThat(traceId).hasLength(32)
    assertThat(traceId).matches("[a-f0-9]+")
  }

  @Test
  fun `generateSpanId produces valid W3C format`() {
    val spanId = CorrelationContext.generateSpanId()
    assertThat(spanId).hasLength(16)
    assertThat(spanId).matches("[a-f0-9]+")
  }

  @Test
  fun `context is thread-local`() {
    val mainThreadId = AtomicReference<String>()
    val otherThreadId = AtomicReference<String>()
    val latch = CountDownLatch(1)

    // Set correlation ID on main thread
    mainThreadId.set(CorrelationContext.correlationId)

    // Get correlation ID on another thread
    Thread {
      otherThreadId.set(CorrelationContext.correlationId)
      latch.countDown()
    }.start()

    latch.await()

    // Each thread should have its own correlation ID
    assertThat(mainThreadId.get()).isNotEqualTo(otherThreadId.get())
  }

  @Test
  fun `snapshot can be used to propagate context to new thread`() {
    CorrelationContext.set("propagated", "value")
    val snapshot = CorrelationContext.snapshot()
    val propagatedValue = AtomicReference<String>()
    val latch = CountDownLatch(1)

    Thread {
      CorrelationContext.restore(snapshot)
      propagatedValue.set(CorrelationContext.get("propagated"))
      latch.countDown()
    }.start()

    latch.await()
    assertThat(propagatedValue.get()).isEqualTo("value")
  }

  @Test
  fun `constants have expected values`() {
    assertThat(CorrelationContext.KEY_CORRELATION_ID).isEqualTo("correlation_id")
    assertThat(CorrelationContext.KEY_TRACE_ID).isEqualTo("trace_id")
    assertThat(CorrelationContext.KEY_SPAN_ID).isEqualTo("span_id")
    assertThat(CorrelationContext.KEY_OPERATION_NAME).isEqualTo("operation_name")

    assertThat(CorrelationContext.HEADER_CORRELATION_ID).isEqualTo("X-Correlation-ID")
    assertThat(CorrelationContext.HEADER_TRACE_PARENT).isEqualTo("traceparent")
  }

  @Test
  fun `coroutine context propagation with snapshot`() = runBlocking {
    CorrelationContext.set("coroutine_key", "main_value")
    val snapshot = CorrelationContext.snapshot()

    withContext(Dispatchers.Default) {
      // Context is lost in new dispatcher
      assertThat(CorrelationContext.get("coroutine_key")).isNull()

      // Restore propagates context
      CorrelationContext.restore(snapshot)
      assertThat(CorrelationContext.get("coroutine_key")).isEqualTo("main_value")
    }
  }
}
