/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.pii

/**
 * Data classification levels per ISO 27001 A.8.2.
 * @since 0.5.0
 */
enum class DataClassification {
  PUBLIC,
  INTERNAL,
  CONFIDENTIAL,
  RESTRICTED,
}

/**
 * Policy for automatic data classification tagging.
 *
 * @property enabled Whether classification is active.
 * @property defaultClassification Default level when no PII is found.
 * @property piiClassification Level to apply when PII is detected.
 * @since 0.5.0
 */
data class DataClassificationPolicy(
  val enabled: Boolean = false,
  val defaultClassification: DataClassification = DataClassification.INTERNAL,
  val piiClassification: DataClassification = DataClassification.CONFIDENTIAL,
) {
  companion object {
    fun disabledDefaults() = DataClassificationPolicy(enabled = false)

    fun enabledDefaults() = DataClassificationPolicy(
      enabled = true,
      defaultClassification = DataClassification.INTERNAL,
      piiClassification = DataClassification.CONFIDENTIAL,
    )
  }
}
