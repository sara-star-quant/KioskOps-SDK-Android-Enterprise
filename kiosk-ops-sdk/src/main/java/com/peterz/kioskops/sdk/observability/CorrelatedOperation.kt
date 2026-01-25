/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.observability

import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Result of a correlated operation with timing and correlation metadata.
 *
 * @property value The operation result
 * @property correlationId The correlation ID used for this operation
 * @property durationNanos Execution duration in nanoseconds
 * @property operationName Name of the operation
 */
data class CorrelatedResult<T>(
  val value: T,
  val correlationId: String,
  val durationNanos: Long,
  val operationName: String,
)

/**
 * Execute a suspending operation with automatic correlation context and timing.
 *
 * Creates or uses existing correlation context, records execution time,
 * and provides structured result with metadata for logging and tracing.
 *
 * Compliance (ISO 27001 A.12.4): All SDK operations should be wrapped
 * with this function to enable audit trail correlation.
 *
 * @param operationName Human-readable operation name for tracing
 * @param attributes Additional attributes to include in context
 * @param context Optional coroutine context for execution
 * @param block The suspending operation to execute
 * @return CorrelatedResult with the operation result and metadata
 *
 * @since 0.4.0
 */
suspend inline fun <T> correlatedOperation(
  operationName: String,
  attributes: Map<String, String> = emptyMap(),
  context: CoroutineContext = EmptyCoroutineContext,
  crossinline block: suspend () -> T,
): CorrelatedResult<T> {
  // Capture correlation context for propagation
  val parentSnapshot = CorrelationContext.snapshot()
  val correlationId = CorrelationContext.correlationId

  return withContext(context) {
    // Restore context in new coroutine
    CorrelationContext.restore(parentSnapshot)
    CorrelationContext.set(CorrelationContext.KEY_OPERATION_NAME, operationName)
    attributes.forEach { (k, v) -> CorrelationContext.set(k, v) }

    val startTime = System.nanoTime()
    try {
      val result = block()
      val durationNanos = System.nanoTime() - startTime
      CorrelatedResult(
        value = result,
        correlationId = correlationId,
        durationNanos = durationNanos,
        operationName = operationName,
      )
    } finally {
      // Clean up added attributes
      CorrelationContext.remove(CorrelationContext.KEY_OPERATION_NAME)
      attributes.keys.forEach { CorrelationContext.remove(it) }
    }
  }
}

/**
 * Execute a suspending operation with correlation, automatically recording
 * success/failure to an optional callback.
 *
 * @param operationName Human-readable operation name
 * @param attributes Additional attributes for context
 * @param onSuccess Called on successful completion with result and timing
 * @param onFailure Called on exception with error and timing
 * @param block The suspending operation to execute
 * @return The operation result (throws on failure)
 *
 * @since 0.4.0
 */
suspend inline fun <T> trackedOperation(
  operationName: String,
  attributes: Map<String, String> = emptyMap(),
  crossinline onSuccess: (T, String, Long) -> Unit = { _, _, _ -> },
  crossinline onFailure: (Throwable, String, Long) -> Unit = { _, _, _ -> },
  crossinline block: suspend () -> T,
): T {
  val correlationId = CorrelationContext.correlationId
  val startTime = System.nanoTime()

  return CorrelationContext.withContext(operationName = operationName) {
    try {
      val result = block()
      val durationNanos = System.nanoTime() - startTime
      onSuccess(result, correlationId, durationNanos)
      result
    } catch (e: Throwable) {
      val durationNanos = System.nanoTime() - startTime
      onFailure(e, correlationId, durationNanos)
      throw e
    }
  }
}

/**
 * Execute a blocking operation with correlation context.
 *
 * For non-suspending code that needs correlation tracking.
 *
 * @param operationName Human-readable operation name
 * @param attributes Additional attributes for context
 * @param block The blocking operation to execute
 * @return The operation result
 *
 * @since 0.4.0
 */
inline fun <T> correlatedBlock(
  operationName: String,
  attributes: Map<String, String> = emptyMap(),
  block: () -> T,
): CorrelatedResult<T> {
  val correlationId = CorrelationContext.correlationId
  val startTime = System.nanoTime()

  return CorrelationContext.withContext(operationName = operationName) {
    attributes.forEach { (k, v) -> CorrelationContext.set(k, v) }
    try {
      val result = block()
      val durationNanos = System.nanoTime() - startTime
      CorrelatedResult(
        value = result,
        correlationId = correlationId,
        durationNanos = durationNanos,
        operationName = operationName,
      )
    } finally {
      attributes.keys.forEach { CorrelationContext.remove(it) }
    }
  }
}

/**
 * Coroutine context element for propagating correlation context across dispatchers.
 *
 * Usage:
 * ```kotlin
 * withContext(Dispatchers.IO + CorrelationContextElement()) {
 *   // CorrelationContext is available here
 * }
 * ```
 *
 * @since 0.4.0
 */
class CorrelationContextElement(
  private val snapshot: Map<String, String> = CorrelationContext.snapshot(),
) : CoroutineContext.Element {

  override val key: CoroutineContext.Key<*> = Key

  /**
   * Restore the captured context to the current thread.
   */
  fun restore() {
    CorrelationContext.restore(snapshot)
  }

  companion object Key : CoroutineContext.Key<CorrelationContextElement>
}
