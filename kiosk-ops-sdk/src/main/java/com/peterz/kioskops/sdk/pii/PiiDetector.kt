/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.pii

/**
 * Interface for detecting PII in event payloads.
 *
 * Implementations scan JSON payloads for personally identifiable information.
 * NIST SI-19: De-identification.
 *
 * @since 0.5.0
 */
interface PiiDetector {
  /**
   * Scan a JSON payload for PII.
   *
   * @param payloadJson The JSON payload to scan.
   * @return Scan result with list of findings.
   */
  fun scan(payloadJson: String): PiiScanResult
}

/**
 * Result of a PII scan.
 * @since 0.5.0
 */
data class PiiScanResult(
  val findings: List<PiiFinding>,
) {
  val hasPii: Boolean get() = findings.isNotEmpty()
}

/**
 * A single PII finding within a payload.
 * @since 0.5.0
 */
data class PiiFinding(
  val jsonPath: String,
  val piiType: PiiType,
  val confidence: Float,
)

/**
 * Types of PII that can be detected.
 * @since 0.5.0
 */
enum class PiiType {
  EMAIL,
  PHONE,
  SSN,
  CREDIT_CARD,
  ADDRESS,
  NAME,
  DOB,
  IP_ADDRESS,
  PASSPORT,
  NATIONAL_ID,
}
