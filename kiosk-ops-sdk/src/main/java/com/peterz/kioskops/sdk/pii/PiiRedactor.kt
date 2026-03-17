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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Redacts PII from JSON payloads by replacing detected values.
 *
 * Replaces values with `[REDACTED:TYPE]` markers.
 *
 * @since 0.5.0
 */
class PiiRedactor {

  private val json = Json { ignoreUnknownKeys = true }

  /**
   * Redact PII from a JSON payload based on scan findings.
   *
   * @param payloadJson Original JSON payload.
   * @param findings PII findings to redact.
   * @return Redacted JSON string and list of redacted field paths.
   */
  fun redact(payloadJson: String, findings: List<PiiFinding>): RedactionResult {
    if (findings.isEmpty()) return RedactionResult(payloadJson, emptyList())

    val findingsByPath = findings.groupBy { it.jsonPath }
    val element = json.parseToJsonElement(payloadJson)
    val redactedPaths = mutableListOf<String>()
    val redacted = redactElement(element, "$", findingsByPath, redactedPaths)
    return RedactionResult(redacted.toString(), redactedPaths)
  }

  private fun redactElement(
    element: JsonElement,
    path: String,
    findingsByPath: Map<String, List<PiiFinding>>,
    redactedPaths: MutableList<String>,
  ): JsonElement {
    val pathFindings = findingsByPath[path]
    if (pathFindings != null && element is JsonPrimitive) {
      val highestConfidence = pathFindings.maxByOrNull { it.confidence }
      if (highestConfidence != null) {
        redactedPaths.add(path)
        return JsonPrimitive("[REDACTED:${highestConfidence.piiType}]")
      }
    }

    return when (element) {
      is JsonObject -> buildJsonObject {
        for ((key, child) in element) {
          put(key, redactElement(child, "$path.$key", findingsByPath, redactedPaths))
        }
      }
      is JsonArray -> buildJsonArray {
        for ((i, child) in element.withIndex()) {
          add(redactElement(child, "$path[$i]", findingsByPath, redactedPaths))
        }
      }
      else -> element
    }
  }
}

/**
 * Result of a PII redaction operation.
 * @since 0.5.0
 */
data class RedactionResult(
  val redactedJson: String,
  val redactedPaths: List<String>,
)
