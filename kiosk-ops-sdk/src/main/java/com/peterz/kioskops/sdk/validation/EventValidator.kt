/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.validation

/**
 * Interface for event payload validation.
 *
 * Implementations validate event payloads against registered schemas.
 * NIST SI-10: Information Input Validation.
 *
 * @since 0.5.0
 */
interface EventValidator {
  /**
   * Validate an event payload against its registered schema.
   *
   * @param eventType The event type identifier.
   * @param payloadJson The JSON payload to validate.
   * @return Validation result.
   */
  fun validate(eventType: String, payloadJson: String): ValidationResult
}

/**
 * Result of an event validation.
 *
 * @since 0.5.0
 */
sealed class ValidationResult {
  /** Payload passes all schema constraints. */
  object Valid : ValidationResult()

  /** Payload violates one or more schema constraints. */
  data class Invalid(val errors: List<String>) : ValidationResult()

  /** No schema registered for this event type. */
  data class SchemaNotFound(val eventType: String) : ValidationResult()
}
