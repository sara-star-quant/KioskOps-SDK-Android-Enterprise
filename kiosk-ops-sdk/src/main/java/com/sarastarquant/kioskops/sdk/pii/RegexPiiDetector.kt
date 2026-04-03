/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.pii

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Regex-based PII detector.
 *
 * Uses compiled regex patterns to detect common PII types in JSON payloads.
 * Walks the JSON tree via kotlinx.serialization.json.JsonElement.
 *
 * NIST SI-19: De-identification.
 *
 * @since 0.5.0
 */
class RegexPiiDetector(
  private val minimumConfidence: Float = 0.7f,
  private val fieldExclusions: Set<String> = emptySet(),
) : PiiDetector {

  private val json = Json { ignoreUnknownKeys = true }

  override fun scan(payloadJson: String): PiiScanResult {
    return try {
      val element = json.parseToJsonElement(payloadJson)
      val findings = mutableListOf<PiiFinding>()
      walkElement(element, "$", findings)
      PiiScanResult(findings.filter { it.confidence >= minimumConfidence })
    } catch (_: Exception) {
      PiiScanResult(emptyList())
    }
  }

  private fun walkElement(element: JsonElement, path: String, findings: MutableList<PiiFinding>) {
    if (path in fieldExclusions) return

    when (element) {
      is JsonPrimitive -> {
        if (element.isString) {
          val value = element.content
          scanValue(value, path, findings)
        }
      }
      is JsonObject -> {
        for ((key, child) in element) {
          walkElement(child, "$path.$key", findings)
        }
      }
      is JsonArray -> {
        for ((i, child) in element.withIndex()) {
          walkElement(child, "$path[$i]", findings)
        }
      }
    }
  }

  private fun scanValue(value: String, path: String, findings: MutableList<PiiFinding>) {
    // Skip values that match safe patterns (UUIDs, timestamps, versions)
    // to reduce false positives.
    for (safe in SAFE_PATTERNS) {
      if (safe.containsMatchIn(value)) return
    }

    for ((pattern, piiType, confidence) in PATTERNS) {
      if (pattern.containsMatchIn(value)) {
        findings.add(PiiFinding(jsonPath = path, piiType = piiType, confidence = confidence))
      }
    }
  }

  private data class PatternEntry(val pattern: Regex, val piiType: PiiType, val confidence: Float)

  companion object {

    /**
     * Safe patterns that should never be flagged as PII.
     * If a value matches any of these, PII detection is skipped entirely.
     */
    private val SAFE_PATTERNS: List<Regex> = listOf(
      // UUID (v1-v5 and similar hex-dash formats)
      Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"),
      // ISO 8601 timestamp
      Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}"),
      // Semantic version (must not have a fourth dotted segment like an IP address)
      Regex("^\\d+\\.\\d+\\.\\d+(-[\\w.]+)?(\\+[\\w.]+)?$"),
    )

    private val PATTERNS: List<PatternEntry> = listOf(
      // Email
      PatternEntry(Regex("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"), PiiType.EMAIL, 0.95f),

      // Phone (international formats)
      PatternEntry(Regex("\\+?1?[\\s.-]?\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}"), PiiType.PHONE, 0.85f),
      PatternEntry(Regex("\\+\\d{1,3}[\\s.-]?\\d{4,14}"), PiiType.PHONE, 0.80f),

      // SSN (US)
      PatternEntry(Regex("\\b\\d{3}[\\s.-]?\\d{2}[\\s.-]?\\d{4}\\b"), PiiType.SSN, 0.90f),

      // Credit card (Luhn-plausible patterns)
      PatternEntry(Regex("\\b4\\d{3}[\\s.-]?\\d{4}[\\s.-]?\\d{4}[\\s.-]?\\d{4}\\b"), PiiType.CREDIT_CARD, 0.95f), // Visa
      PatternEntry(Regex("\\b5[1-5]\\d{2}[\\s.-]?\\d{4}[\\s.-]?\\d{4}[\\s.-]?\\d{4}\\b"), PiiType.CREDIT_CARD, 0.95f), // MC
      PatternEntry(Regex("\\b3[47]\\d{2}[\\s.-]?\\d{6}[\\s.-]?\\d{5}\\b"), PiiType.CREDIT_CARD, 0.95f), // Amex

      // IP address (IPv4)
      PatternEntry(Regex("\\b(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b"), PiiType.IP_ADDRESS, 0.85f),

      // Date of birth patterns
      PatternEntry(Regex("\\b(?:0[1-9]|1[0-2])[/\\-](?:0[1-9]|[12]\\d|3[01])[/\\-](?:19|20)\\d{2}\\b"), PiiType.DOB, 0.80f),
      PatternEntry(Regex("\\b(?:19|20)\\d{2}[/\\-](?:0[1-9]|1[0-2])[/\\-](?:0[1-9]|[12]\\d|3[01])\\b"), PiiType.DOB, 0.80f),

      // Passport (generic alphanumeric, 6-9 chars)
      PatternEntry(Regex("\\b[A-Z]{1,2}\\d{6,8}\\b"), PiiType.PASSPORT, 0.70f),

      // US National ID (EIN format)
      PatternEntry(Regex("\\b\\d{2}-\\d{7}\\b"), PiiType.NATIONAL_ID, 0.75f),

      // Australian Tax File Number (TFN): 9 digits with optional spaces
      PatternEntry(Regex("\\b\\d{3}\\s?\\d{3}\\s?\\d{3}\\b"), PiiType.NATIONAL_ID, 0.75f),

      // UK National Insurance Number (NIN)
      PatternEntry(Regex("\\b[A-CEGHJ-PR-TW-Z]{2}\\s?\\d{2}\\s?\\d{2}\\s?\\d{2}\\s?[A-D]\\b"), PiiType.NATIONAL_ID, 0.85f),

      // Canadian Social Insurance Number (SIN): 9 digits with optional spaces
      // (same pattern as Australian TFN; both covered by the TFN entry above)

      // German Steuer-ID: 11 digits with optional spaces (XX XXX XXX XXX)
      PatternEntry(Regex("\\b\\d{2}\\s?\\d{3}\\s?\\d{3}\\s?\\d{3}\\b"), PiiType.NATIONAL_ID, 0.75f),

      // Japan My Number (Individual Number): 12 digits starting with 1-9
      PatternEntry(Regex("\\b[1-9]\\d{11}\\b"), PiiType.NATIONAL_ID, 0.70f),

      // India Aadhaar: 12 digits starting with 2-9, grouped in fours
      PatternEntry(Regex("\\b[2-9]\\d{3}\\s?\\d{4}\\s?\\d{4}\\b"), PiiType.NATIONAL_ID, 0.75f),

      // Brazil CPF: 11 digits in xxx.xxx.xxx-xx format
      PatternEntry(Regex("\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b"), PiiType.NATIONAL_ID, 0.90f),

      // South Africa ID: 13 digits starting with a valid date (YYMMDD)
      PatternEntry(Regex("\\b\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{7}\\b"), PiiType.NATIONAL_ID, 0.80f),
    )
  }
}
