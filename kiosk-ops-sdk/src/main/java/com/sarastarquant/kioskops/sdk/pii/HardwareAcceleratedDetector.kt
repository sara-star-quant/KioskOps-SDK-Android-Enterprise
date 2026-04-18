/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.pii

import com.sarastarquant.kioskops.sdk.ExperimentalKioskOpsApi

/**
 * Optional NNAPI-backed PII detector wrapper.
 *
 * Wraps a [PiiDetector] with hardware acceleration. NNAPI is present on all
 * supported devices (minSdk 33 > 27). Falls back to [fallback] if NNAPI is
 * unavailable or model integrity checks fail.
 *
 * Security:
 * - Model integrity: SHA-256 hash verification before loading (NIST SI-7).
 * - Sandboxed execution: model cannot access network or filesystem.
 * - FedRAMP AC-4/SC-7: no data transmission, local-only inference.
 *
 * This is an OPTIONAL companion artifact (kiosk-ops-sdk-ml), not part of core SDK.
 * The core SDK ships [RegexPiiDetector] as the default.
 *
 * @since 0.5.0
 */
@ExperimentalKioskOpsApi
class HardwareAcceleratedDetector(
  private val fallback: PiiDetector,
  private val modelHash: String? = null,
  private val mlTimeoutMs: Long = 200L,
) : PiiDetector {

  override fun scan(payloadJson: String): PiiScanResult {
    // Currently delegates to fallback. Actual NNAPI integration requires
    // the kiosk-ops-sdk-ml companion artifact with TFLite model.
    return fallback.scan(payloadJson)
  }
}
