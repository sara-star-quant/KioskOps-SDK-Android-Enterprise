/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.validation

/**
 * Callback interface for validation outcomes.
 *
 * Implementations can log, emit metrics, or take other action on validation results.
 *
 * @since 0.5.0
 */
interface ValidationListener {
  /** Called when an event passes validation. */
  fun onValidationPassed(eventType: String) {}

  /** Called when an event fails validation. */
  fun onValidationFailed(eventType: String, errors: List<String>) {}

  /** Called when no schema is found for an event type. */
  fun onSchemaNotFound(eventType: String) {}
}
