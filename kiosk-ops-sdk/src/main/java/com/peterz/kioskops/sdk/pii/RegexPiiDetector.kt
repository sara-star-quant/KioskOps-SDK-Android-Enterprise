/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.pii

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
    for ((pattern, piiType, confidence) in PATTERNS) {
      if (pattern.containsMatchIn(value)) {
        findings.add(PiiFinding(jsonPath = path, piiType = piiType, confidence = confidence))
      }
    }
  }

  private data class PatternEntry(val pattern: Regex, val piiType: PiiType, val confidence: Float)

  companion object {
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
    )
  }
}
