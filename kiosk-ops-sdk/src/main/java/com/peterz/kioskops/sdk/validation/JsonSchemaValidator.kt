/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.validation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Lightweight JSON Schema validator supporting a Draft 2020-12 subset.
 *
 * Supported keywords: type, required, properties, pattern, minLength, maxLength,
 * enum, format (date, date-time, email, uri), minimum, maximum, minItems, maxItems,
 * items, additionalProperties.
 *
 * Uses kotlinx.serialization.json. No external dependencies.
 *
 * NIST SI-10: Information Input Validation.
 *
 * @since 0.5.0
 */
class JsonSchemaValidator(
  private val registry: SchemaRegistry,
) : EventValidator {

  private val json = Json { ignoreUnknownKeys = true }

  override fun validate(eventType: String, payloadJson: String): ValidationResult {
    val schemaStr = registry.getSchema(eventType)
      ?: return ValidationResult.SchemaNotFound(eventType)

    return try {
      val schema = json.parseToJsonElement(schemaStr)
      val payload = json.parseToJsonElement(payloadJson)
      val errors = mutableListOf<String>()
      validateElement(payload, schema, "$", errors)
      if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    } catch (e: Exception) {
      ValidationResult.Invalid(listOf("Schema parsing error: ${e.message}"))
    }
  }

  private fun validateElement(
    element: JsonElement,
    schema: JsonElement,
    path: String,
    errors: MutableList<String>,
  ) {
    if (schema !is JsonObject) return

    // type check
    schema["type"]?.let { typeNode ->
      val expectedType = (typeNode as? JsonPrimitive)?.contentOrNull
      if (expectedType != null && !matchesType(element, expectedType)) {
        errors.add("$path: expected type '$expectedType', got ${jsonType(element)}")
        return
      }
    }

    // enum check
    schema["enum"]?.let { enumNode ->
      if (enumNode is JsonArray) {
        if (element !in enumNode) {
          errors.add("$path: value not in enum ${enumNode.map { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }}")
        }
      }
    }

    // string-specific checks
    if (element is JsonPrimitive && element.isString) {
      val content = element.content
      schema["minLength"]?.let { min ->
        val minVal = (min as? JsonPrimitive)?.intOrNull
        if (minVal != null && content.length < minVal) {
          errors.add("$path: string length ${content.length} < minLength $minVal")
        }
      }
      schema["maxLength"]?.let { max ->
        val maxVal = (max as? JsonPrimitive)?.intOrNull
        if (maxVal != null && content.length > maxVal) {
          errors.add("$path: string length ${content.length} > maxLength $maxVal")
        }
      }
      schema["pattern"]?.let { pat ->
        val pattern = (pat as? JsonPrimitive)?.contentOrNull
        if (pattern != null) {
          try {
            if (!Regex(pattern).containsMatchIn(content)) {
              errors.add("$path: does not match pattern '$pattern'")
            }
          } catch (_: Exception) {
            errors.add("$path: invalid regex pattern '$pattern'")
          }
        }
      }
      schema["format"]?.let { fmt ->
        val format = (fmt as? JsonPrimitive)?.contentOrNull
        if (format != null) {
          validateFormat(content, format, path, errors)
        }
      }
    }

    // number-specific checks
    if (element is JsonPrimitive && !element.isString) {
      val numVal = element.doubleOrNull
      if (numVal != null) {
        schema["minimum"]?.let { min ->
          val minVal = (min as? JsonPrimitive)?.doubleOrNull
          if (minVal != null && numVal < minVal) {
            errors.add("$path: value $numVal < minimum $minVal")
          }
        }
        schema["maximum"]?.let { max ->
          val maxVal = (max as? JsonPrimitive)?.doubleOrNull
          if (maxVal != null && numVal > maxVal) {
            errors.add("$path: value $numVal > maximum $maxVal")
          }
        }
      }
    }

    // object-specific checks
    if (element is JsonObject) {
      // required
      schema["required"]?.let { reqNode ->
        if (reqNode is JsonArray) {
          for (req in reqNode) {
            val key = (req as? JsonPrimitive)?.contentOrNull ?: continue
            if (key !in element) {
              errors.add("$path: missing required property '$key'")
            }
          }
        }
      }

      // properties
      schema["properties"]?.let { propsNode ->
        if (propsNode is JsonObject) {
          for ((key, propSchema) in propsNode) {
            val value = element[key]
            if (value != null) {
              validateElement(value, propSchema, "$path.$key", errors)
            }
          }
        }
      }

      // additionalProperties
      schema["additionalProperties"]?.let { addlNode ->
        if (addlNode is JsonPrimitive && addlNode.booleanOrNull == false) {
          val declaredProps = (schema["properties"] as? JsonObject)?.keys ?: emptySet()
          for (key in element.keys) {
            if (key !in declaredProps) {
              errors.add("$path: additional property '$key' not allowed")
            }
          }
        }
      }
    }

    // array-specific checks
    if (element is JsonArray) {
      schema["minItems"]?.let { min ->
        val minVal = (min as? JsonPrimitive)?.intOrNull
        if (minVal != null && element.size < minVal) {
          errors.add("$path: array size ${element.size} < minItems $minVal")
        }
      }
      schema["maxItems"]?.let { max ->
        val maxVal = (max as? JsonPrimitive)?.intOrNull
        if (maxVal != null && element.size > maxVal) {
          errors.add("$path: array size ${element.size} > maxItems $maxVal")
        }
      }
      schema["items"]?.let { itemsSchema ->
        for ((i, item) in element.withIndex()) {
          validateElement(item, itemsSchema, "$path[$i]", errors)
        }
      }
    }
  }

  private fun matchesType(element: JsonElement, expected: String): Boolean = when (expected) {
    "object" -> element is JsonObject
    "array" -> element is JsonArray
    "string" -> element is JsonPrimitive && element.isString
    "number" -> element is JsonPrimitive && !element.isString && element.doubleOrNull != null
    "integer" -> element is JsonPrimitive && !element.isString && element.longOrNull != null
    "boolean" -> element is JsonPrimitive && !element.isString && element.booleanOrNull != null
    "null" -> element is JsonNull
    else -> true
  }

  private fun jsonType(element: JsonElement): String = when {
    element is JsonObject -> "object"
    element is JsonArray -> "array"
    element is JsonNull -> "null"
    element is JsonPrimitive && element.isString -> "string"
    element is JsonPrimitive && element.booleanOrNull != null -> "boolean"
    element is JsonPrimitive && element.longOrNull != null -> "integer"
    element is JsonPrimitive && element.doubleOrNull != null -> "number"
    else -> "unknown"
  }

  private fun validateFormat(
    value: String,
    format: String,
    path: String,
    errors: MutableList<String>,
  ) {
    val valid = when (format) {
      "email" -> EMAIL_PATTERN.matches(value)
      "date" -> DATE_PATTERN.matches(value)
      "date-time" -> DATE_TIME_PATTERN.matches(value)
      "uri" -> value.startsWith("http://") || value.startsWith("https://")
      else -> true
    }
    if (!valid) {
      errors.add("$path: invalid format '$format'")
    }
  }

  companion object {
    private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val DATE_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val DATE_TIME_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")
  }
}
