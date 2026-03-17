/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.crypto

/**
 * Policy controlling field-level encryption within event payloads.
 *
 * @property enabled Whether field-level encryption is active.
 * @property encryptedFields Map of event type to set of JSON field paths to encrypt.
 * @property defaultEncryptedFields Field paths to encrypt for all event types.
 * @since 0.5.0
 */
data class FieldEncryptionPolicy(
  val enabled: Boolean = false,
  val encryptedFields: Map<String, Set<String>> = emptyMap(),
  val defaultEncryptedFields: Set<String> = emptySet(),
) {
  companion object {
    fun disabledDefaults() = FieldEncryptionPolicy(enabled = false)

    fun enabledDefaults() = FieldEncryptionPolicy(
      enabled = true,
      defaultEncryptedFields = setOf("email", "phone", "ssn", "name", "address"),
    )
  }

  /**
   * Get the set of fields to encrypt for a given event type.
   */
  fun fieldsForEventType(eventType: String): Set<String> {
    val typeSpecific = encryptedFields[eventType] ?: emptySet()
    return defaultEncryptedFields + typeSpecific
  }
}
