/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.validation

/**
 * Policy controlling event validation behavior.
 *
 * @property enabled Whether validation is active.
 * @property strictMode If true, validation errors reject the event. If false, they flag only.
 * @property unknownEventTypeAction Action when no schema is registered for an event type.
 * @property validationTimeoutMs Maximum time for a single validation before fallback to allow.
 * @since 0.5.0
 */
data class ValidationPolicy(
  val enabled: Boolean = false,
  val strictMode: Boolean = false,
  val unknownEventTypeAction: UnknownEventTypeAction = UnknownEventTypeAction.ALLOW,
  val validationTimeoutMs: Long = 500L,
) {
  companion object {
    fun disabledDefaults() = ValidationPolicy(enabled = false)

    fun strictDefaults() = ValidationPolicy(
      enabled = true,
      strictMode = true,
      unknownEventTypeAction = UnknownEventTypeAction.REJECT,
    )

    fun permissiveDefaults() = ValidationPolicy(
      enabled = true,
      strictMode = false,
      unknownEventTypeAction = UnknownEventTypeAction.ALLOW,
    )
  }
}

/**
 * Action to take when an event type has no registered schema.
 * @since 0.5.0
 */
enum class UnknownEventTypeAction {
  ALLOW,
  FLAG,
  REJECT,
}
